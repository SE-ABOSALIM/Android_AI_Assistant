import json
import os
import sys

import torch
from transformers import XLMRobertaTokenizer, XLMRobertaForSequenceClassification

model_path = "./xlmr-intent-model-best"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

tokenizer = XLMRobertaTokenizer.from_pretrained(model_path)
model = XLMRobertaForSequenceClassification.from_pretrained(model_path)

model.eval()

id2label = model.config.id2label
calibration_path = os.path.join(model_path, "calibration.json")

temperature = 1.0
if os.path.exists(calibration_path):
    with open(calibration_path, "r", encoding="utf-8") as calibration_file:
        temperature = float(json.load(calibration_file).get("temperature", 1.0))

def predict_intent(text, language):
    model_input = f"[{language}] {text}"

    inputs = tokenizer(
        model_input,
        return_tensors="pt",
        truncation=True,
        max_length=64
    )

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits / temperature, dim=1)
        pred_id = torch.argmax(probs, dim=1).item()
        confidence = probs[0][pred_id].item()

    return {
        "input": model_input,
        "predicted_label": id2label[pred_id],
        "confidence": round(confidence, 4),
        "temperature": temperature,
    }

# Örnek testler
tests = [
    # ===== OPEN_APP =====
    ("WhatsApp aç", "TR"),
    ("YouTube'u başlat", "TR"),
    ("Instagram uygulamasını aç", "TR"),
    ("open WhatsApp", "EN"),
    ("launch YouTube app", "EN"),
    ("start Instagram", "EN"),
    ("افتح واتساب", "AR"),
    ("شغل يوتيوب", "AR"),
    ("افتح تطبيق انستغرام", "AR"),

    # ===== GO_HOME =====
    ("ana sayfaya dön", "TR"),
    ("ana ekrana git", "TR"),
    ("başlangıç ekranına dön", "TR"),
    ("go home", "EN"),
    ("go to home screen", "EN"),
    ("take me to homepage", "EN"),
    ("اذهب إلى الشاشة الرئيسية", "AR"),
    ("ارجع للرئيسية", "AR"),
    ("الصفحة الرئيسية", "AR"),

    # ===== GO_BACK =====
    ("geri git", "TR"),
    ("önceki sayfaya dön", "TR"),
    ("geri dön", "TR"),
    ("go back", "EN"),
    ("go to previous page", "EN"),
    ("back", "EN"),
    ("ارجع", "AR"),
    ("ارجع للخلف", "AR"),
    ("رجوع", "AR"),

    # ===== CLICK_ITEM =====
    ("gönder butonuna bas", "TR"),
    ("arama çubuğuna tıkla", "TR"),
    ("tamam butonuna bas", "TR"),
    ("click send button", "EN"),
    ("tap search bar", "EN"),
    ("press ok button", "EN"),
    ("اضغط على زر الإرسال", "AR"),
    ("انقر على شريط البحث", "AR"),
    ("اضغط على زر موافق", "AR"),

    # ===== WRITE_TEXT =====
    ('"merhaba nasılsın" yaz', "TR"),
    ('metin alanına "selam" yaz', "TR"),
    ('şunu yaz: yarın geliyorum', "TR"),
    ('type "hello how are you"', "EN"),
    ('write "see you tomorrow"', "EN"),
    ('enter text: hello world', "EN"),
    ('اكتب "مرحبا كيف حالك"', "AR"),
    ('اكتب في الحقل "السلام عليكم"', "AR"),
    ('أدخل النص مرحبا', "AR"),

    # ===== SCROLL =====
    ("aşağı kaydır", "TR"),
    ("yukarı kaydır", "TR"),
    ("biraz aşağı in", "TR"),
    ("scroll down", "EN"),
    ("scroll up", "EN"),
    ("move a bit down", "EN"),
    ("مرر للأسفل", "AR"),
    ("مرر للأعلى", "AR"),
    ("انزل قليلا", "AR"),

    # ===== VOLUME =====
    ("sesi artır", "TR"),
    ("sesi azalt", "TR"),
    ("ses seviyesini yükselt", "TR"),
    ("increase volume", "EN"),
    ("decrease volume", "EN"),
    ("turn volume up", "EN"),
    ("ارفع الصوت", "AR"),
    ("اخفض الصوت", "AR"),
    ("زيد الصوت", "AR"),

    # ===== MIXED / EDGE CASES =====
    ("aç", "TR"),  # ambiguous
    ("geri", "TR"),  # ambiguous
    ("yaz", "TR"),  # ambiguous
    ("open it", "EN"),  # ambiguous
    ("go there", "EN"),  # ambiguous
    ("type something", "EN"),
    ("افتح", "AR"),  # ambiguous
    ("ارجع", "AR"),
    ("اكتب", "AR"),
]

for text, lang in tests:
    print(predict_intent(text, lang))
