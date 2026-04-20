import json
import os
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from datasets import Dataset, DatasetDict
from transformers import (
    AutoTokenizer,
    MT5ForConditionalGeneration,
    DataCollatorForSeq2Seq,
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
)

# =========================
# Paths
# =========================
try:
    BASE_DIR = Path(__file__).resolve().parent
    os.chdir(BASE_DIR)
except NameError:
    BASE_DIR = Path.cwd()

DATASET_PATH = BASE_DIR / "../Optimized-Expanded-Dataset-joint-nlu2.xlsx"
SHEET_NAME = "Joint_NLU_Ready"

MODEL_NAME = "google/mt5-small"
MODEL_OUTPUT_DIR = BASE_DIR / "./result_model"
TRAINING_OUTPUT_DIR = BASE_DIR / "./mt5-joint-nlu"

MAX_SOURCE_LEN = 96
MAX_TARGET_LEN = 160

# =========================
# GPU check
# =========================
print("CUDA available:", torch.cuda.is_available())
if torch.cuda.is_available():
    print("GPU:", torch.cuda.get_device_name(0))
else:
    print("Running on CPU")

# =========================
# 1) Read dataset
# =========================
if not DATASET_PATH.exists():
    raise FileNotFoundError(f"Dataset not found: {DATASET_PATH}")

df = pd.read_excel(DATASET_PATH, sheet_name=SHEET_NAME)

required_cols = ["text", "lang", "split", "target_json"]
missing_cols = [col for col in required_cols if col not in df.columns]
if missing_cols:
    raise ValueError(f"Missing required columns: {missing_cols}")

# =========================
# 2) Basic cleaning
# =========================
df = df.dropna(subset=required_cols).copy()
df["lang"] = df["lang"].astype(str).str.upper().str.strip()
df["text"] = df["text"].astype(str).str.strip()
df["target_json"] = df["target_json"].astype(str).str.strip()
df["split"] = df["split"].astype(str).str.lower().str.strip()

# Build source text
df["source_text"] = "[" + df["lang"] + "] " + df["text"]

# Only allowed splits
df = df[df["split"].isin(["train", "val", "test"])].copy()

if df.empty:
    raise ValueError("Dataset is empty after split filtering.")

train_df = df[df["split"] == "train"][["source_text", "target_json"]].reset_index(drop=True)
val_df   = df[df["split"] == "val"][["source_text", "target_json"]].reset_index(drop=True)
test_df  = df[df["split"] == "test"][["source_text", "target_json"]].reset_index(drop=True)

if train_df.empty:
    raise ValueError("Train split is empty.")
if val_df.empty:
    raise ValueError("Validation split is empty.")
if test_df.empty:
    raise ValueError("Test split is empty.")

dataset = DatasetDict({
    "train": Dataset.from_pandas(train_df),
    "validation": Dataset.from_pandas(val_df),
    "test": Dataset.from_pandas(test_df),
})

# =========================
# 3) Tokenizer + model
# =========================
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = MT5ForConditionalGeneration.from_pretrained(MODEL_NAME)

def preprocess_function(batch):
    model_inputs = tokenizer(
        batch["source_text"],
        max_length=MAX_SOURCE_LEN,
        truncation=True,
    )

    labels = tokenizer(
        text_target=batch["target_json"],
        max_length=MAX_TARGET_LEN,
        truncation=True,
    )

    model_inputs["labels"] = labels["input_ids"]
    return model_inputs

tokenized_dataset = dataset.map(
    preprocess_function,
    batched=True,
    remove_columns=dataset["train"].column_names
)

data_collator = DataCollatorForSeq2Seq(
    tokenizer=tokenizer,
    model=model
)

# =========================
# 4) Metric
# =========================
def canonicalize_json_string(text: str) -> str:
    text = " ".join(text.strip().split())
    try:
        parsed = json.loads(text)
        return json.dumps(parsed, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except Exception:
        return text

def compute_metrics(eval_pred):
    predictions, labels = eval_pred

    if isinstance(predictions, tuple):
        predictions = predictions[0]

    predictions = np.asarray(predictions)
    labels = np.asarray(labels)

    if predictions.ndim == 3:
        predictions = np.argmax(predictions, axis=-1)

    predictions = np.where(predictions < 0, tokenizer.pad_token_id, predictions)
    labels = np.where(labels < 0, tokenizer.pad_token_id, labels)

    decoded_preds = tokenizer.batch_decode(predictions, skip_special_tokens=True)
    decoded_labels = tokenizer.batch_decode(labels, skip_special_tokens=True)

    decoded_preds = [canonicalize_json_string(p) for p in decoded_preds]
    decoded_labels = [canonicalize_json_string(l) for l in decoded_labels]

    exact_match = np.mean([int(p == l) for p, l in zip(decoded_preds, decoded_labels)])

    return {"exact_match": float(exact_match)}

# =========================
# 5) Training args
# =========================
training_args = Seq2SeqTrainingArguments(
    output_dir=str(TRAINING_OUTPUT_DIR),
    eval_strategy="epoch",
    save_strategy="epoch",
    logging_strategy="epoch",
    learning_rate=3e-4,
    per_device_train_batch_size=8,
    per_device_eval_batch_size=8,
    num_train_epochs=8,
    weight_decay=0.01,
    predict_with_generate=True,
    generation_max_length=MAX_TARGET_LEN,
    load_best_model_at_end=True,
    metric_for_best_model="exact_match",
    greater_is_better=True,
    save_total_limit=2,
    report_to="none",
    fp16=False,
)

# =========================
# 6) Trainer
# =========================
trainer = Seq2SeqTrainer(
    model=model,
    args=training_args,
    train_dataset=tokenized_dataset["train"],
    eval_dataset=tokenized_dataset["validation"],
    tokenizer=tokenizer,
    data_collator=data_collator,
    compute_metrics=compute_metrics,
)

# =========================
# 7) Train
# =========================
trainer.train()

# =========================
# 8) Final evaluation
# =========================
test_metrics = trainer.evaluate(tokenized_dataset["test"], metric_key_prefix="test")
print("Test metrics:", test_metrics)

# =========================
# 9) Save model
# =========================
MODEL_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

trainer.save_model(str(MODEL_OUTPUT_DIR))
tokenizer.save_pretrained(str(MODEL_OUTPUT_DIR))

# =========================
# 10) Save metadata
# =========================
metadata = {
    "base_model": MODEL_NAME,
    "sheet_name": SHEET_NAME,
    "max_source_len": MAX_SOURCE_LEN,
    "max_target_len": MAX_TARGET_LEN,
    "cuda_available": torch.cuda.is_available(),
    "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
}

with open(MODEL_OUTPUT_DIR / "model_meta.json", "w", encoding="utf-8") as f:
    json.dump(metadata, f, ensure_ascii=False, indent=2)