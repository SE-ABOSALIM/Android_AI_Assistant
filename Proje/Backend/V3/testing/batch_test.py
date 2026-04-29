import requests
import json
import time

API_URL = "http://127.0.0.1:8000/predict"

TEST_CASES = [
        {"text": "Sesi kapat", "language": "tr"},
        {"text": "Sesi sustur", "language": "tr"},
        {"text": "Sessize al", "language": "tr"},
        {"text": "Telefonu sessize al", "language": "tr"},

        {"text": "Sesi aç", "language": "tr"},
        {"text": "Sesi geri aç", "language": "tr"},
        {"text": "Sessizi kaldır", "language": "tr"},
        {"text": "Telefonun sesini geri aç", "language": "tr"},

        {"text": "Mute the sound", "language": "en"},
        {"text": "Mute audio", "language": "en"},
        {"text": "Turn off the sound", "language": "en"},
        {"text": "Silence the phone", "language": "en"},

        {"text": "Unmute the sound", "language": "en"},
        {"text": "Unmute audio", "language": "en"},
        {"text": "Turn the sound back on", "language": "en"},
        {"text": "Enable sound", "language": "en"},

        {"text": "اكتم الصوت", "language": "ar"},
        {"text": "اغلق الصوت", "language": "ar"},
        {"text": "اجعل الهاتف صامت", "language": "ar"},
        {"text": "حول الهاتف إلى صامت", "language": "ar"},

        {"text": "افتح الصوت", "language": "ar"},
        {"text": "الغ كتم الصوت", "language": "ar"},
        {"text": "ارجع الصوت", "language": "ar"},
        {"text": "شغل الصوت مرة أخرى", "language": "ar"}
]

results = []

for i, case in enumerate(TEST_CASES, start=1):
    start = time.perf_counter()

    try:
        response = requests.post(API_URL, json=case, timeout=10)
        elapsed_ms = round((time.perf_counter() - start) * 1000, 2)

        item = {
            "index": i,
            "input": case,
            "status_code": response.status_code,
            "elapsed_ms": elapsed_ms,
            "response": response.json()
        }

    except Exception as e:
        elapsed_ms = round((time.perf_counter() - start) * 1000, 2)

        item = {
            "index": i,
            "input": case,
            "elapsed_ms": elapsed_ms,
            "error": str(e)
        }

    results.append(item)

    print(f"\n#{i} INPUT:")
    print(json.dumps(case, ensure_ascii=False, indent=2))

    print("RESPONSE:")
    print(json.dumps(item.get("response", item.get("error")), ensure_ascii=False, indent=2))

    print(f"TIME: {elapsed_ms} ms")

with open("batch_test_results2.json", "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print("\nDone. Results saved to batch_test_results2.json")