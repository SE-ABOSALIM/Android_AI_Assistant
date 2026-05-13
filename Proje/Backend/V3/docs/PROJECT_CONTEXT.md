# Android AI Assistant Project Context

Last updated: 2026-05-13

This file is the persistent project context for future Codex sessions. Read this
before making backend or Android changes.

## Project Purpose

The project is an Android AI Assistant. The user speaks or types a natural
language command. The backend predicts an intent and parameters, validates the
contract, enriches missing parameters when possible, and returns a structured
response for the Android app to execute.

The main goal right now is to integrate newly trained dataset intents cleanly
without turning the backend into large hard-coded files again.

## Repository Areas

- Backend V3:
  `C:/Users/mahmi/Desktop/Univercity/Diger/Android_AI_Assistant/Proje/Backend/V3`
- Android app:
  `C:/Users/mahmi/Desktop/Univercity/Diger/Android_AI_Assistant/Proje/Android_App`
- Dataset:
  `C:/Users/mahmi/Desktop/Univercity/Diger/Android_AI_Assistant/Proje/Machine Learning Model/intent_dataset.xlsx`

Backend is the current priority. Android exists and works in parts, but its
structure is known to be much messier and should be handled later unless the
specific feature requires Android execution support.

## Backend Architecture

Prediction flow:

1. `services/predict_service.py`
   - Runs rule-based matching first.
   - Falls back to ML model prediction.
   - Sends the result to validation.

2. `rule_engine/`
   - Modular rule-based command handling.
   - `registry.py` defines handler order.
   - Used for open-ended or deterministic commands such as `go back`,
     `geri git`, `open WhatsApp`, `parlakligi yukselt`, volume commands, etc.

3. `services/model_service.py`
   - Lazy-loads the local Hugging Face model from `models/result_model/`.
   - Converts labels into `{intent, parameters}`.
   - Keeps unsupported model intents as their real intent instead of masking
     them as `UNKNOWN_COMMAND`.

4. `intents/registry.py`
   - Central source for intent contracts.
   - Defines thresholds, required parameters, one-of parameter groups,
     optional parameters, and Android support.
   - `UNKNOWN_COMMAND` and `UNSUPPORTED_INTENT` must remain distinct.

5. `validation/`
   - Registry-driven validation.
   - Enriches parameters for app commands, contacts, text, timers, search,
     alarms, etc.
   - Builds the final response with backend support, Android support, missing
     slots, error codes, confidence, thresholds, and top predictions.

6. `extraction/`
   - Parameter extraction modules split by domain.
   - Keep parameter extraction split by domain.

7. `patterns/commands/`
   - Command pattern lists split by domain.
   - `patterns/command_patterns.py` is only a compatibility facade.

8. `app_catalog/`
   - Stores and matches installed Android apps by session.
   - Used heavily by `OPEN_APP`, `OPEN_APP_INFO`, and `UNINSTALL_APP`.

## Current Intent/Feature Decisions

- Backend intent support is controlled by `intents/registry.py`.
- Android support is separate from backend support.
- Unsupported known/future model intent:
  - intent remains the predicted intent
  - `accepted = false`
  - `error_code = UNSUPPORTED_INTENT`
- Unknown/unrecognized command:
  - intent is `UNKNOWN_COMMAND`
  - `error_code = UNKNOWN_COMMAND`
- Low confidence:
  - intent becomes `UNKNOWN_COMMAND`
  - `error_code = LOW_CONFIDENCE`

## Important Recent Work

- Added backend intent registry.
- Converted validator to registry-based validation.
- Split validation, extraction, rule-based logic, and command patterns into
  smaller modules.
- Added backend tests for intent contracts, rule behavior, and pattern facade.
- Fixed app opening confusion by adding deterministic `OPEN_APP` rule handling
  before ML fallback.
- Added volume level support for `ADJUST_VOLUME`:
  - `volume_level = low`
  - `volume_level = medium`
  - `volume_level = max`
  - legacy `high` or `maximum` may normalize to `max` as fallback.

## New Intent Integration Workflow

When adding a backend-supported intent:

1. Add or update the contract in `intents/registry.py`.
2. If the command is deterministic or open-ended, add rule handling under
   `rule_engine/`.
3. Add domain patterns under `patterns/commands/`, not in the facade file.
4. Add parameter extraction under `extraction/` when needed.
5. Add validation enrichment in `validation/enrichers.py` only if the
   intent can recover or infer missing parameters.
6. Add tests in `tests/`.
7. Run backend verification.

## Verification Commands

From:
`C:/Users/mahmi/Desktop/Univercity/Diger/Android_AI_Assistant/Proje/Backend`

```powershell
python -m unittest discover V3.tests
python -m compileall V3
```

To run the backend:

```powershell
uvicorn V3.main:app --host 127.0.0.1 --port 8000
```

Android compile check, only when Android files are touched:

```powershell
cd C:/Users/mahmi/Desktop/Univercity/Diger/Android_AI_Assistant/Proje/Android_App
.\gradlew.bat :app:compileDebugJavaWithJavac
```

## Known Backend Quality Notes

The backend is now structurally much cleaner and ready for new intent work, but
it is not production-grade yet.

Remaining technical debt:

- `intents/registry.py` can grow large as intent count increases.
- Pattern files are split, but the pattern-list approach will still need strong
  tests as coverage grows.
- Request schemas need stricter limits for `text`, `language`, `session_id`,
  and app catalog size if the backend is exposed outside local/dev use.
- App catalog storage is in-memory and session-based. This is fine for local
  assistant use, but not for multi-worker or persistent production use.
- `main.py` uses `print` for request logging. Structured logging would be
  cleaner.
- Arabic command coverage now lives in the language-keyed pattern dictionaries
  under `patterns/commands/`; keep Arabic examples covered by tests when adding
  new deterministic rules.

## User Preferences

- The user usually wants direct, practical engineering judgment.
- Respond in Turkish unless there is a clear reason not to.
- If the user asks only for review or structure analysis, do not edit files.
- If the user asks to implement, make the change and verify it.
- Keep Android out of scope unless the requested backend feature requires
  Android execution support.
- Do not revert unrelated user changes.
