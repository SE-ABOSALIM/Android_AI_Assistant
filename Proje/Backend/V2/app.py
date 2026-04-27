from fastapi import FastAPI
from fastapi.responses import JSONResponse

from schemas import PredictRequest, PredictResponse, HealthResponse
from model_service import ModelService, MODEL_DIR


app = FastAPI(title="Android Assistant Intent API")

model_service = ModelService(model_dir=MODEL_DIR)


@app.get("/", response_model=HealthResponse)
def root() -> HealthResponse:
    return HealthResponse(
        message="Intent API is running.",
        model_loaded=True,
        model_path=MODEL_DIR,
    )


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    result = model_service.predict_intent(
        text=request.text,
        language=request.language,
    )
    return result