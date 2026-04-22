from fastapi import FastAPI, HTTPException
from model_service import predict_intent
from schemas import PredictRequest, PredictResponse

app = FastAPI(title="Joint NLU API")

VALID_LANGS = {"TR", "EN", "AR"}

@app.get("/")
def root():
    return {"message": "Joint NLU API is running"}

@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    lang = request.language.upper().strip()

    if lang not in VALID_LANGS:
        raise HTTPException(status_code=400, detail="language must be TR, EN, or AR")

    try:
        result = predict_intent(request.text.strip(), lang)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))