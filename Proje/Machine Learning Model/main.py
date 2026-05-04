import argparse
import json
import os
import random
from pathlib import Path
from typing import Dict, Any, Optional

import numpy as np
import pandas as pd
import torch
from datasets import Dataset, DatasetDict
from sklearn.metrics import accuracy_score, f1_score, classification_report
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
    DataCollatorWithPadding,
    EarlyStoppingCallback,
)


def parse_args():
    p = argparse.ArgumentParser()

    p.add_argument("--data", default="../final_ready_simplified_categorical_target.xlsx")
    p.add_argument("--sheet", default="Joint_NLU_Ready")
    p.add_argument("--output", default="./result_model")
    p.add_argument("--base_model", default="FacebookAI/xlm-roberta-base")

    # Daha iyi defaultlar
    p.add_argument("--epochs", type=int, default=12)
    p.add_argument("--batch_size", type=int, default=8)
    p.add_argument("--lr", type=float, default=3e-5)
    p.add_argument("--max_length", type=int, default=96)

    p.add_argument("--warmup_ratio", type=float, default=0.06)
    p.add_argument("--weight_decay", type=float, default=0.01)
    p.add_argument("--seed", type=int, default=42)

    # Güvenli default: fp16 kapalı
    p.add_argument("--fp16", action="store_true")

    return p.parse_args()


def set_seed(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def normalize_param(value) -> str:
    if pd.isna(value):
        return "none"

    value = str(value).strip().lower()

    if value in ["", "nan", "none", "null", "{}"]:
        return "none"

    return value


def make_label_key(row) -> str:
    intent = str(row["intent"]).strip().upper()
    param = normalize_param(row["parameters"])
    return f"{intent}__{param}"


def label_to_target_json(label_key: str) -> Dict[str, Any]:
    intent, param = label_key.split("__", 1)

    if param == "none":
        return {"intent": intent, "parameters": {}}

    if intent in ["SCROLL_SCREEN", "SWIPE_GESTURE"]:
        return {"intent": intent, "parameters": {"direction": param}}

    if intent == "ADJUST_VOLUME":
        return {"intent": intent, "parameters": {"volume_action": param}}

    return {"intent": intent, "parameters": {}}


def load_data(data_path: str, sheet: str):
    df = pd.read_excel(data_path, sheet_name=sheet)

    required = {"text", "lang", "split", "intent", "parameters", "target_json"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"Missing columns: {missing}")

    df = df.copy()

    df["text"] = df["text"].astype(str).str.strip()
    df["lang"] = df["lang"].astype(str).str.upper().str.strip()
    df["split"] = df["split"].astype(str).str.lower().str.strip()
    df["intent"] = df["intent"].astype(str).str.upper().str.strip()
    df["parameters"] = df["parameters"].apply(normalize_param)

    df = df[
        (df["text"] != "")
        & (df["lang"] != "")
        & (df["intent"] != "")
        & (df["split"].isin(["train", "val", "test"]))
    ].reset_index(drop=True)

    if df.empty:
        raise ValueError("Dataset is empty after cleaning.")

    df["source_text"] = "[" + df["lang"] + "] " + df["text"]
    df["label_key"] = df.apply(make_label_key, axis=1)

    labels = sorted(df["label_key"].unique().tolist())
    label2id = {label: idx for idx, label in enumerate(labels)}
    id2label = {idx: label for label, idx in label2id.items()}

    df["label"] = df["label_key"].map(label2id)

    print("\nDataset loaded")
    print("Rows:", len(df))
    print("Labels:", len(labels))
    print("\nSplit counts:")
    print(df["split"].value_counts())
    print("\nLabel counts:")
    print(df["label_key"].value_counts())

    return df, label2id, id2label, labels


def save_label_files(label2id, id2label, labels, output_dir):
    os.makedirs(output_dir, exist_ok=True)

    with open(os.path.join(output_dir, "label2id.json"), "w", encoding="utf-8") as f:
        json.dump(label2id, f, ensure_ascii=False, indent=2)

    with open(os.path.join(output_dir, "id2label.json"), "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in id2label.items()}, f, ensure_ascii=False, indent=2)

    label_to_json = {
        label: label_to_target_json(label)
        for label in labels
    }

    with open(os.path.join(output_dir, "label_to_target_json.json"), "w", encoding="utf-8") as f:
        json.dump(label_to_json, f, ensure_ascii=False, indent=2)


def build_dataset_dict(df: pd.DataFrame) -> DatasetDict:
    result = {}

    for split in ["train", "val", "test"]:
        split_df = df[df["split"] == split].reset_index(drop=True)

        if split_df.empty:
            raise ValueError(f"{split} split is empty.")

        result[split] = Dataset.from_dict({
            "source_text": split_df["source_text"].tolist(),
            "labels": split_df["label"].tolist(),
        })

    return DatasetDict(result)


def tokenize_dataset(dataset: DatasetDict, tokenizer, max_length: int):
    def tokenize_fn(batch):
        return tokenizer(
            batch["source_text"],
            truncation=True,
            max_length=max_length,
        )

    return dataset.map(
        tokenize_fn,
        batched=True,
        remove_columns=["source_text"],
    )


def build_class_weights(df: pd.DataFrame, num_labels: int) -> torch.Tensor:
    train_labels = df[df["split"] == "train"]["label"]
    counts = train_labels.value_counts().sort_index()

    total = counts.sum()
    weights = []

    for label_id in range(num_labels):
        count = counts.get(label_id, 1)
        w = total / (num_labels * count)
        weights.append(np.sqrt(w))

    return torch.tensor(weights, dtype=torch.float)


class WeightedTrainer(Trainer):
    def __init__(self, class_weights: Optional[torch.Tensor] = None, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.class_weights = class_weights

    def compute_loss(self, model, inputs, return_outputs=False, **kwargs):
        labels = inputs.pop("labels")
        outputs = model(**inputs)
        logits = outputs.logits

        if self.class_weights is not None:
            loss_fn = torch.nn.CrossEntropyLoss(
                weight=self.class_weights.to(logits.device)
            )
        else:
            loss_fn = torch.nn.CrossEntropyLoss()

        loss = loss_fn(logits, labels)

        return (loss, outputs) if return_outputs else loss


def build_compute_metrics(id2label):
    def compute_metrics(eval_pred):
        logits, labels = eval_pred
        preds = np.argmax(logits, axis=-1)

        acc = accuracy_score(labels, preds)
        macro_f1 = f1_score(labels, preds, average="macro", zero_division=0)
        weighted_f1 = f1_score(labels, preds, average="weighted", zero_division=0)

        gold_intents = [id2label[int(i)].split("__", 1)[0] for i in labels]
        pred_intents = [id2label[int(i)].split("__", 1)[0] for i in preds]

        intent_acc = accuracy_score(gold_intents, pred_intents)

        return {
            "accuracy": float(acc),
            "macro_f1": float(macro_f1),
            "weighted_f1": float(weighted_f1),
            "intent_accuracy": float(intent_acc),
        }

    return compute_metrics


def save_predictions_csv(trainer, tokenized_split, raw_df, id2label, output_dir, split_name: str):
    output = trainer.predict(tokenized_split)

    logits = output.predictions
    probs = torch.softmax(torch.tensor(logits), dim=-1).numpy()

    pred_ids = np.argmax(probs, axis=-1)
    confs = probs.max(axis=-1)

    split_df = raw_df[raw_df["split"] == split_name].reset_index(drop=True)

    gold_labels = split_df["label_key"].tolist()
    pred_labels = [id2label[int(i)] for i in pred_ids]

    result_df = pd.DataFrame({
        "source_text": split_df["source_text"].tolist(),
        "gold_label": gold_labels,
        "pred_label": pred_labels,
        "confidence": confs.round(4),
        "correct": [g == p for g, p in zip(gold_labels, pred_labels)],
    })

    path = os.path.join(output_dir, f"{split_name}_predictions.csv")
    result_df.to_csv(path, index=False, encoding="utf-8-sig")

    return result_df


def train(args):
    set_seed(args.seed)

    df, label2id, id2label, labels = load_data(args.data, args.sheet)

    os.makedirs(args.output, exist_ok=True)
    save_label_files(label2id, id2label, labels, args.output)

    print(f"\nLoading base model: {args.base_model}")

    tokenizer = AutoTokenizer.from_pretrained(args.base_model, use_fast=False)

    model = AutoModelForSequenceClassification.from_pretrained(
        args.base_model,
        num_labels=len(labels),
        id2label=id2label,
        label2id=label2id,
    )

    raw_dataset = build_dataset_dict(df)
    tokenized = tokenize_dataset(raw_dataset, tokenizer, args.max_length)

    tokenizer.save_pretrained(args.output)

    data_collator = DataCollatorWithPadding(tokenizer=tokenizer)

    class_weights = build_class_weights(df, len(labels))

    print("\nClass weights:")
    for idx, weight in enumerate(class_weights.tolist()):
        print(f"{id2label[idx]}: {weight:.4f}")

    training_args = TrainingArguments(
        output_dir=args.output,
        overwrite_output_dir=True,

        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,

        learning_rate=args.lr,
        warmup_ratio=args.warmup_ratio,
        weight_decay=args.weight_decay,

        eval_strategy="epoch",
        save_strategy="epoch",
        logging_strategy="epoch",

        load_best_model_at_end=True,
        metric_for_best_model="macro_f1",
        greater_is_better=True,

        save_total_limit=2,
        seed=args.seed,
        report_to="none",

        fp16=args.fp16,
        max_grad_norm=1.0,
    )

    trainer = WeightedTrainer(
        model=model,
        args=training_args,
        train_dataset=tokenized["train"],
        eval_dataset=tokenized["val"],
        tokenizer=tokenizer,
        data_collator=data_collator,
        compute_metrics=build_compute_metrics(id2label),
        callbacks=[EarlyStoppingCallback(early_stopping_patience=4)],
        class_weights=class_weights,
    )

    print("\nTraining started")
    trainer.train()

    print("\nValidation evaluation")
    val_metrics = trainer.evaluate(tokenized["val"], metric_key_prefix="val")
    print(val_metrics)

    print("\nTest evaluation")
    test_metrics = trainer.evaluate(tokenized["test"], metric_key_prefix="test")
    print(test_metrics)

    print("\nSaving final model")
    trainer.save_model(args.output)
    tokenizer.save_pretrained(args.output)

    train_pred = save_predictions_csv(
        trainer, tokenized["train"], df, id2label, args.output, "train"
    )
    val_pred = save_predictions_csv(
        trainer, tokenized["val"], df, id2label, args.output, "val"
    )
    test_pred = save_predictions_csv(
        trainer, tokenized["test"], df, id2label, args.output, "test"
    )

    print("\nTest classification report:")
    print(classification_report(
        test_pred["gold_label"],
        test_pred["pred_label"],
        zero_division=0
    ))

    metrics_summary = {
        "base_model": args.base_model,
        "num_labels": len(labels),
        "epochs": args.epochs,
        "batch_size": args.batch_size,
        "lr": args.lr,
        "max_length": args.max_length,
        "fp16": args.fp16,
        "val_metrics": val_metrics,
        "test_metrics": test_metrics,
        "train_accuracy": float((train_pred["gold_label"] == train_pred["pred_label"]).mean()),
        "val_accuracy": float((val_pred["gold_label"] == val_pred["pred_label"]).mean()),
        "test_accuracy": float((test_pred["gold_label"] == test_pred["pred_label"]).mean()),
    }

    with open(os.path.join(args.output, "metrics_summary.json"), "w", encoding="utf-8") as f:
        json.dump(metrics_summary, f, ensure_ascii=False, indent=2)

    # Critical sanity test
    sanity_examples = [
        ("[TR] alt tarafa in", "SCROLL_SCREEN__down"),
        ("[TR] aşağı kaydır", "SCROLL_SCREEN__down"),
        ("[EN] Swipe left", "SWIPE_GESTURE__left"),
        ("[EN] Open whatsapp", "OPEN_APP__none"),
        ("[EN] set a timer for 11 minutes", "SET_TIMER__none"),
    ]

    print("\nSanity checks:")
    model.eval()
    device = model.device

    for source_text, expected_label in sanity_examples:
        inputs = tokenizer(
            source_text,
            return_tensors="pt",
            truncation=True,
            max_length=args.max_length,
        ).to(device)

        with torch.no_grad():
            logits = model(**inputs).logits
            probs = torch.softmax(logits, dim=-1)[0]

        pred_id = int(torch.argmax(probs).item())
        pred_label = id2label[pred_id]
        confidence = float(probs[pred_id].item())

        print(f"{source_text}")
        print(f"  expected: {expected_label}")
        print(f"  predicted: {pred_label}")
        print(f"  confidence: {confidence:.4f}")

    print(f"\nDone. Model saved to: {args.output}")


if __name__ == "__main__":
    args = parse_args()
    train(args)