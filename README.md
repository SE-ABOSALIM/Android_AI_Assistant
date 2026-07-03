<div align="center">

# Android AI Assistant

**A multilingual, AI-assisted voice control system for Android devices.**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](Proje/Android_App)
[![Java](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](Proje/Android_App)
[![Python](https://img.shields.io/badge/Python-3.13-3776AB?logo=python&logoColor=white)](Proje/Backend)
[![FastAPI](https://img.shields.io/badge/FastAPI-Backend-009688?logo=fastapi&logoColor=white)](Proje/Backend/V3)
[![XLM--RoBERTa](https://img.shields.io/badge/Model-XLM--RoBERTa-FFD21E)](Proje/Machine%20Learning%20Model)

</div>

## Overview

Android AI Assistant is a graduation project that converts spoken commands into structured Android actions. The system combines Android `SpeechRecognizer`, a FastAPI backend, deterministic command rules, an XLM-RoBERTa intent classifier, validation and parameter enrichment, and Android Accessibility/System APIs.

The application supports **English, Turkish, and Arabic**, including an RTL-aware Arabic interface. It is designed primarily for hands-busy scenarios and users with limited physical mobility. Initial installation and system-permission setup still require manual interaction and may require assistance.

## Highlights

- **41 Android-executable intents** covering app management, navigation, text input, search, calls, alarms, camera, media, and system settings.
- **Hybrid command understanding:** deterministic rules handle open-ended or safety-sensitive commands, while XLM-RoBERTa provides multilingual intent classification.
- **Dynamic UI interaction:** the Accessibility node tree is searched using exact, normalized, fuzzy, positional, and icon-alias matching.
- **Ambiguity handling:** matching UI elements can be numbered directly on screen for voice-based selection.
- **Custom command flows:** users can save and execute ordered, multi-step command sequences.
- **Persistent data layer:** PostgreSQL stores devices, app catalogs, command history, and custom flows; Redis accelerates app-catalog lookup.
- **Localized Android UI:** Home, Permissions, Custom Commands, Guide, and Settings screens are available in three languages.

## Application Screens

<table>
  <tr>
    <td align="center"><img src="docs/images/home.jpg" width="180" alt="Home screen"><br><b>Home</b></td>
    <td align="center"><img src="docs/images/permissions.jpg" width="180" alt="Permissions screen"><br><b>Permissions</b></td>
    <td align="center"><img src="docs/images/custom-commands.jpg" width="180" alt="Custom commands screen"><br><b>Custom Commands</b></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/guide.jpg" width="180" alt="Guide screen"><br><b>Guide</b></td>
    <td align="center"><img src="docs/images/settings.jpg" width="180" alt="Settings screen"><br><b>Settings</b></td>
    <td align="center"><img src="docs/images/dynamic-ui-selection.jpg" width="180" alt="Numbered UI candidates"><br><b>Dynamic Selection</b></td>
  </tr>
</table>

## System Architecture

```mermaid
flowchart LR
    A[Android SpeechRecognizer] --> B[FastAPI Backend]
    B --> C{Deterministic rule matched?}
    C -->|Yes| D[Rule Engine]
    C -->|No| E[XLM-RoBERTa Classifier]
    D --> F[Validation and Enrichment]
    E --> F
    F --> G[Android Command Executor]
    G --> H[Accessibility and System APIs]
    B <--> I[(PostgreSQL)]
    B <--> J[(Redis Cache)]
```

## Model Results

The multilingual classifier was fine-tuned from `FacebookAI/xlm-roberta-base` using 58 classification labels.

| Metric          | Validation |   Test |
| --------------- | ---------: | -----: |
| Accuracy        |     95.87% | 93.45% |
| Macro F1        |     95.08% | 90.93% |
| Intent accuracy |     97.49% | 94.31% |

## Technology Stack

| Layer          | Technologies                                                          |
| -------------- | --------------------------------------------------------------------- |
| Android client | Java, Android SDK, AccessibilityService, SpeechRecognizer, Retrofit   |
| Backend        | Python, FastAPI, PyTorch, Hugging Face Transformers                   |
| NLP            | XLM-RoBERTa, rule engine, parameter extraction, validation/enrichment |
| Data           | PostgreSQL, Redis                                                     |
| Languages      | English, Turkish, Arabic                                              |

## Repository Structure

```text
Android_AI_Assistant/
|-- Proje/
|   |-- Android_App/             # Android client and command execution
|   |-- Backend/V3/              # FastAPI, NLP pipeline, database, and cache
|   `-- Machine Learning Model/  # Dataset and model training scripts
|-- docs/                        # Thesis, presentation, and README media
`-- README.md
```

## Getting Started

### Prerequisites

- Android Studio and Android SDK 35
- JDK 17 or newer (Java 11 source compatibility)
- Python 3.13 and Pipenv
- PostgreSQL
- Redis (recommended for catalog caching)
- Android 7.0+ device; a physical device is recommended for Accessibility testing

### 1. Clone the repository

```bash
git clone https://github.com/SE-ABOSALIM/Android_AI_Assistant.git
cd Android_AI_Assistant
```

### 2. Download the trained model

The trained model is not stored in Git because of its size. Download the complete model bundle and place it under:

```text
Proje/Backend/V3/models/result_model/
```

The directory must contain `model.safetensors`, tokenizer files, and model configuration files.

**Model download:** [Google Drive link](https://drive.google.com/drive/u/5/folders/1hwkBiAgx0PFf08FkETy-YmI0nQt9L8YX)

### 3. Install backend dependencies

```bash
cd Proje/Backend
pip install pipenv
pipenv sync
```

### 4. Configure PostgreSQL and Redis

Copy the example environment file:

```powershell
Copy-Item V3/.env.example V3/.env
```

Update `V3/.env` with your local PostgreSQL credentials and Redis URL. Apply the SQL migrations in numerical order:

```powershell
$env:PSQL_DATABASE_URL="postgresql://postgres:YOUR_PASSWORD@localhost:5432/android-ai-assistant"
Get-ChildItem V3/database/migrations/*.sql |
  Sort-Object Name |
  ForEach-Object { psql $env:PSQL_DATABASE_URL -f $_.FullName }
```

### 5. Start the backend

```bash
pipenv run python -m uvicorn V3.main:app --host 0.0.0.0 --port 8001
```

API documentation is available at `http://localhost:8001/docs`.

### 6. Configure and run the Android application

```powershell
cd ../Android_App
Copy-Item local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
backend.baseUrl=http://YOUR_COMPUTER_LAN_IP:8001/
```

A physical phone cannot reach the computer backend through `localhost`; use the computer's LAN IP and keep both devices on the same network. Open `Proje/Android_App` in Android Studio, build the app, then grant the requested runtime and advanced Android permissions from the Permissions screen.

## Verification

```powershell
# Android unit tests
cd Proje/Android_App
.\gradlew.bat :app:testDebugUnitTest

# Backend tests
cd ../Backend
pipenv run python -m unittest discover V3.tests
```

Current verification status: Android unit tests pass, and the backend unittest suite passes with 120 tests. The backend suite covers rule parsing, validation, app-catalog matching, and intent-contract checks.

## Documentation

- [Graduation Thesis (TR)](<docs/Android_AI_Assistant_Thesis(TR).pdf>)
- [Project Presentation (TR)](<docs/Android_AI_Assistant_Presentation(TR).pdf>)

## Current Limitations

- Initial permission setup requires manual Android Settings interaction.
- UI automation quality depends on the accessibility metadata exposed by the active application.
- The Android client requires network access to the backend in the current architecture.
- Android speech-recognition session behavior can vary by device and installed recognition service.

## Future Work

- **Continuous speech recognition:** replace the session-based Android `SpeechRecognizer` flow with an `AudioRecord`, voice activity detection, and continuous STT pipeline to reduce listening interruptions.
- **Background-media protection:** reduce false command activation from videos and other media through wake-word detection, playback-state checks, and acoustic echo-cancellation techniques.
- **Optional offline operation:** evaluate on-device speech recognition and intent processing so that core commands can run without a backend connection where device resources permit.
- **More robust UI interaction:** supplement Accessibility metadata with OCR or lightweight computer-vision techniques when applications expose incomplete labels, descriptions, or resource identifiers.
- **Broader Android compatibility testing:** validate behavior across additional Android versions, device manufacturers, screen sizes, and vendor-specific Accessibility implementations.
- **User and pronunciation personalization:** support user-specific command aliases, pronunciation variants, and adaptive matching to improve recognition for names, applications, and frequently used commands.
- **More resilient custom command flows:** improve custom command execution to make multi-step workflows more flexible, scalable, and reliable across dynamic UI states.

## Author

**Muhammed Chreiki**<br>
Software Engineering, Istanbul Topkapi University<br>
[GitHub](https://github.com/SE-ABOSALIM)
