# Android Assistant Backend Refactored

Run:

```bash
# from Proje/Backend
uvicorn V3.main:app --host 127.0.0.1 --port 8001
```

Expected structure:

```text
V3/
|-- app_catalog/
|-- docs/
|-- extraction/
|-- intents/
|-- patterns/
|-- resources/
|-- models/
|   `-- result_model/
|-- rule_engine/
|-- services/
|   |-- app_catalog_service.py
|   |-- model_service.py
|   |-- predict_service.py
|   |-- rule_service.py
|   `-- validation_service.py
|-- tests/
|-- utils/
|-- validation/
|-- main.py
|-- config.py
`-- schemas.py
```
