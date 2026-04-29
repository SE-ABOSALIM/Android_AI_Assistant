# Android Assistant Backend Refactored

Run:

```bash
# from Proje/Backend
uvicorn V3.main:app --host 127.0.0.1 --port 8000
```

Expected structure:

```text
V3/
├── main.py
├── config.py
├── schemas.py
├── result_model/
└── services/
    ├── __init__.py
    ├── text_utils.py
    ├── thresholds.py
    ├── model_service.py
    ├── rule_service.py
    ├── extractors.py
    ├── validator.py
    └── predict_service.py
```
