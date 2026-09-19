import os
import json
import pandas as pd
from normalizer_bridge import TransactionNormalizer

DATA_RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
REPORTS_DIR = os.path.join(os.path.dirname(__file__), "..", "reports")

def audit_dataset(name, filename, text_col, cat_col):
    filepath = os.path.join(DATA_RAW_DIR, filename)
    df = pd.read_csv(filepath)

    total_rows = len(df)
    missing_text = int(df[text_col].isna().sum())
    missing_cat = int(df[cat_col].isna().sum())
    
    exact_duplicates = int(df.duplicated(subset=[text_col]).sum())
    
    # Analyze string lengths
    clean_series = df[text_col].dropna().astype(str)
    char_lengths = clean_series.apply(len)
    word_counts = clean_series.apply(lambda x: len(x.split()))
    
    extremely_short = int((word_counts < 2).sum())
    
    cat_distribution = df[cat_col].value_counts().to_dict()
    
    # Inspect samples per category
    samples_per_cat = {}
    for cat in df[cat_col].dropna().unique():
        samples = df[df[cat_col] == cat][text_col].dropna().head(3).tolist()
        samples_per_cat[str(cat)] = samples

    # Test normalization impact
    normalizer = TransactionNormalizer()
    normalized_texts = clean_series.apply(lambda x: normalizer.normalize(x).normalized_merchant)
    normalized_duplicates = int(normalized_texts.duplicated().sum())

    report = {
        "dataset_name": name,
        "filename": filename,
        "total_rows": total_rows,
        "missing_text_count": missing_text,
        "missing_category_count": missing_cat,
        "exact_duplicates": exact_duplicates,
        "exact_duplicate_rate_pct": round(exact_duplicates / total_rows * 100, 2) if total_rows > 0 else 0,
        "normalized_duplicates": normalized_duplicates,
        "normalized_duplicate_rate_pct": round(normalized_duplicates / total_rows * 100, 2) if total_rows > 0 else 0,
        "extremely_short_records": extremely_short,
        "char_length_stats": {
            "min": int(char_lengths.min()) if not char_lengths.empty else 0,
            "mean": round(float(char_lengths.mean()), 2) if not char_lengths.empty else 0,
            "median": int(char_lengths.median()) if not char_lengths.empty else 0,
            "max": int(char_lengths.max()) if not char_lengths.empty else 0,
        },
        "word_count_stats": {
            "min": int(word_counts.min()) if not word_counts.empty else 0,
            "mean": round(float(word_counts.mean()), 2) if not word_counts.empty else 0,
            "median": int(word_counts.median()) if not word_counts.empty else 0,
            "max": int(word_counts.max()) if not word_counts.empty else 0,
        },
        "category_distribution": cat_distribution,
        "samples_per_category": samples_per_cat
    }
    return report

def run_audits():
    os.makedirs(REPORTS_DIR, exist_ok=True)
    audits = {}

    print("Auditing US Bank Transactions v2...")
    audits["us_bank_transactions_v2"] = audit_dataset(
        "US Bank Transactions v2 (DoDataThings)",
        "us_bank_transactions_v2.csv",
        "description",
        "category"
    )

    print("Auditing Indian Transactions (Sumeetgpt)...")
    audits["indian_transactions_sumeet"] = audit_dataset(
        "Indian Transactions (Sumeetgpt)",
        "indian_transactions_sumeet.csv",
        "text",
        "category"
    )

    print("Auditing Deepam Indian Train...")
    audits["deepam_indian_train"] = audit_dataset(
        "Deepam Indian Train (GitHub)",
        "deepam_indian_train.csv",
        "transaction_narration",
        "label"
    )

    print("Auditing Deepam Indian Test...")
    audits["deepam_indian_test"] = audit_dataset(
        "Deepam Indian Test (GitHub)",
        "deepam_indian_test.csv",
        "transaction_narration",
        "label"
    )

    output_path = os.path.join(REPORTS_DIR, "data_audit_report.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(audits, f, indent=2)
    print(f"Data audit report saved to {output_path}")

if __name__ == "__main__":
    run_audits()
