import json
import os

import pandas as pd
import numpy as np
import torch
from datasets import Dataset, DatasetDict
from transformers import (
    XLMRobertaTokenizer,
    XLMRobertaForSequenceClassification,
    TrainingArguments,
    Trainer,
    DataCollatorWithPadding,
)
from sklearn.metrics import accuracy_score, precision_recall_fscore_support


def fit_temperature(logits, labels):
    labels_tensor = torch.tensor(labels, dtype=torch.long)
    logits_tensor = torch.tensor(logits, dtype=torch.float32)

    best_temp = 1.0
    best_loss = float("inf")

    for temperature in np.arange(0.05, 3.01, 0.01):
        scaled_logits = logits_tensor / float(temperature)
        loss = torch.nn.functional.cross_entropy(scaled_logits, labels_tensor).item()
        if loss < best_loss:
            best_loss = loss
            best_temp = float(round(temperature, 2))

    return best_temp, best_loss

# 1) Read Excel
file_path = "Dataset/Optimized-Expanded-Dataset.xlsx"
df = pd.read_excel(file_path, sheet_name="Core_Train_Ready")

# 2) Basic cleaning
df = df.dropna(subset=["text", "intent", "lang", "split"]).copy()
df["lang"] = df["lang"].str.upper().str.strip()
df["text"] = df["text"].astype(str).str.strip()

# 3) Build model input
df["model_input"] = "[" + df["lang"] + "] " + df["text"]

# 4) Label mapping
intent_list = sorted(df["intent"].unique().tolist())
label2id = {label: idx for idx, label in enumerate(intent_list)}
id2label = {idx: label for label, idx in label2id.items()}
df["label"] = df["intent"].map(label2id)

# 5) Split
train_df = df[df["split"].str.lower() == "train"][["model_input", "label"]]
val_df   = df[df["split"].str.lower() == "val"][["model_input", "label"]]
test_df  = df[df["split"].str.lower() == "test"][["model_input", "label"]]

dataset = DatasetDict({
    "train": Dataset.from_pandas(train_df.reset_index(drop=True)),
    "validation": Dataset.from_pandas(val_df.reset_index(drop=True)),
    "test": Dataset.from_pandas(test_df.reset_index(drop=True)),
})

# 6) Tokenizer
model_name = "FacebookAI/xlm-roberta-base"
tokenizer = XLMRobertaTokenizer.from_pretrained(model_name)

def tokenize_fn(batch):
    return tokenizer(
        batch["model_input"],
        truncation=True,
        max_length=64
    )

tokenized_dataset = dataset.map(tokenize_fn, batched=True)

# 7) Model
model = XLMRobertaForSequenceClassification.from_pretrained(
    model_name,
    num_labels=len(intent_list),
    id2label=id2label,
    label2id=label2id
)

data_collator = DataCollatorWithPadding(tokenizer=tokenizer)

# 8) Metrics
def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=1)

    precision, recall, f1, _ = precision_recall_fscore_support(
        labels, preds, average="macro", zero_division=0
    )
    acc = accuracy_score(labels, preds)

    return {
        "accuracy": acc,
        "macro_precision": precision,
        "macro_recall": recall,
        "macro_f1": f1,
    }

# 9) Training args
training_args = TrainingArguments(
    output_dir="./xlmr-intent-model",
    eval_strategy="epoch",
    save_strategy="epoch",
    logging_strategy="epoch",
    learning_rate=2e-5,
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    num_train_epochs=5,
    weight_decay=0.01,
    load_best_model_at_end=True,
    metric_for_best_model="macro_f1",
    greater_is_better=True,
    report_to="none",
)

# 10) Trainer
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=tokenized_dataset["train"],
    eval_dataset=tokenized_dataset["validation"],
    tokenizer=tokenizer,
    data_collator=data_collator,
    compute_metrics=compute_metrics,
)

trainer.train()

# 11) Final evaluation
test_metrics = trainer.evaluate(tokenized_dataset["test"])
print(test_metrics)

# 12) Calibrate confidence on validation logits
val_predictions = trainer.predict(tokenized_dataset["validation"])
best_temperature, calibration_loss = fit_temperature(
    val_predictions.predictions,
    val_predictions.label_ids,
)

# 13) Save
model_output_dir = "./xlmr-intent-model-best"
trainer.save_model(model_output_dir)
tokenizer.save_pretrained(model_output_dir)

with open(os.path.join(model_output_dir, "calibration.json"), "w", encoding="utf-8") as calibration_file:
    json.dump(
        {
            "temperature": best_temperature,
            "validation_cross_entropy": round(calibration_loss, 6),
        },
        calibration_file,
        ensure_ascii=False,
        indent=2,
    )
