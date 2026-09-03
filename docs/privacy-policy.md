# Privacy Policy for Android AI Assistant

**Effective date:** [EFFECTIVE DATE]

**Developer / publisher:** Muhammed Chreiki

**Privacy contact:** muhammedchreiki.03@gmail.com

This Privacy Policy explains how Android AI Assistant (the “App”) accesses,
uses, transmits, stores, and protects information when it provides voice-driven
Android automation, installed-app matching, and custom-command features. The
publisher name, contact address, effective date, production hosting provider,
and public policy URL must be completed and reviewed before publication.

## Information the App accesses or collects

### Voice commands and speech recognition

The App uses the speech-recognition service configured by Android through the
`SpeechRecognizer` API and microphone permission to turn speech into text. The
App does not explicitly select an on-device recognizer or a specific recognition
provider. Depending on the device, configured recognition provider, and its
settings, the recognition service may process microphone audio on the device or
remotely under the provider's terms and privacy practices.

The App does not read the recognizer's audio buffer and does not send raw
microphone audio or audio files to the Android AI Assistant backend. It receives
recognized text from Android and sends the following to the backend to resolve
the command:

- the primary recognized command text;
- recognition alternatives returned by Android;
- the selected command language;
- the stable Android device identifier described below;
- the current installed-app catalog version; and
- a yes-or-no signal indicating whether a search input is available on the
  current screen.

The backend processes command text, recognition alternatives, and derived
prediction parameters in memory to resolve the current request.
Raw command text is not persisted in command history.
Raw prediction parameters are not persisted in command history. Command history
contains only minimal operational metadata:
an internal device-record reference, intent identifier, language, acceptance or
failure state, confidence, bounded error code when present, processing time, and
timestamp. This rule applies regardless of intent, success, failure, UNKNOWN
classification, or future classifier changes. The search-input yes-or-no signal
is also used only while resolving the current request.

### Contacts and phone features

If the user chooses a contact-calling feature and grants Contacts permission,
the App queries contact names and phone numbers on the device to match the name
already present in the command. Contact records and the locally resolved phone
number are not sent to the Android AI Assistant backend. The recognized or typed
command itself is sent and processed as described above, so a contact name or
number spoken or typed as part of that command is included in the transmitted
command content but is not retained in command history.

The App can open the system dialer or, with Phone permission, request a direct
call. Phone State access is used only to pause listening while a call is active;
the phone number supplied to Android's call-state callback is ignored. Answer
Phone Calls access is used locally for an explicit answer or reject command.
Call-state values, call history, and resolved contact records are not sent to or
stored by the backend.

### Camera and flashlight features

Camera permission is used locally to control the device flashlight. For a photo
command, the App launches and can automate the system camera interface. The App
does not receive, upload, or store the resulting photo or video. Camera-facing
words included in a command are part of the command text and derived parameters
processed for the request but not retained in command history.

### AccessibilityService information

After the separate in-app disclosure and affirmative consent, the App's
AccessibilityService may inspect the current interface hierarchy when needed to
perform an action the user requested. This can include visible text, content
descriptions, view identifiers, field hints, control state and position,
package/window information, and node relationships.

Visible screen text, content descriptions, view identifiers, and node details
are processed on the device. They are not included in backend requests and are
not stored in PostgreSQL or Redis. Only the non-content `has_search_input`
yes-or-no signal is sent with a command and used ephemerally during resolution.
Content-bearing Accessibility matching logs are disabled in release builds.
Debug builds may contain interface labels and bounds in local Android Logcat.

### Installed application information

The App discovers applications that expose launcher activities. It sends their
display labels and package names, plus an app-count-derived catalog version,
language, session identifier, and device identifier, to the backend. It does not
use broad `QUERY_ALL_PACKAGES` visibility.

The backend stores package names in `apps`, and stores the device association,
display label, normalized label, and last catalog version in `device_apps`.
Generated matching aliases can be held in a Redis cache for up to two hours;
the Android client currently sends an empty aliases list. Catalog data is used
to match a requested app to a launchable package.

### Device identity and authentication

The App sends Android's app-scoped `ANDROID_ID` as a stable device identifier.
The backend stores it in `devices` with platform, app version, preferred
language, creation time, and last-seen time. The backend uses the internal
immutable database identifier of that device row, rather than duplicating the
raw Android identifier, to associate command-history metadata. The device record
also associates app catalogs, custom commands, and installation credentials with
the same installation. It is not represented as anonymous data.

The backend also reads the network address presented by a registration request
and uses it temporarily in a Redis rate-limit counter. Authenticated request
rate-limit counters use the backend's internal device-record identifier. These
counters contain a request count, expire after the configured window (one hour
for registration and one minute for the current authenticated endpoint groups),
and are not written to PostgreSQL or application logs.

At registration, the backend creates a random bearer credential and returns it
once to the App. The App encrypts it with AES-GCM using a key held by Android
Keystore and stores the encrypted value in private SharedPreferences. The App
sends the raw credential in the Authorization header for authenticated backend
requests. The backend uses it transiently for verification and stores only its
SHA-256 hash. Credential history, including revocation timestamps for rotated
credentials, remains in `device_auth_credentials`. The App and backend do not
intentionally log the raw credential or Authorization header.

### Custom commands

Custom commands are user-created content stored on the backend. Stored fields
include the command name, normalized name, language, enabled state, and ordered
steps. A step can contain an intent, parameters such as app names, search text,
or target text, a wait duration, and stop-on-failure behavior. There is no
separate custom-command alias field in the current API or database.

The user can create, edit, list, and delete custom commands in the App. Deleting
a custom command removes its backend `custom_commands` row, and the database
cascade removes its `custom_command_steps` rows.

### Local settings and consent records

The App stores language and theme preferences, whether optional permissions
were requested, and the accepted Accessibility disclosure version in private
SharedPreferences. The selected language is also sent with backend requests and
stored in the device and command-history metadata records. Session and catalog
state are held in memory. The encrypted bearer credential is stored locally as
described above.
Permission grant status is checked through Android and is not sent to the
backend.

## How information is used

Information is used only for current App functionality and security purposes:

- recognize, classify, validate, and execute a command;
- match a requested app against launcher-visible installed applications;
- resolve contact calling locally and perform user-requested device actions;
- save and run user-created custom command sequences;
- authenticate an installation, enforce request-size and rate limits, and bind
  backend records to the authenticated device; and
- record command results and limited operational diagnostics.

The source contains no advertising, marketing, analytics, or behavioral
profiling integration.

## Storage, retention, and deletion

PostgreSQL stores device records, launcher-visible app catalogs, minimal
command-history operational metadata, custom commands and their steps, and
credential hashes. Command history does not store raw command text, raw
prediction parameters, session identifiers, or the raw Android device
identifier. These records do not expire automatically because of inactivity.
They remain until an explicit product, administrator, or database operation
removes them. The App does not currently expose a self-service control for
deleting the device row, installed-app catalog, command-history metadata, or
credential history.

Redis stores a temporary copy of the installed-app catalog under a key derived
from the device identifier. That cache has a two-hour time-to-live and may also
be removed by the authenticated catalog-close endpoint. Removing or expiring
the Redis copy does not remove the PostgreSQL catalog. Redis also stores
short-lived request counters keyed by registration network address or internal
device-record identifier. The current counter windows are one hour for
registration and one minute for prediction, app-catalog, and custom-command
requests.

The App provides explicit deletion for an individual custom command, including
its steps. It does not create user accounts, and no “delete account” operation
exists. A global data-deletion request workflow has not been implemented; do not
represent the privacy contact placeholder as such a workflow until the
publisher establishes and documents one.

The schema defines `failed_app_open_attempts` and `error_messages` tables, but
the current backend has no application write path to either table. If records
are added by a future implementation or an administrator, the current schema
does not automatically expire them.

## Logs

Production backend logs contain limited operational information such as route
status, request duration, catalog counts, cache/database success flags, and
exception type. They do not intentionally include raw command text, prediction
parameters, device identifiers, app labels or package names, raw bearer tokens,
or Authorization headers. Log retention depends on the production hosting and
logging configuration, which is not defined in this repository and must be
documented before publication.

Android release logs contain operation failures and HTTP status information but
do not intentionally include recognized speech, contact content, Accessibility
labels, bearer credentials, Android ID, or installed package values.
Content-bearing Accessibility matching diagnostics are restricted to debug
builds and remain in local Logcat.

## Sharing and external services

The current source does not include advertising, analytics, Firebase, crash
reporting, remote monitoring, or an external AI API. Data sent to the Android AI
Assistant backend is first-party processing, not a transfer to an advertising
or data-broker integration.

Android speech recognition, the system camera, and the dialer are platform or
user-selected services. The App uses the speech-recognition service configured
by Android and does not explicitly select a specific provider. The configured
service can vary by device and settings, and may process speech data on the
device or remotely. Those services are governed by their providers' terms and
privacy practices; the App does not assume one universal speech-recognition
provider.

Production hosting and infrastructure providers are not selected in this
repository. Before publication, the publisher must identify any service
providers that process App data, confirm their role and practices, and update
this policy if required. The current source contains no sale, advertising, or
data-broker integration.

## Security and transport

The App uses per-device bearer authentication, Android Keystore-backed local
credential encryption, backend token hashing, request-size limits, and Redis
rate limits. These controls reduce risk but do not guarantee absolute security.
The repository does not prove encryption at rest for backend databases or logs,
so this policy makes no such claim.

The current Android configuration permits cleartext HTTP and its default local
backend URL uses HTTP. Production HTTPS/domain deployment is a separate required
hardening task. Until that task is completed and verified, the publisher must
not claim that all collected data is encrypted in transit and should not use the
current transport for real production user data over untrusted networks.

## User choices and controls

Users can stop listening, deny optional feature permissions, choose not to grant
Accessibility access, change language/theme, and delete individual custom
commands. Android system settings can revoke permissions or disable the
AccessibilityService. These actions do not automatically delete durable backend
records. Clearing app data removes local preferences and the encrypted local
credential but does not delete the stable backend device record or its related
data.

## Children's privacy

Our application is intended for a general audience, including users who rely on
accessibility features, and is not specifically directed toward children.
We do not knowingly collect personal information from children. We do not claim
compliance with any specific children's privacy law or regulatory framework.

## Changes to this policy

The publisher may update this policy when the App's data practices change. The
effective date above must be updated when a revised policy is published. Code,
the in-app policy, the hosted policy, the Accessibility disclosure, and Google
Play Data Safety answers must remain consistent.

## Contact

For privacy questions, contact:

Muhammed Chreiki
muhammedchreiki.03@gmail.com

This document is a technical, source-backed privacy draft and is not legal
advice.
