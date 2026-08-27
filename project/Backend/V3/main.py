import asyncio
import time

from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, status

from V3.cache.app_catalog_cache import delete_cached_app_catalog_snapshot, set_cached_app_catalog_snapshot
from V3.config import MODEL_DIR
from V3.database.app_catalog_repository import save_app_catalog_snapshot
from V3.database.command_history_repository import record_command_history
from V3.database.custom_command_repository import (
    delete_custom_command,
    list_custom_commands,
    save_custom_command,
    update_custom_command,
)
from V3.database.installation_auth_repository import register_installation
from V3.middleware.request_body_limit import RequestBodyLimitMiddleware
from V3.schemas import (
    AppCatalogCloseResponse,
    AppCatalogRequest,
    AppCatalogResponse,
    AppCatalogStatusResponse,
    CustomCommandListResponse,
    CustomCommandMutationRequest,
    CustomCommandMutationResponse,
    FinalResponse,
    InstallationRegistrationRequest,
    InstallationRegistrationResponse,
    PredictRequest,
)
from V3.security.authentication import AuthenticatedInstallation, require_owned_device
from V3.security.rate_limit import (
    app_catalog_access,
    custom_command_access,
    predict_access,
    registration_access,
)
from V3.security.settings import max_request_body_bytes
from V3.services.model_service import get_device_name, preload_model
from V3.services.predict_service import predict_command
from V3.services.app_catalog_service import (
    catalog_count,
    get_app_catalog_status,
    save_app_catalog,
)
from V3.services.custom_command_service import try_build_custom_command_response

app = FastAPI(title="Android Assistant Intent API")
app.add_middleware(
    RequestBodyLimitMiddleware,
    max_body_bytes=max_request_body_bytes(),
)


@app.on_event("startup")
async def preload_intent_model():
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


@app.post(
    "/installations/register",
    response_model=InstallationRegistrationResponse,
    status_code=status.HTTP_201_CREATED,
)
async def register_android_installation(
    request: InstallationRegistrationRequest,
    _: None = Depends(registration_access),
):
    result = await register_installation(
        device_id=request.device_id,
        platform=request.platform,
        app_version=request.app_version,
        language=request.language,
    )
    if result.status not in {"created", "recovered"} or not result.credential:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Installation registration is temporarily unavailable",
        )
    return InstallationRegistrationResponse(credential=result.credential)


@app.post("/predict", response_model=FinalResponse)
def predict(
    request: PredictRequest,
    background_tasks: BackgroundTasks,
    identity: AuthenticatedInstallation = Depends(predict_access),
):
    device_id = require_owned_device(identity, request.device_id)
    started_at = time.perf_counter()
    response = try_build_custom_command_response(
        text=request.text,
        language=request.language,
        device_id=device_id,
    )
    if response is None:
        response = predict_command(
            text=request.text,
            language=request.language,
            text_alternatives=request.text_alternatives,
            session_id=device_id,
            device_id=device_id,
            catalog_version=request.catalog_version,
            has_search_input=request.has_search_input,
        )
    response["processing_time_ms"] = round((time.perf_counter() - started_at) * 1000, 2)
    background_tasks.add_task(
        _record_command_history_background,
        identity.device_ref_id,
        response.get("intent"),
        request.language,
        bool(response.get("accepted")),
        response.get("confidence"),
        response.get("error_code"),
        response["processing_time_ms"],
    )
    print(
        "\n[predict] "
        f"input: {response['input']} ||",
        f"intent={response['intent']} ||",
        f"duration_ms={response['processing_time_ms']:.2f} ||",
        f"confidence={response.get('confidence')} ||",
        f"accepted={bool(response.get('accepted'))}\n",
        flush=True,
    )
    return response


@app.get("/custom-commands", response_model=CustomCommandListResponse)
async def custom_commands(
    device_id: str,
    language: str = "TR",
    identity: AuthenticatedInstallation = Depends(custom_command_access),
):
    owned_device_id = require_owned_device(identity, device_id)
    return await list_custom_commands(device_id=owned_device_id, language=language)


@app.post("/custom-commands", response_model=CustomCommandMutationResponse)
async def create_custom_command(
    request: CustomCommandMutationRequest,
    identity: AuthenticatedInstallation = Depends(custom_command_access),
):
    device_id = require_owned_device(identity, request.device_id)
    item = await save_custom_command(
        device_id=device_id,
        language=request.language,
        name=request.name,
        steps=[step.dict() for step in request.steps],
    )
    return CustomCommandMutationResponse(
        accepted=item is not None,
        item=item,
        error_code=None if item is not None else "CUSTOM_COMMAND_SAVE_FAILED",
        error_message=None if item is not None else "Custom command could not be saved.",
    )


@app.put("/custom-commands/{command_id}", response_model=CustomCommandMutationResponse)
async def edit_custom_command(
    command_id: str,
    request: CustomCommandMutationRequest,
    identity: AuthenticatedInstallation = Depends(custom_command_access),
):
    device_id = require_owned_device(identity, request.device_id)
    item = await update_custom_command(
        command_id=command_id,
        device_id=device_id,
        language=request.language,
        name=request.name,
        steps=[step.dict() for step in request.steps],
    )
    return CustomCommandMutationResponse(
        accepted=item is not None,
        item=item,
        error_code=None if item is not None else "CUSTOM_COMMAND_UPDATE_FAILED",
        error_message=None if item is not None else "Custom command could not be updated.",
    )


@app.delete("/custom-commands/{command_id}", response_model=CustomCommandMutationResponse)
async def remove_custom_command(
    command_id: str,
    device_id: str,
    identity: AuthenticatedInstallation = Depends(custom_command_access),
):
    owned_device_id = require_owned_device(identity, device_id)
    deleted_count = await delete_custom_command(
        command_id=command_id,
        device_id=owned_device_id,
    )
    return CustomCommandMutationResponse(
        accepted=deleted_count > 0,
        deleted_count=deleted_count,
        error_code=None if deleted_count > 0 else "CUSTOM_COMMAND_DELETE_FAILED",
        error_message=None if deleted_count > 0 else "Custom command could not be deleted.",
    )


def _record_command_history_background(
    device_ref_id: str,
    intent: str,
    language: str,
    accepted: bool,
    confidence: float | None,
    error_code: str | None,
    processing_time_ms: float,
) -> None:
    try:
        asyncio.run(
            record_command_history(
                device_ref_id=device_ref_id,
                intent=intent,
                language=language,
                accepted=accepted,
                confidence=confidence,
                error_code=error_code,
                processing_time_ms=processing_time_ms,
            )
        )
    except Exception as exc:
        print(
            "[database] failed to record command history from predict endpoint | "
            f"error={type(exc).__name__}",
            flush=True,
        )


@app.post("/app-catalog", response_model=AppCatalogResponse)
async def app_catalog(
    request: AppCatalogRequest,
    identity: AuthenticatedInstallation = Depends(app_catalog_access),
):
    device_id = require_owned_device(identity, request.device_id)
    result = save_app_catalog(
        session_id=request.session_id,
        language=request.language,
        catalog_version=request.catalog_version,
        apps=request.apps,
    )
    db_persisted = await save_app_catalog_snapshot(
        session_id=device_id,
        catalog_version=result["catalog_version"],
        language=result.get("language"),
        entries=result.get("apps", []),
        device_id=device_id,
        app_version=request.app_version,
        platform=request.platform,
    )
    redis_cached = False
    if db_persisted:
        catalog_payload = {
            "catalog_version": result["catalog_version"],
            "language": result.get("language"),
            "apps": result.get("apps", []),
        }
        redis_cached = await set_cached_app_catalog_snapshot(
            device_id,
            catalog_payload,
        )

    print(
        "[app-catalog] "
        "authenticated_device=true | "
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


@app.get("/app-catalog/{session_id}", response_model=AppCatalogStatusResponse)
def app_catalog_status(
    session_id: str,
    identity: AuthenticatedInstallation = Depends(app_catalog_access),
):
    status = (
        get_app_catalog_status(identity.device_id)
        if session_id == identity.device_id
        else {"available": False, "catalog_version": None, "app_count": 0}
    )
    return AppCatalogStatusResponse(
        accepted=True,
        session_id=session_id,
        available=bool(status["available"]),
        catalog_version=status.get("catalog_version"),
        app_count=int(status.get("app_count") or 0),
    )


@app.delete("/app-catalog/{session_id}", response_model=AppCatalogCloseResponse)
async def close_app_catalog(
    session_id: str,
    identity: AuthenticatedInstallation = Depends(app_catalog_access),
):
    removed = (
        await delete_cached_app_catalog_snapshot(identity.device_id)
        if session_id == identity.device_id
        else False
    )
    return AppCatalogCloseResponse(
        accepted=True,
        session_id=session_id,
        removed=removed,
        remaining_sessions=catalog_count(),
    )
