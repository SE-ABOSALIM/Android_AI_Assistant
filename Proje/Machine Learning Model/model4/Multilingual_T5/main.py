import json
import os
from pathlib import Path
from typing import Any, Dict, List

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


try:
    BASE_DIR = Path(__file__).resolve().parent
    os.chdir(BASE_DIR)
except NameError:
    BASE_DIR = Path.cwd()


DATASET_PATH = BASE_DIR / "../Optimized-Expanded-balanced-Dataset_final_ready.xlsx"
SHEET_NAME = "Joint_NLU_Ready"

MODEL_NAME = "google/mt5-small"

MODEL_OUTPUT_DIR = BASE_DIR / "./result_model_final_ready"
TRAINING_OUTPUT_DIR = BASE_DIR / "./mt5-final-ready-training"

MAX_SOURCE_LEN = 96
MAX_TARGET_LEN = 160

SEED = 42


def set_seed(seed: int) -> None:
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed) if torch.cuda.is_available() else None


def compact_json(raw: str) -> str:
    obj = json.loads(str(raw).strip())
    return json.dumps(
        obj,
        ensure_ascii=False,
        sort_keys=False,
        separators=(",", ":")
    )


def canonicalize_json_string(text: str) -> str:
    text = " ".join(str(text).strip().split())
    try:
        parsed = json.loads(text)
        return json.dumps(
            parsed,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":")
        )
    except Exception:
        return text


def is_valid_json(text: str) -> bool:
    try:
        json.loads(str(text).strip())
        return True
    except Exception:
        return False


def extract_json_field(text: str, field: str) -> Any:
    try:
        obj = json.loads(str(text).strip())
        return obj.get(field)
    except Exception:
        return None


def load_dataset() -> pd.DataFrame:
    if not DATASET_PATH.exists():
        raise FileNotFoundError(f"Dataset not found: {DATASET_PATH}")

    df = pd.read_excel(DATASET_PATH, sheet_name=SHEET_NAME)

    required_cols = ["text", "lang", "split", "target_json"]
    missing_cols = [col for col in required_cols if col not in df.columns]

    if missing_cols:
        raise ValueError(f"Missing required columns: {missing_cols}")

    df = df.dropna(subset=required_cols).copy()

    df["text"] = df["text"].astype(str).str.strip()
    df["lang"] = df["lang"].astype(str).str.upper().str.strip()
    df["split"] = df["split"].astype(str).str.lower().str.strip()
    df["target_json"] = df["target_json"].astype(str).str.strip()

    df = df[
        (df["text"] != "")
        & (df["lang"] != "")
        & (df["target_json"] != "")
        & (df["split"].isin(["train", "val", "test"]))
    ].copy()

    if df.empty:
        raise ValueError("Dataset is empty after cleaning and split filtering.")

    invalid_rows: List[int] = []

    compact_targets = []
    for idx, value in df["target_json"].items():
        try:
            compact_targets.append(compact_json(value))
        except Exception:
            invalid_rows.append(idx)

    if invalid_rows:
        print("Invalid target_json rows:")
        print(invalid_rows[:50])
        raise ValueError(f"Found invalid target_json rows. Count: {len(invalid_rows)}")

    df["target_json"] = compact_targets

    df["source_text"] = "[" + df["lang"] + "] " + df["text"]

    return df.reset_index(drop=True)


def check_target_lengths(df: pd.DataFrame, tokenizer) -> None:
    encoded = tokenizer(
        df["target_json"].tolist(),
        add_special_tokens=True,
        truncation=False,
    )

    lengths = [len(ids) for ids in encoded["input_ids"]]
    max_len = max(lengths)
    over_limit = sum(length > MAX_TARGET_LEN for length in lengths)

    print(f"Max target token length: {max_len}")
    print(f"Targets over MAX_TARGET_LEN={MAX_TARGET_LEN}: {over_limit}")

    if over_limit > 0:
        too_long = df.iloc[[i for i, length in enumerate(lengths) if length > MAX_TARGET_LEN]]
        print("Examples of too-long target_json:")
        print(too_long[["source_text", "target_json"]].head(10).to_string(index=False))
        raise ValueError(
            f"{over_limit} target_json values are longer than MAX_TARGET_LEN={MAX_TARGET_LEN}. "
            "Increase MAX_TARGET_LEN before training."
        )


def build_dataset_dict(df: pd.DataFrame) -> DatasetDict:
    train_df = df[df["split"] == "train"][["source_text", "target_json"]].reset_index(drop=True)
    val_df = df[df["split"] == "val"][["source_text", "target_json"]].reset_index(drop=True)
    test_df = df[df["split"] == "test"][["source_text", "target_json"]].reset_index(drop=True)

    if train_df.empty:
        raise ValueError("Train split is empty.")
    if val_df.empty:
        raise ValueError("Validation split is empty.")
    if test_df.empty:
        raise ValueError("Test split is empty.")

    print(f"Train size: {len(train_df)}")
    print(f"Val size:   {len(val_df)}")
    print(f"Test size:  {len(test_df)}")

    return DatasetDict({
        "train": Dataset.from_pandas(train_df, preserve_index=False),
        "validation": Dataset.from_pandas(val_df, preserve_index=False),
        "test": Dataset.from_pandas(test_df, preserve_index=False),
    })


def main() -> None:
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    set_seed(SEED)

    print("CUDA available:", torch.cuda.is_available())
    if torch.cuda.is_available():
        print("GPU:", torch.cuda.get_device_name(0))
    else:
        print("Running on CPU")

    print("Loading dataset...")
    df = load_dataset()

    print("Split counts:")
    print(df["split"].value_counts())

    if "intent" in df.columns:
        print("Intent counts:")
        print(df["intent"].value_counts())

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, use_fast=False)
    model = MT5ForConditionalGeneration.from_pretrained(MODEL_NAME)

    print("Checking target JSON token lengths...")
    check_target_lengths(df, tokenizer)

    dataset = build_dataset_dict(df)

    def preprocess_function(batch: Dict[str, Any]) -> Dict[str, Any]:
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

    print("Tokenizing dataset...")
    tokenized_dataset = dataset.map(
        preprocess_function,
        batched=True,
        remove_columns=dataset["train"].column_names,
    )

    data_collator = DataCollatorForSeq2Seq(
        tokenizer=tokenizer,
        model=model,
    )

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

        pred_canon = [canonicalize_json_string(p) for p in decoded_preds]
        label_canon = [canonicalize_json_string(l) for l in decoded_labels]

        exact_match = np.mean([
            int(p == l)
            for p, l in zip(pred_canon, label_canon)
        ])

        valid_json_rate = np.mean([
            int(is_valid_json(p))
            for p in decoded_preds
        ])

        pred_intents = [extract_json_field(p, "intent") for p in decoded_preds]
        label_intents = [extract_json_field(l, "intent") for l in decoded_labels]

        intent_accuracy = np.mean([
            int(p == l)
            for p, l in zip(pred_intents, label_intents)
        ])

        pred_accepted = [extract_json_field(p, "accepted") for p in decoded_preds]
        label_accepted = [extract_json_field(l, "accepted") for l in decoded_labels]

        accepted_accuracy = np.mean([
            int(p == l)
            for p, l in zip(pred_accepted, label_accepted)
        ])

        return {
            "exact_match": float(exact_match),
            "valid_json_rate": float(valid_json_rate),
            "intent_accuracy": float(intent_accuracy),
            "accepted_accuracy": float(accepted_accuracy),
        }

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
        generation_num_beams=1,

        load_best_model_at_end=True,
        metric_for_best_model="exact_match",
        greater_is_better=True,

        save_total_limit=2,
        report_to="none",
        fp16=False,
        seed=SEED,
    )

    trainer = Seq2SeqTrainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_dataset["train"],
        eval_dataset=tokenized_dataset["validation"],
        tokenizer=tokenizer,
        data_collator=data_collator,
        compute_metrics=compute_metrics,
    )

    print("Starting training...")
    trainer.train()

    print("Running validation evaluation...")
    val_metrics = trainer.evaluate(
        tokenized_dataset["validation"],
        metric_key_prefix="val"
    )
    print("Validation metrics:", val_metrics)

    print("Running test evaluation...")
    test_metrics = trainer.evaluate(
        tokenized_dataset["test"],
        metric_key_prefix="test"
    )
    print("Test metrics:", test_metrics)

    print("Saving model...")
    MODEL_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    trainer.save_model(str(MODEL_OUTPUT_DIR))
    tokenizer.save_pretrained(str(MODEL_OUTPUT_DIR))

    metadata = {
        "base_model": MODEL_NAME,
        "dataset_path": str(DATASET_PATH),
        "sheet_name": SHEET_NAME,
        "max_source_len": MAX_SOURCE_LEN,
        "max_target_len": MAX_TARGET_LEN,
        "learning_rate": 3e-4,
        "train_batch_size": 8,
        "eval_batch_size": 8,
        "num_train_epochs": 8,
        "weight_decay": 0.01,
        "cuda_available": torch.cuda.is_available(),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
        "validation_metrics": val_metrics,
        "test_metrics": test_metrics,
    }

    with open(MODEL_OUTPUT_DIR / "model_meta.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("Saving debug predictions...")

    test_raw = dataset["test"]
    debug_items = []

    model.eval()
    device = model.device

    for i in range(min(30, len(test_raw))):
        source_text = test_raw[i]["source_text"]
        label = test_raw[i]["target_json"]

        inputs = tokenizer(
            source_text,
            return_tensors="pt",
            max_length=MAX_SOURCE_LEN,
            truncation=True,
        ).to(device)

        with torch.no_grad():
            output_ids = model.generate(
                **inputs,
                max_length=MAX_TARGET_LEN,
                num_beams=1,
            )

        pred = tokenizer.decode(output_ids[0], skip_special_tokens=True)

        debug_items.append({
            "source_text": source_text,
            "expected": label,
            "prediction": pred,
            "prediction_valid_json": is_valid_json(pred),
        })

    with open(MODEL_OUTPUT_DIR / "debug_predictions.json", "w", encoding="utf-8") as f:
        json.dump(debug_items, f, ensure_ascii=False, indent=2)

    print("Done.")
    print(f"Model saved to: {MODEL_OUTPUT_DIR}")
    print(f"Debug predictions saved to: {MODEL_OUTPUT_DIR / 'debug_predictions.json'}")


if __name__ == "__main__":
    main()