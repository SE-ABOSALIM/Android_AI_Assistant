from fastapi import FastAPI

from V3.config import MODEL_DIR
from V3.schemas import AppCatalogCloseResponse, AppCatalogRequest, AppCatalogResponse, FinalResponse, PredictRequest
from V3.services.model_service import get_device_name
from V3.services.predict_service import predict_command
from V3.services.app_catalog_service import catalog_count, delete_app_catalog, save_app_catalog

app = FastAPI(title="Android Assistant Intent API")


@app.get("/")
def root():
    return {
        "message": "Android Assistant Intent API is running",
        "version": "app_catalog_validation_v1",
        "device": get_device_name(),
        "model_dir": str(MODEL_DIR),
    }


@app.post("/predict", response_model=FinalResponse)
def predict(request: PredictRequest):
    return predict_command(
        text=request.text,
        language=request.language,
        session_id=request.session_id,
        catalog_version=request.catalog_version,
    )


@app.post("/app-catalog", response_model=AppCatalogResponse)
def app_catalog(request: AppCatalogRequest):
    result = save_app_catalog(
        session_id=request.session_id,
        catalog_version=request.catalog_version,
        apps=request.apps,
    )
    return AppCatalogResponse(
        accepted=True,
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
