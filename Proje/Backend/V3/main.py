import asyncio
import time

from fastapi import BackgroundTasks, FastAPI, Query

from V3.cache.app_catalog_cache import set_cached_app_catalog_snapshot
from V3.config import MODEL_DIR
from V3.database.app_catalog_repository import save_app_catalog_snapshot
from V3.database.command_history_repository import (
    clear_command_history,
    delete_command_history_item,
    list_command_history,
    record_command_history,
)
from V3.schemas import (
    AppCatalogCloseResponse,
    AppCatalogRequest,
    AppCatalogResponse,
    CommandHistoryMutationResponse,
    CommandHistoryResponse,
    FinalResponse,
    PredictRequest,
)
from V3.services.model_service import get_device_name, preload_model
from V3.services.predict_service import predict_command
from V3.services.app_catalog_service import catalog_count, delete_app_catalog, save_app_catalog

app = FastAPI(title="Android Assistant Intent API")


@app.on_event("startup")
def preload_intent_model():
    started_at = time.perf_counter()
    preload_model()
    elapsed_ms = (time.perf_counter() - started_at) * 1000
    print(
        f"[startup] model preloaded in {elapsed_ms:.2f} ms | device={get_device_name()}",
        flush=True,
    )


@app.get("/")
def root():
    return {
        "message": "Android Assistant Intent API is running",
        "version": "app_catalog_validation_v1",
        "device": get_device_name(),
        "model_dir": str(MODEL_DIR),
    }


@app.post("/predict", response_model=FinalResponse)
def predict(request: PredictRequest, background_tasks: BackgroundTasks):
    started_at = time.perf_counter()
    response = predict_command(
        text=request.text,
        language=request.language,
        text_alternatives=request.text_alternatives,
        session_id=request.session_id,
        catalog_version=request.catalog_version,
        has_search_input=request.has_search_input,
    )
    response["processing_time_ms"] = round((time.perf_counter() - started_at) * 1000, 2)
    background_tasks.add_task(
        _record_command_history_background,
        request.text,
        request.language,
        response.copy(),
        request.session_id,
        request.device_id,
        response["processing_time_ms"],
    )
    print(
        "[predict] "
        f"{response['processing_time_ms']:.2f} ms | "
        f"text='{request.text}' | "
        f"language={request.language.upper()} | "
        f"intent={response.get('intent')} | "
        f"accepted={response.get('accepted')} | "
        f"confidence={response.get('confidence')}",
        flush=True,
    )
    return response


def _record_command_history_background(
    text: str,
    language: str,
    response: dict,
    session_id: str | None,
    device_id: str | None,
    processing_time_ms: float,
) -> None:
    try:
        asyncio.run(
            record_command_history(
                text=text,
                language=language,
                response=response,
                session_id=session_id,
                device_id=device_id,
                processing_time_ms=processing_time_ms,
            )
        )
    except Exception as exc:
        print(
            "[database] failed to record command history from predict endpoint | "
            f"error={exc}",
            flush=True,
        )


@app.get("/command-history", response_model=CommandHistoryResponse)
async def command_history(
    session_id: str | None = None,
    device_id: str | None = None,
    limit: int = Query(default=20, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    q: str | None = None,
):
    return await list_command_history(
        session_id=session_id,
        device_id=device_id,
        limit=limit,
        offset=offset,
        query=q,
    )


@app.delete("/command-history", response_model=CommandHistoryMutationResponse)
async def clear_history(session_id: str | None = None, device_id: str | None = None):
    deleted_count = await clear_command_history(
        session_id=session_id,
        device_id=device_id,
    )
    return CommandHistoryMutationResponse(
        accepted=True,
        deleted_count=deleted_count,
    )


@app.delete("/command-history/{history_id}", response_model=CommandHistoryMutationResponse)
async def delete_history_item(history_id: str, session_id: str | None = None, device_id: str | None = None):
    deleted_count = await delete_command_history_item(
        history_id=history_id,
        session_id=session_id,
        device_id=device_id,
    )
    return CommandHistoryMutationResponse(
        accepted=deleted_count > 0,
        deleted_count=deleted_count,
    )

@app.post("/app-catalog", response_model=AppCatalogResponse)
async def app_catalog(request: AppCatalogRequest):
    result = save_app_catalog(
        session_id=request.session_id,
        language=request.language,
        catalog_version=request.catalog_version,
        apps=request.apps,
    )
    db_persisted = await save_app_catalog_snapshot(
        session_id=result["session_id"],
        catalog_version=result["catalog_version"],
        language=result.get("language"),
        entries=result.get("apps", []),
        device_id=request.device_id,
        app_version=request.app_version,
        platform=request.platform,
    )
    redis_cached = False
    if db_persisted:
        redis_cached = await set_cached_app_catalog_snapshot(
            result["session_id"],
            {
                "catalog_version": result["catalog_version"],
                "language": result.get("language"),
                "apps": result.get("apps", []),
            },
        )

    print(
        "[app-catalog] "
        f"session_id={result['session_id']} | "
        f"catalog_version={result['catalog_version']} | "
        f"app_count={result['app_count']} | "
        f"db_persisted={db_persisted} | "
        f"redis_cached={redis_cached}",
        flush=True,
    )
    return AppCatalogResponse(
        accepted=db_persisted,
        session_id=result["session_id"],
        catalog_version=result["catalog_version"],
        app_count=result["app_count"],
    )


@app.delete("/app-catalog/{session_id}", response_model=AppCatalogCloseResponse)
def close_app_catalog(session_id: str):
    removed = delete_app_catalog(session_id)
    return AppCatalogCloseResponse(
        accepted=True,
        session_id=session_id,
        removed=removed,
        remaining_sessions=catalog_count(),
    )
