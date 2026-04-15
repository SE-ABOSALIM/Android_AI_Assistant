from fastapi import FastAPI, HTTPException
from model_service import predict_intent
from schemas import PredictRequest, PredictResponse

app = FastAPI(title="XLM-R Intent API")

VALID_LANGS = {"TR", "EN", "AR"}

@app.get("/")
def root():
    return {"message": "Intent API is running"}

@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    lang = request.language.upper().strip()

    if lang not in VALID_LANGS:
        raise HTTPException(status_code=400, detail="language must be TR, EN, or AR")

    result = predict_intent(request.text.strip(), lang)
    return result