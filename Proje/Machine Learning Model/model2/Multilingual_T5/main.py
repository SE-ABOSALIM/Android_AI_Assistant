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

BASE_DIR = Path(__file__).resolve().parent
os.chdir(BASE_DIR)

DATASET_PATH = "../Optimized-Expanded-Dataset-joint-nlu2.xlsx"
SHEET_NAME = "Joint_NLU_Ready"

MODEL_NAME = "google/mt5-small"
MODEL_OUTPUT_DIR = "./result_model"

MAX_SOURCE_LEN = 96
MAX_TARGET_LEN = 160

# 1) Read dataset
df = pd.read_excel(DATASET_PATH, sheet_name=SHEET_NAME)

# 2) Basic cleaning
df = df.dropna(subset=["text", "lang", "split", "target_json"]).copy()
df["lang"] = df["lang"].astype(str).str.upper().str.strip()
df["text"] = df["text"].astype(str).str.strip()
df["target_json"] = df["target_json"].astype(str).str.strip()
df["split"] = df["split"].astype(str).str.lower().str.strip()

# 3) Build source text
df["source_text"] = "[" + df["lang"] + "] " + df["text"]

# Optional: filter only allowed splits
df = df[df["split"].isin(["train", "val", "test"])].copy()

train_df = df[df["split"] == "train"][["source_text", "target_json"]].reset_index(drop=True)
val_df   = df[df["split"] == "val"][["source_text", "target_json"]].reset_index(drop=True)
test_df  = df[df["split"] == "test"][["source_text", "target_json"]].reset_index(drop=True)

dataset = DatasetDict({
    "train": Dataset.from_pandas(train_df),
    "validation": Dataset.from_pandas(val_df),
    "test": Dataset.from_pandas(test_df),
})

# 4) Tokenizer + model
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

tokenized_dataset = dataset.map(preprocess_function, batched=True)

data_collator = DataCollatorForSeq2Seq(
    tokenizer=tokenizer,
    model=model
)

# 5) Exact-match style metric (strict but useful)
def normalize_json_string(text: str) -> str:
    return " ".join(text.strip().split())

def compute_metrics(eval_pred):
    predictions, labels = eval_pred

    if isinstance(predictions, tuple):
        predictions = predictions[0]

    predictions = np.asarray(predictions)
    labels = np.asarray(labels)

    # Eğer predictions logits gelirse önce argmax al
    if predictions.ndim == 3:
        predictions = np.argmax(predictions, axis=-1)

    predictions = np.where(predictions < 0, tokenizer.pad_token_id, predictions)
    labels = np.where(labels < 0, tokenizer.pad_token_id, labels)

    decoded_preds = tokenizer.batch_decode(predictions, skip_special_tokens=True)
    decoded_labels = tokenizer.batch_decode(labels, skip_special_tokens=True)

    decoded_preds = [" ".join(p.strip().split()) for p in decoded_preds]
    decoded_labels = [" ".join(l.strip().split()) for l in decoded_labels]

    exact_match = np.mean([int(p == l) for p, l in zip(decoded_preds, decoded_labels)])

    return {"exact_match": float(exact_match)}

# 6) Training args
training_args = Seq2SeqTrainingArguments(
    output_dir="./mt5-joint-nlu",
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
    fp16=torch.cuda.is_available(),
)

# 7) Trainer
trainer = Seq2SeqTrainer(
    model=model,
    args=training_args,
    train_dataset=tokenized_dataset["train"],
    eval_dataset=tokenized_dataset["validation"],
    tokenizer=tokenizer,
    data_collator=data_collator,
    compute_metrics=compute_metrics,
)

# 8) Train
trainer.train()

# 9) Final evaluation
test_metrics = trainer.evaluate(tokenized_dataset["test"], metric_key_prefix="test")
print("Test metrics:", test_metrics)

# 10) Save
trainer.save_model(MODEL_OUTPUT_DIR)
tokenizer.save_pretrained(MODEL_OUTPUT_DIR)

# 11) Save metadata
metadata = {
    "base_model": MODEL_NAME,
    "sheet_name": SHEET_NAME,
    "max_source_len": MAX_SOURCE_LEN,
    "max_target_len": MAX_TARGET_LEN,
}
with open(os.path.join(MODEL_OUTPUT_DIR, "model_meta.json"), "w", encoding="utf-8") as f:
    json.dump(metadata, f, ensure_ascii=False, indent=2)