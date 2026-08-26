# Google Play Data Safety draft

**Audit date:** 2026-08-26  
**Package:** `com.example.anroidaiassistant`  
**Status:** Source-backed Play Console worksheet; not legal advice

This draft describes the current repository after Production Hardening 5/11.
Google defines collection as transmitting data off the user's device, including
ephemeral and pseudonymous data. Data used only on the device is not declared as
collected. “Shared” means transfer to a third party, subject to Google's stated
service-provider and user-initiated-action exceptions.

Official references used for the current taxonomy and policy requirements:

- [Google Play Data Safety form guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Android `SpeechRecognizer` API](https://developer.android.com/reference/android/speech/SpeechRecognizer)

## Play Console summary answers

| Question | Draft answer | Reason |
| --- | --- | --- |
| Does the app collect any required user-data type? | **Yes** | Recognized command content, launcher-visible installed apps, an app-scoped Android ID, language, custom commands, command results, and diagnostics leave the device. On devices without on-device recognition, the configured speech-recognition service may also receive microphone audio. |
| Does the app share user data? | **No, based on current source** | The Android client sends data only to the first-party backend. No ads, analytics, crash-reporting, remote-monitoring, or third-party AI SDK/API is integrated. The compatibility fallback uses whichever speech-recognition service Android has configured; the App does not select one universal provider. The provider/service-provider classification can therefore vary by device and should not be represented as a fixed integration. |
| Is all collected data encrypted in transit? | **No — BLOCKED UNTIL PRODUCTION HTTPS** | The default backend URL is HTTP, the manifest permits cleartext traffic, and no production TLS configuration is proved by the repository. |
| Can users request deletion of all collected data? | **No** | Individual custom commands can be deleted. There is no global deletion request flow or self-service deletion for device, catalog, command-history, or credential records. |
| Does the app create user accounts? | **No** | Authentication is installation/device based, not a user-account feature. |

## Proposed collected data types

| Google Play category / type | Collected | Shared | Ephemeral | Required or optional | Purpose |
| --- | --- | --- | --- | --- | --- |
| Audio files — Voice or sound recordings | **Yes, conservatively for the global form** | No under the user-initiated/Android-configured recognition-service analysis; classification may vary with the configured service | **No — the architecture cannot guarantee ephemeral processing across every Android-configured service** | Required for voice mode | App functionality. The App prefers on-device recognition. When it is unavailable or unsupported, Android's configured recognizer is used for compatibility and availability; that service can vary by device and may process audio remotely. The Android AI Assistant backend never receives audio. |
| Personal info — Other info | **Yes** | No | No | Required | App functionality; Personalization. The selected language is always sent for registration/command/catalog handling and is stored. |
| Contacts — Contacts | **Yes, conservatively** | No | No | Optional | App functionality. A contact name or number spoken/typed in a call command is transmitted as command content and the extracted `contact_name` may be stored. The App does **not** upload address-book query results or the locally resolved number. |
| App activity — App interactions | **Yes** | No | No | Required | App functionality. Intent, extracted parameters, accepted/failed status, error code, confidence, and command result metadata are stored. |
| App activity — In-app search history | **Yes** | No | No | Optional | App functionality. Search commands and extracted query text are stored as command content/parameters only when the user uses search. |
| App activity — Installed apps | **Yes** | No | No | Required | App functionality. Launcher-visible app labels and package names are synchronized when the App starts/warm-ups the catalog. |
| App activity — Other user-generated content | **Yes** | No | No | Required | App functionality. Voice/manual command text is required for the assistant; optional custom-command names and steps share this type. Because the same type is required for primary functionality, mark the type required. |
| App info and performance — Diagnostics | **Yes** | No | No | Required | App functionality. Processing time, accepted/failed status, and error code are stored with each command. Operational logs also record safe status/timing/count/error-type metadata. |
| App info and performance — Other app performance data | **Yes** | No | No | Required | App functionality; Fraud prevention, security, and compliance. Platform and app version are sent during installation registration and stored with the device. |
| Device or other IDs — Device or other IDs | **Yes** | No | No | Required | App functionality; Fraud prevention, security, and compliance. `ANDROID_ID`, an in-memory session ID, the installation credential/device association, and the registration request's network address support ownership, authentication, and abuse prevention. |

“Required” means users cannot use the relevant primary backend assistant flow
without this collection. Optional feature permissions do not make the core
command/device/catalog collection optional. Custom commands, contact calling,
and search are feature choices, but command content as a data type is still
required by the primary assistant function.

## Data types evaluated as not collected

| Google Play category / type | Draft answer | Source-backed reason |
| --- | --- | --- |
| Photos and videos — Photos / Videos | **No** | The App launches/automates the system camera and does not receive or upload captured media. Camera permission is used for torch control. |
| Contacts — address-book records and resolved phone numbers | **No for this sub-flow** | `ContactsContract` results stay on-device. The conservative Contacts “Yes” above is limited to contact information the user includes in transmitted command content. |
| Personal info — Phone number | **No for the user's own number** | The App does not read or transmit the user's own phone number. A target number explicitly spoken in a command is treated as command/contact content. |
| App info and performance — Crash logs | **No** | No crash-reporting SDK or crash-log upload path exists. Local exception logs are not transmitted by application code. |
| Accessibility screen/node content | **No** | Visible text, content descriptions, view IDs, hierarchy, state, and bounds remain on-device. Only the non-content `has_search_input` Boolean leaves the device. |
| Phone/call history or call state | **No** | Active-call state is used locally to pause listening; callback phone number is ignored. No call history is read. |
| Permission/access status | **No** | Android permission and Accessibility state checks remain local. |

## Complete source-backed data inventory

“Persistent” below means the current code has a durable write path. PostgreSQL
records have no inactivity expiry. “No app delete” does not rule out a manual
administrator/database operation.

| Data | Source / API / permission | Local access | Off-device endpoint | Persistence / store | Logging after 5/11 | Purpose | Deletion and retention | Third-party sharing | Play mapping |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Recognized voice text | `SpeechRecognizer.RESULTS_RECOGNITION`; `RECORD_AUDIO` | Yes | `POST /predict`: `text` | PostgreSQL `command_history.text` | Not in production Android/backend logs | Resolve and execute commands | No app deletion route; retained until explicit admin/DB removal | No first-party backend sharing; recognizer caveat applies | Other user-generated content; also Contacts/Search where applicable |
| 2. Recognition alternatives | Same callback list; maximum result request is currently 1 | Yes | `POST /predict`: `text_alternatives` | Used in memory during validation/app matching; not directly written | No | Improve app-name matching | Ephemeral request processing | No | Other user-generated content, ephemeral collection |
| 3. Raw microphone audio | Android `SpeechRecognizer`; `RECORD_AUDIO`; audio callback ignored | Recognizer/service accesses it; App does not consume buffer | No Android AI Assistant backend endpoint/field. The Android-configured compatibility fallback may process it remotely | None in App/backend; handling outside the App varies with the recognizer configured on the device | No | Preserve voice-command compatibility and availability when on-device recognition is unavailable or unsupported | Controlled by the Android-configured recognizer; no universal provider is assumed | No under the current user-initiated/system-service analysis; reassess only if the App directly integrates or selects a provider | Voice recordings: Yes conservatively for the global form; not received by App backend |
| 4. Contacts/names/numbers | `ContactsContract.CommonDataKinds.Phone`; `READ_CONTACTS` | Names/numbers queried and matched locally | Local query results: none. A name/number included in command text goes to `/predict` | Local result: none. Command text and extracted `contact_name` can be in `command_history` | No content logging | Resolve a requested call target | No backend contact-book record to delete; command history persists | No | Contacts: Yes conservatively for transmitted command contact content |
| 5. Phone/call state | `TelephonyManager`, `TelecomManager`; `READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS` | Active/off-hook Boolean; call actions | None | None | No | Pause listening; dial/answer/reject on request | Ends with local process/state | Dialer/telecom action is user-requested platform interaction | No collected call-state type |
| 6. Camera/torch/media | `CameraManager`, system camera intent; `CAMERA` for torch | Torch state and camera UI automation | Only command text/derived camera-facing parameter via `/predict`; no media | Command parameter may persist; no photo/video store | No media logging | Torch control and user-requested system camera action | Media remains under system camera; backend command history persists | System camera is user-requested platform app | Photos/videos: No |
| 7. Accessibility screen/node data | `AccessibilityNodeInfo`, active-window APIs; AccessibilityService | Text, descriptions, IDs, hints, class/package/window, hierarchy/state/bounds | None of the content | None | Release: no. Debug-only local matching diagnostics can contain labels/bounds | Locate and operate requested UI | In-memory; debug Logcat lifecycle is device/tool controlled | No | Not collected |
| 8. `has_search_input` | Derived by `SearchInputController.hasSearchInputAvailable()` | Yes | `POST /predict`: Boolean | Not written to command history/cache/database | Not logged | Resolve ambiguous search/call commands | Ephemeral | No | App interaction signal, ephemeral |
| 9. Installed-app catalog | Launcher intent query; no `QUERY_ALL_PACKAGES` | Launcher-visible list | `POST /app-catalog` | PostgreSQL `apps` + `device_apps`; Redis snapshot | Only count/version/success flags, no entries | App-name/package resolution | PostgreSQL indefinite; Redis TTL 2h | No | Installed apps |
| 10. Package names/app labels | `InstalledAppReader`; launcher activities | Yes | Each `apps[]`: `label`, `package_name`, `aliases` (currently empty) | `apps.package_name/display_name`; `device_apps.display_name/normalized_name` | Package values removed from release logs | Match and launch apps | Stale catalog rows replaced on explicit sync; current catalog otherwise indefinite | No | Installed apps |
| 11. Catalog aliases/hashes/version | Android sends empty aliases and a count/hash-derived catalog version; backend generates match aliases | Yes | `catalog_version`, empty per-app `aliases` | Version in `device_apps`; generated `match_aliases` in Redis payload, not current PostgreSQL schema | Not logged | Efficient language/app matching | Redis 2h; PostgreSQL version indefinite | No | Installed apps / technical metadata |
| 12. `ANDROID_ID` / stable device identity | `Settings.Secure.ANDROID_ID` | Yes | Registration, app catalog, predict, custom-command query/body | `devices.device_id`; linked FKs | Not logged | Device association and installation ownership | No app deletion; indefinite | No | Device or other IDs |
| 13. Session ID | Random UUID held by `AssistantSession` | Yes | App-catalog paths/body and `/predict` | `command_history.session_id`; app catalog persists by authenticated device instead | Not logged | Correlate local assistant session/request | Memory clears when session ends; history copy indefinite | No | Device or other IDs |
| 14. Raw bearer credential | Backend-generated random value; Android Authorization header | Yes | Every protected endpoint header | Android: AES-GCM ciphertext in private SharedPreferences with Keystore key. Backend: raw value not stored | Never intentionally logged | Authenticate installation | Local clear-data removes ciphertext; backend rotation revokes prior hash row | No | Device or other IDs / security credential |
| 15. Bearer token hash | Backend SHA-256 of credential | No | Generated server-side | `device_auth_credentials.token_hash`, with created/revoked timestamps | Not logged | Credential verification and rotation history | No inactivity deletion; device deletion would cascade | No | Device or other IDs / security |
| 16. Custom command name/state | User entry; no separate alias field | Yes | `/custom-commands` GET/POST/PUT/DELETE | `custom_commands`: name, normalized name, language, enabled, timestamps | Not logged | User-created reusable automation | Explicit delete; indefinite until deletion | No | Other user-generated content, optional feature |
| 17. Custom-command steps | User entry | Yes | Step `intent`, `parameters`, `wait_after_ms`, `stop_on_failure` | `custom_command_steps.parameters_json` and fields | Not logged | Execute deterministic workflow | Cascade-deleted with parent command; otherwise indefinite | No | Other user-generated content, optional feature |
| 18. Command history | Backend prediction result | No | Created from `/predict` | `command_history`: text, language, intent, parameters, accepted, confidence, status, error code, timing, identifiers | Content no longer printed; safe status/timing remains | Persist command outcomes | Repository delete methods exist but no exposed app/API route; indefinite | No | UGC, App interactions, Search history, Diagnostics |
| 19. Intent/parameters/confidence | Rule/ML/validation output | Returned to Android | `/predict` response | Intent/parameters/confidence persist in command history; raw label/top predictions do not | Raw parameters removed from logs | Command execution/validation | Persisted fields indefinite | No | App interactions; UGC/Search/Contacts depending parameter |
| 20. Failed app-open attempts | Schema only | No current application capture | None | Table exists but no `INSERT` path | No | Reserved diagnostic schema | Existing/manual rows have no expiry | No | Not currently collected; potential Diagnostics/Installed apps if implemented |
| 21. Error messages table | Schema only | No current application capture | None | Table exists but no `INSERT` path | No | Reserved diagnostic schema | Existing/manual rows have no expiry | No | Not currently collected; potential Diagnostics if implemented |
| 22. Redis app catalog | Derived from PostgreSQL/sync | No | Server-side only | Key `app_catalog:<device_id>`; labels, packages, aliases, match aliases, language, version; TTL 7,200 seconds | Error type only | Fast app matching | Automatic Redis TTL 2h; authenticated close can delete cache only | No | Installed apps, temporary cached copy |
| 23. Local Android diagnostic logs | Android `Log` calls | Device only | None by App | Logcat only | Safe operational failures; sensitive Accessibility candidate details debug-only | Diagnostics | Android/ADB Logcat lifecycle | No | Not collected |
| 24. Backend application logs | `print` calls | Server side | Already server-side | Destination/retention set by undeclared hosting runtime | Timing, status, counts, safe flags, exception type; no raw text/params/token/device/app value | Operations | Production retention not defined; publication placeholder | No SDK/server transfer in source | Diagnostics |
| 25. Local preferences | `assistant_settings`, feature permission flags, Accessibility consent, encrypted credential | Yes | Language and credential are transmitted in their respective flows; theme/flags/consent are not | Private SharedPreferences; Keystore key for credential | No | Preferences, consent, auth | App clear-data/uninstall behavior; Android backup configuration does not explicitly exclude them | No | Language/ID mappings above; other values not collected |
| 26. Language/platform/app version | App setting and build metadata | Yes | Registration; language also predict/catalog/custom queries | `devices.preferred_language/platform/app_version`; command history language | Language/version values not logged | Correct processing, compatibility, security context | Indefinite with device row | No | Other info; Other app performance data |
| 27. Rate-limit identity/counters | Registration request network address; authenticated installation's internal `device_ref_id` | No Android API access; backend receives network metadata | Network address accompanies registration; internal ID is server-derived | Redis `rate_limit:<group>:<identity>` stores a count with TTL; no PostgreSQL write | Neither identity nor count is logged; Redis failure logs error type only | Abuse prevention and service availability | Registration: 3,600s; predict/app-catalog/custom-command: 60s by default; settings can override | No | Device or other IDs; Fraud prevention, security, and compliance |

## Network field inventory

All endpoints except installation registration receive an automatically attached
`Authorization: Bearer <credential>` header.

| Endpoint | Android request fields | Backend response fields |
| --- | --- | --- |
| `POST /installations/register` | `device_id`, `platform`, `app_version`, `language` | `credential`, `token_type` |
| `POST /app-catalog` | `session_id`, `device_id`, `language`, `catalog_version`, `apps[]` (`label`, `package_name`, `aliases`) | `accepted`, `session_id`, `catalog_version`, `app_count` |
| `GET /app-catalog/{session_id}` | Path `session_id` | `accepted`, `session_id`, `available`, `catalog_version`, `app_count` |
| `DELETE /app-catalog/{session_id}` | Path `session_id` | Backend: `accepted`, `session_id`, `removed`, `remaining_sessions`; Android discards the body |
| `POST /predict` | `text`, `language`, `text_alternatives`, `session_id`, `device_id`, `catalog_version`, `has_search_input` | `input`, `normalized_input`, `language`, `intent`, `parameters`, support/contract flags, `accepted`, missing slots, error fields, confirmation flag, `confidence`, `threshold`, `raw_label`, processing time, `top_predictions` |
| `GET /custom-commands` | Query `device_id`, `language` | Items: ID, name, language, enabled, steps, created/updated time |
| `POST /custom-commands` | `device_id`, `language`, `name`, `steps[]` | Accepted/item or error fields |
| `PUT /custom-commands/{command_id}` | Path ID plus the same mutation body | Accepted/item or error fields |
| `DELETE /custom-commands/{command_id}` | Path ID plus query `device_id` | Accepted/deleted count or error fields |

## PostgreSQL retention and deletion

- There is no automatic deletion based on 3-day, 30-day, 90-day, one-year, or
  other inactivity intervals.
- A catalog sync explicitly removes `device_apps` rows absent from the newest
  catalog version; it does not delete unrelated device data.
- Deleting a custom command through the App/API deletes that command and its
  steps through `ON DELETE CASCADE`.
- Credential rotation revokes the previous active credential; it retains the
  hash and revocation history.
- Command-history clear/item-delete repository functions exist, but no current
  FastAPI route or Android UI invokes them.
- The catalog-close route removes only Redis cache. It does not remove the
  PostgreSQL device or app catalog.
- `devices`, `apps`, `device_apps`, `command_history`, credential history, and
  any schema-only error/failed-attempt rows otherwise remain until an explicit
  administrator/database/product operation removes them.

## Redis key families

| Key family | Stored data and association | Default TTL | Purpose |
| --- | --- | --- | --- |
| `app_catalog:<device_id>` | Catalog version, language, launcher labels, package names, supplied aliases, and generated match aliases associated with the authenticated device | 7,200 seconds | Fast app matching; the authenticated catalog-close route can delete this cache copy |
| `rate_limit:registration:<client_host>` | Registration request count associated with the network address presented to FastAPI | 3,600 seconds | Registration abuse prevention; registration fails closed if Redis is unavailable |
| `rate_limit:predict:<device_ref_id>` | Request count associated with the backend's internal authenticated-device row identifier | 60 seconds | Prediction throttling |
| `rate_limit:app_catalog:<device_ref_id>` | Request count associated with the internal authenticated-device identifier | 60 seconds | Catalog endpoint throttling |
| `rate_limit:custom_commands:<device_ref_id>` | Request count associated with the internal authenticated-device identifier | 60 seconds | Custom-command endpoint throttling |

The rate-limit values are configurable through environment settings. The table
states source defaults. Authenticated endpoint rate limits fail open when Redis
is unavailable; registration fails closed. No rate-limit key stores command
text, app labels/packages, a bearer token, or an Authorization header.

## Third-party and sharing review

Android dependencies are AndroidX UI libraries, Material Components, Retrofit,
Gson, and test libraries. The backend uses FastAPI, PyTorch/Transformers,
Uvicorn, asyncpg, and Redis. The source has no analytics, advertising, Firebase,
crash reporting, remote monitoring, external AI API, or social SDK data path.
The Transformer model runs inside the backend process.

Android `SpeechRecognizer` is a platform API. On-device recognition is preferred.
It may be unavailable or unsupported for a device, Android version, language or
model configuration, or recognizer implementation. In that case, the App uses
Android's configured recognition service as a compatibility and availability
fallback so that voice commands remain available. The configured recognizer can
vary by device, and Android documents that the general API implementation may
stream audio to remote servers. The App does not receive audio bytes or send them
to its backend; its backend receives recognized text. Because remote audio
processing remains a possible supported behavior, the conservative global-form
answer remains **Voice or sound recordings: collected** and is not marked
ephemeral. This conclusion does not assume one universal provider. System camera
and dialer launches are user-requested platform interactions.

The production backend hosting provider is not defined. A host that processes
data only on the publisher's instructions can qualify as a service provider
under Google's definition, but the actual contract and use must be verified.
Any independent use by a provider or any future SDK must be reassessed as
sharing.

## Encryption, deletion, and publication blockers

### BLOCKED UNTIL PRODUCTION HTTPS

Do not select “all data encrypted in transit.” `BuildConfig.BACKEND_BASE_URL`
defaults to `http://10.0.2.2:8001/`, and the manifest enables cleartext traffic.
Select **No** in the current form draft. Change the answer only after the
separate production HTTPS task is complete and the release artifact is verified.

### Deletion mechanism answer

Select **No** for a general deletion-request mechanism. The custom-command delete
button is real but does not delete command history, device identity, catalog, or
credential history. The App has no user accounts, so account-deletion rules are
not triggered by an account feature. A global deletion workflow can be a future
product/compliance decision; this task does not invent one.

### Publication placeholders

Before Play submission, supply and verify:

1. publisher/legal display name matching the Play listing;
2. privacy contact email;
3. effective date;
4. active, public, non-geofenced, non-editable hosted privacy-policy URL;
5. production hosting/logging providers and their retention/processing terms;
6. target audience and children's-privacy position;
7. completed HTTPS deployment and a verified release transport configuration.

## Consistency matrix

| Code behavior | Privacy Policy | Data Safety answer |
| --- | --- | --- |
| Recognized text goes to `/predict` and primary text is stored in command history | Disclosed as transmitted and durable | Other user-generated content: Yes; non-ephemeral |
| Raw audio has no App/backend field or write path, but the Android-configured compatibility fallback may process it remotely | Backend receives recognized text, not raw audio; fallback purpose and device-dependent behavior disclosed | Voice recordings: Yes conservatively because remote processing remains possible; no universal provider or provider-verification blocker assumed |
| Launcher labels/packages are persisted and cached | Installed-app section identifies PostgreSQL and 2h cache | Installed apps: Yes; non-ephemeral |
| `ANDROID_ID` and token hash bind persistent records | Device/auth section distinguishes ID, raw token, and hash | Device or other IDs: Yes |
| Contact records resolve locally; spoken contact content is in command history | Both paths are explicitly distinguished | Contacts: conservative Yes for command content; no upload of address book results |
| Accessibility nodes remain local; only Boolean leaves device | Local processing and release/debug logs disclosed | Node content: No; Boolean ephemeral |
| Custom commands and steps persist; delete cascades | Creation/storage/deletion accurately described | Other user-generated content: Yes, optional feature |
| Command history has no inactivity cleanup | Indefinite-until-explicit-operation wording | Non-ephemeral; no general deletion mechanism |
| Backend logs no longer print raw text/parameters | Only safe operational fields are described | Diagnostics only; no hidden content-log purpose |
| Default transport is HTTP/cleartext allowed | No complete encryption-in-transit claim | Encryption in transit: No / blocked |

## Source map

- Android network contract: `api/ApiService.java` and `api/dto/*`
- Voice flow: `MyAccessibilityService.java` and `api/dto/PredictRequest.java`
- App catalog: `InstalledAppReader.java`, `AppCatalogSyncer.java`,
  `database/app_catalog_repository.py`, and `cache/app_catalog_cache.py`
- Accessibility: `MyAccessibilityService.java`, `accessibility/*`, and
  `docs/google-play-accessibility-declaration.md`
- Contacts/phone/camera: `contacts/*`, `telephony/CallStateMonitor.java`, and
  relevant command handlers
- Identity/auth: `DeviceIdentity.java`, `api/auth/*`,
  `database/installation_auth_repository.py`, and migration 008
- Durable schema: migrations 001–008 and the three database repositories
- Retention invariant: `tests/test_database_retention.py`
- Third-party dependencies: Android version catalog/app Gradle files and backend
  `Pipfile`
