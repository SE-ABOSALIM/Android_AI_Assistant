from fastapi import FastAPI

from V3.config import MODEL_DIR
from V3.schemas import PredictRequest
from V3.services.model_service import get_device_name
from V3.services.predict_service import predict_command

app = FastAPI(title="Android Assistant Intent API")


@app.get("/")
def root():
    return {
        "message": "Android Assistant Intent API is running",
        "version": "rule_first_refactored_v1",
        "device": get_device_name(),
        "model_dir": str(MODEL_DIR),
    }


@app.post("/predict")
def predict(request: PredictRequest):
    return predict_command(
        text=request.text,
        language=request.language,
    )
