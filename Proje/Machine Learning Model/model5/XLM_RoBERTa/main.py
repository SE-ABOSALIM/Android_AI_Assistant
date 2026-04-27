"""
train.py — XLM-RoBERTa NLU Intent + Parameter Classifier
==========================================================
Architecture : FacebookAI/xlm-roberta-base + sequence classification head
Task         : Predict label_key = intent__parameter (17 classes)
Languages    : EN, TR, AR (multilingual)

Usage:
    python train.py --data final_ready_simplified_categorical_target.xlsx \
                    --output ./nlu_model \
                    --epochs 8 \
                    --batch_size 32 \
                    --lr 2e-5
"""

import argparse
import json
import os
import numpy as np
import pandas as pd
from pathlib import Path

import torch
from datasets import Dataset, DatasetDict
from sklearn.metrics import (
    accuracy_score, f1_score, classification_report, confusion_matrix
)
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
    EarlyStoppingCallback,
)

# ─────────────────────────────────────────────────────────────
# 1. ARGUMENT PARSING
# ─────────────────────────────────────────────────────────────
def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--data",       default="../final_ready_simplified_categorical_target.xlsx")
    p.add_argument("--sheet",      default="Joint_NLU_Ready")
    p.add_argument("--output",     default="./nlu_model")
    p.add_argument("--base_model", default="FacebookAI/xlm-roberta-base")
    p.add_argument("--epochs",     type=int,   default=8)
    p.add_argument("--batch_size", type=int,   default=32)
    p.add_argument("--lr",         type=float, default=2e-5)
    p.add_argument("--max_length", type=int,   default=64)
    p.add_argument("--warmup_ratio", type=float, default=0.1)
    p.add_argument("--weight_decay", type=float, default=0.01)
    p.add_argument("--seed",       type=int,   default=42)
    return p.parse_args()


# ─────────────────────────────────────────────────────────────
# 2. DATA LOADING & LABEL CONSTRUCTION
# ─────────────────────────────────────────────────────────────
def make_label_key(row):
    """Combine intent and categorical parameter into a single label."""
    p = row["parameters"]
    if pd.isna(p) or str(p).strip() == "":
        return row["intent"] + "__none"
    return row["intent"] + "__" + str(p).strip().lower()


def load_data(data_path: str, sheet: str):
    df = pd.read_excel(data_path, sheet_name=sheet)
    required = {"text", "lang", "split", "intent", "parameters"}
    assert required.issubset(df.columns), f"Missing columns: {required - set(df.columns)}"

    # Build source_text = "[LANG] text"
    df["source_text"] = "[" + df["lang"].str.upper() + "] " + df["text"].astype(str)

    # Build label_key
    df["label_key"] = df.apply(make_label_key, axis=1)

    # Encode labels
    labels = sorted(df["label_key"].unique())
    label2id = {l: i for i, l in enumerate(labels)}
    id2label = {i: l for l, i in label2id.items()}
    df["label"] = df["label_key"].map(label2id)

    print(f"\n✓ Loaded {len(df)} rows, {len(labels)} labels")
    print(f"  Labels: {labels}")
    print(f"  Split distribution:")
    print(df.groupby("split")["label_key"].count().to_string())

    return df, label2id, id2label, labels


def save_label_files(label2id, id2label, labels, output_dir):
    os.makedirs(output_dir, exist_ok=True)

    with open(f"{output_dir}/label2id.json", "w") as f:
        json.dump(label2id, f, indent=2)
    with open(f"{output_dir}/id2label.json", "w") as f:
        json.dump(id2label, f, indent=2)

    # Build label_to_target_json mapping
    PARAM_FIELD_MAP = {
        "SCROLL_SCREEN": ("direction",    ["up", "down"]),
        "SWIPE_GESTURE": ("direction",    ["left", "right"]),
        "ADJUST_VOLUME": ("volume_action", ["increase", "decrease", "mute", "unmute"]),
    }

    label_to_target_json = {}
    for label_key in labels:
        intent, param = label_key.split("__", 1)
        parameters = {}
        if intent in PARAM_FIELD_MAP and param != "none":
            field, valid = PARAM_FIELD_MAP[intent]
            if param in valid:
                parameters[field] = param
        label_to_target_json[label_key] = {"intent": intent, "parameters": parameters}

    with open(f"{output_dir}/label_to_target_json.json", "w", encoding="utf-8") as f:
        json.dump(label_to_target_json, f, indent=2, ensure_ascii=False)

    print(f"✓ Saved label2id.json, id2label.json, label_to_target_json.json → {output_dir}/")
    return label_to_target_json


# ─────────────────────────────────────────────────────────────
# 3. TOKENIZATION
# ─────────────────────────────────────────────────────────────
def tokenize_dataset(df, tokenizer, max_length):
    splits = {}
    for split_name in ["train", "val", "test"]:
        split_df = df[df["split"] == split_name].reset_index(drop=True)
        ds = Dataset.from_dict({
            "source_text": split_df["source_text"].tolist(),
            "label":       split_df["label"].tolist(),
            "label_key":   split_df["label_key"].tolist(),
        })
        splits[split_name] = ds

    dataset = DatasetDict(splits)

    def tokenize_fn(batch):
        return tokenizer(
            batch["source_text"],
            truncation=True,
            padding="max_length",
            max_length=max_length,
        )

    tokenized = dataset.map(tokenize_fn, batched=True)
    return tokenized


# ─────────────────────────────────────────────────────────────
# 4. METRICS
# ─────────────────────────────────────────────────────────────
def build_compute_metrics(id2label):
    def compute_metrics(eval_pred):
        logits, labels = eval_pred
        preds = np.argmax(logits, axis=-1)

        acc     = accuracy_score(labels, preds)
        macro   = f1_score(labels, preds, average="macro",    zero_division=0)
        weighted = f1_score(labels, preds, average="weighted", zero_division=0)

        # Intent-level accuracy (strip parameter suffix)
        gold_intents = [id2label[l].split("__")[0] for l in labels]
        pred_intents = [id2label[p].split("__")[0] for p in preds]
        intent_acc  = accuracy_score(gold_intents, pred_intents)

        return {
            "accuracy":       acc,
            "macro_f1":       macro,
            "weighted_f1":    weighted,
            "intent_accuracy": intent_acc,
        }
    return compute_metrics


# ─────────────────────────────────────────────────────────────
# 5. TRAINING
# ─────────────────────────────────────────────────────────────
def train(args):
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    # Load data
    df, label2id, id2label, labels = load_data(args.data, args.sheet)
    os.makedirs(args.output, exist_ok=True)
    label_to_target_json = save_label_files(label2id, id2label, labels, args.output)

    # Load tokenizer and model
    print(f"\n✓ Loading tokenizer & model: {args.base_model}")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.base_model,
        num_labels=len(labels),
        id2label=id2label,
        label2id=label2id,
    )

    # Tokenize
    print("✓ Tokenizing dataset …")
    tokenized = tokenize_dataset(df, tokenizer, args.max_length)
    tokenizer.save_pretrained(args.output)

    # Training arguments
    training_args = TrainingArguments(
        output_dir=args.output,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        learning_rate=args.lr,
        warmup_ratio=args.warmup_ratio,
        weight_decay=args.weight_decay,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="macro_f1",
        greater_is_better=True,
        logging_steps=20,
        seed=args.seed,
        fp16=torch.cuda.is_available(),
        report_to="none",
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized["train"],
        eval_dataset=tokenized["val"],
        tokenizer=tokenizer,
        compute_metrics=build_compute_metrics(id2label),
        callbacks=[EarlyStoppingCallback(early_stopping_patience=3)],
    )

    print("\n─── Training ────────────────────────────────────────")
    trainer.train()

    # ── Test evaluation ──────────────────────────────────────
    print("\n─── Test Evaluation ─────────────────────────────────")
    test_output = trainer.predict(tokenized["test"])
    logits = test_output.predictions
    probs  = torch.softmax(torch.tensor(logits), dim=-1).numpy()
    preds  = np.argmax(probs, axis=-1)
    confs  = probs.max(axis=-1)

    test_df = df[df["split"] == "test"].reset_index(drop=True)
    gold_labels = test_df["label_key"].tolist()
    pred_labels = [id2label[p] for p in preds]

    # Metrics
    acc      = accuracy_score(gold_labels, pred_labels)
    macro_f1 = f1_score(gold_labels, pred_labels, average="macro",    zero_division=0)
    wt_f1    = f1_score(gold_labels, pred_labels, average="weighted", zero_division=0)
    intent_acc = accuracy_score(
        [l.split("__")[0] for l in gold_labels],
        [l.split("__")[0] for l in pred_labels],
    )

    print(f"\n  Test Accuracy   : {acc:.4f}")
    print(f"  Macro F1        : {macro_f1:.4f}")
    print(f"  Weighted F1     : {wt_f1:.4f}")
    print(f"  Intent Accuracy : {intent_acc:.4f}")

    print("\n  Per-class Report:")
    print(classification_report(gold_labels, pred_labels, zero_division=0))

    # Confusion pairs of interest
    focus_pairs = [
        ("ADJUST_VOLUME__increase", "ADJUST_VOLUME__decrease"),
        ("SCROLL_SCREEN__up",       "SCROLL_SCREEN__down"),
        ("SWIPE_GESTURE__left",     "SWIPE_GESTURE__right"),
        ("UNKNOWN_COMMAND__none",   None),  # vs all real commands
    ]
    print("  Focus Confusion Pairs:")
    for a, b in focus_pairs:
        if b:
            a_as_b = sum(1 for g, p in zip(gold_labels, pred_labels) if g == a and p == b)
            b_as_a = sum(1 for g, p in zip(gold_labels, pred_labels) if g == b and p == a)
            print(f"    {a} → predicted as {b}: {a_as_b}")
            print(f"    {b} → predicted as {a}: {b_as_a}")
        else:
            # UNKNOWN_COMMAND predicted as real command
            unk_as_real = sum(
                1 for g, p in zip(gold_labels, pred_labels)
                if g == a and p != a
            )
            real_as_unk = sum(
                1 for g, p in zip(gold_labels, pred_labels)
                if g != a and p == a
            )
            print(f"    UNKNOWN → predicted as real command: {unk_as_real}")
            print(f"    Real command → predicted as UNKNOWN: {real_as_unk}")

    # Save predictions CSV
    result_df = pd.DataFrame({
        "source_text": test_df["source_text"].tolist(),
        "gold_label":  gold_labels,
        "pred_label":  pred_labels,
        "confidence":  confs.round(4),
        "correct":     ["correct" if g == p else "incorrect"
                        for g, p in zip(gold_labels, pred_labels)],
    })
    csv_path = os.path.join(args.output, "test_predictions.csv")
    result_df.to_csv(csv_path, index=False)
    print(f"\n✓ Saved test_predictions.csv → {csv_path}")

    # Save final model
    trainer.save_model(args.output)
    print(f"✓ Model saved → {args.output}/")

    # Save metrics summary
    metrics_summary = {
        "test_accuracy":    round(acc, 4),
        "test_macro_f1":    round(macro_f1, 4),
        "test_weighted_f1": round(wt_f1, 4),
        "test_intent_accuracy": round(intent_acc, 4),
        "num_labels": len(labels),
        "base_model": args.base_model,
    }
    with open(f"{args.output}/metrics_summary.json", "w") as f:
        json.dump(metrics_summary, f, indent=2)
    print(f"✓ Saved metrics_summary.json → {args.output}/")


if __name__ == "__main__":
    args = parse_args()
    train(args)