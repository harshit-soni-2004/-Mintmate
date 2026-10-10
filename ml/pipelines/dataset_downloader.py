import os
import hashlib
import json
import urllib.request

DATA_RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
MANIFEST_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "manifests", "dataset_manifest.json")

DATASETS = {
    "us_bank_categories_v2": {
        "url": "https://huggingface.co/datasets/DoDataThings/us-bank-transaction-categories-v2/raw/main/transactions-synthetic.csv",
        "filename": "us_bank_transactions_v2.csv",
        "license": "MIT",
        "source": "Hugging Face (DoDataThings/us-bank-transaction-categories-v2)",
        "provenance": "Synthetic transaction descriptions modeled after real US bank statement formats across 17 spending categories.",
        "intended_use": "Primary training & evaluation benchmark for baseline text classification models."
    },
    "indian_transactions_sumeet": {
        "url": "https://huggingface.co/datasets/Sumeetgpt/indian-transaction-categorization-synthetic/raw/main/synthetic_indian_transactions.csv",
        "filename": "indian_transactions_sumeet.csv",
        "license": "MIT",
        "source": "Hugging Face (Sumeetgpt/indian-transaction-categorization-synthetic)",
        "provenance": "Synthetic Indian bank/credit-card statement narrations mimicking Indian payment rails and vendors.",
        "intended_use": "Indian transaction evaluation & adaptation benchmarking."
    },
    "deepam_indian_train": {
        "url": "https://raw.githubusercontent.com/DeepamRor/Banking-Transaction-Text-Categorization-Dataset/main/financial_transactions_train.csv",
        "filename": "deepam_indian_train.csv",
        "license": "Apache 2.0",
        "source": "GitHub (DeepamRor/Banking-Transaction-Text-Categorization-Dataset)",
        "provenance": "Templated Indian banking transactions covering top Indian merchants and expense categories.",
        "intended_use": "Indian market evaluation partition."
    },
    "deepam_indian_test": {
        "url": "https://raw.githubusercontent.com/DeepamRor/Banking-Transaction-Text-Categorization-Dataset/main/financial_transactions_test.csv",
        "filename": "deepam_indian_test.csv",
        "license": "Apache 2.0",
        "source": "GitHub (DeepamRor/Banking-Transaction-Text-Categorization-Dataset)",
        "provenance": "Templated Indian banking transactions holdout set.",
        "intended_use": "Indian market unseen test benchmark."
    }
}

def sha256_file(filepath):
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

def download_datasets():
    os.makedirs(DATA_RAW_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(MANIFEST_PATH), exist_ok=True)

    manifest = {"datasets": {}}

    for key, meta in DATASETS.items():
        filepath = os.path.join(DATA_RAW_DIR, meta["filename"])
        if not os.path.exists(filepath):
            print(f"Downloading {key} from {meta['url']}...")
            req = urllib.request.Request(meta["url"], headers={"User-Agent": "Mintmate-Research/1.0"})
            with urllib.request.urlopen(req, timeout=60) as resp, open(filepath, "wb") as f:
                f.write(resp.read())
            print(f"Saved to {filepath}")
        else:
            print(f"File {filepath} already exists.")

        file_hash = sha256_file(filepath)
        size_bytes = os.path.getsize(filepath)

        manifest["datasets"][key] = {
            "filename": meta["filename"],
            "source_url": meta["url"],
            "license": meta["license"],
            "provenance": meta["provenance"],
            "intended_use": meta["intended_use"],
            "sha256": file_hash,
            "size_bytes": size_bytes
        }

    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print(f"Manifest saved to {MANIFEST_PATH}")

if __name__ == "__main__":
    download_datasets()
