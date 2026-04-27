from transformers import AutoTokenizer, MT5ForConditionalGeneration
import torch

MODEL_DIR = "result_model"   # backend ile aynı klasör

device = "cuda" if torch.cuda.is_available() else "cpu"

tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, use_fast=False)
model = MT5ForConditionalGeneration.from_pretrained(MODEL_DIR).to(device)
model.eval()

text = "[EN] set a timer for 5 seconds"

inputs = tokenizer(text, return_tensors="pt", truncation=True, max_length=64).to(device)

with torch.no_grad():
    outputs = model.generate(
        **inputs,
        max_length=128,
        num_beams=4,
        do_sample=False,
        early_stopping=True
    )

decoded = tokenizer.decode(outputs[0], skip_special_tokens=False)
print(decoded)