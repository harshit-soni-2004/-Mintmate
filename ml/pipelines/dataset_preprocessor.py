import os
import json
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split, GroupShuffleSplit
from normalizer_bridge import TransactionNormalizer

DATA_RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
DATA_PROCESSED_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "processed")
REPORTS_DIR = os.path.join(os.path.dirname(__file__), "..", "reports")

# Explicit mapping from external datasets to SpendSense's 9 categories
US_BANK_TAXONOMY_MAPPING = {
    "Restaurants": "FOOD",
    "Groceries": "FOOD",
    "Transportation": "TRAVEL",
    "Travel": "TRAVEL",
    "Shopping": "SHOPPING",
    "Entertainment": "ENTERTAINMENT",
    "Subscription": "ENTERTAINMENT",
    "Utilities": "BILLS",
    "Rent": "BILLS",
    "Mortgage": "BILLS",
    "Insurance": "BILLS",
    "Healthcare": "HEALTH",
    "Education": "EDUCATION",
    "Personal Care": "PERSONAL",
    "Fees": "OTHER",
    # Explicit exclusions (non-expense financial movements)
    "Transfer": None,
    "Income": None
}

INDIAN_SUMEET_TAXONOMY_MAPPING = {
    "Eating Out": "FOOD",
    "Groceries": "FOOD",
    "Travel": "TRAVEL",
    "Transportation & Gas": "TRAVEL",
    "Shopping & Clothing": "SHOPPING",
    "Entertainment & Subscriptions": "ENTERTAINMENT",
    "Utilities": "BILLS",
    "Rent & Mortgage": "BILLS",
    "Insurance": "BILLS",
    "Medicine & Pharmacy": "HEALTH",
    "Hospital & Medical": "HEALTH",
    "Education": "EDUCATION",
    "Kids Activities": "EDUCATION",
    "Personal Care": "PERSONAL",
    "Fees & Interest": "OTHER",
    "ATM & Cash": "OTHER",
    "Income & Deposits": None,
    "Investments & Savings Transfer": None
}

DEEPAM_TAXONOMY_MAPPING = {
    "food": "FOOD",
    "travel": "TRAVEL",
    "shopping": "SHOPPING",
    "emi": "BILLS",
    "investment": None
}

def preprocess_datasets():
    os.makedirs(DATA_PROCESSED_DIR, exist_ok=True)
    normalizer = TransactionNormalizer()

    # 1. Process US Bank Transactions v2 (Primary Training & Benchmark)
    us_raw_path = os.path.join(DATA_RAW_DIR, "us_bank_transactions_v2.csv")
    df_us = pd.read_csv(us_raw_path)
    print(f"Loading US Bank v2: {len(df_us)} rows")

    # Map taxonomy
    df_us["spendsense_category"] = df_us["category"].map(US_BANK_TAXONOMY_MAPPING)
    excluded_us = df_us["spendsense_category"].isna().sum()
    print(f"Excluded {excluded_us} rows from US Bank v2 (Income / Transfer)")
    df_us = df_us.dropna(subset=["spendsense_category"]).copy()

    # Normalize transactions
    print("Normalizing US Bank v2 records...")
    normalized_records = df_us["description"].apply(lambda x: normalizer.normalize(x))
    df_us["normalized_merchant"] = [r.normalized_merchant for r in normalized_records]
    df_us["composite_text"] = [r.composite_text for r in normalized_records]

    # Deduplicate exact text + label matches to prevent duplicate inflation
    before_dedup = len(df_us)
    df_us = df_us.drop_duplicates(subset=["composite_text", "spendsense_category"]).copy()
    print(f"Deduplicated US Bank v2: {before_dedup} -> {len(df_us)} rows")

    # Save processed main dataset
    us_processed_path = os.path.join(DATA_PROCESSED_DIR, "train_dataset_global.csv")
    df_us.to_csv(us_processed_path, index=False)
    print(f"Saved primary global dataset: {us_processed_path} ({len(df_us)} rows)")

    # 2. Process Indian Sumeetgpt Dataset
    sumeet_raw_path = os.path.join(DATA_RAW_DIR, "indian_transactions_sumeet.csv")
    df_sumeet = pd.read_csv(sumeet_raw_path)
    df_sumeet["spendsense_category"] = df_sumeet["category"].map(INDIAN_SUMEET_TAXONOMY_MAPPING)
    df_sumeet = df_sumeet.dropna(subset=["spendsense_category"]).copy()

    norm_sumeet = df_sumeet["text"].apply(lambda x: normalizer.normalize(x))
    df_sumeet["normalized_merchant"] = [r.normalized_merchant for r in norm_sumeet]
    df_sumeet["composite_text"] = [r.composite_text for r in norm_sumeet]
    df_sumeet = df_sumeet.drop_duplicates(subset=["composite_text", "spendsense_category"]).copy()

    sumeet_processed_path = os.path.join(DATA_PROCESSED_DIR, "eval_dataset_indian_sumeet.csv")
    df_sumeet.to_csv(sumeet_processed_path, index=False)
    print(f"Saved Indian Sumeet evaluation dataset: {sumeet_processed_path} ({len(df_sumeet)} rows)")

    # 3. Process Deepam Indian Test Partition
    deepam_raw_path = os.path.join(DATA_RAW_DIR, "deepam_indian_test.csv")
    df_deepam = pd.read_csv(deepam_raw_path)
    df_deepam["spendsense_category"] = df_deepam["label"].map(DEEPAM_TAXONOMY_MAPPING)
    df_deepam = df_deepam.dropna(subset=["spendsense_category"]).copy()

    norm_deepam = df_deepam["transaction_narration"].apply(lambda x: normalizer.normalize(x))
    df_deepam["normalized_merchant"] = [r.normalized_merchant for r in norm_deepam]
    df_deepam["composite_text"] = [r.composite_text for r in norm_deepam]
    df_deepam = df_deepam.drop_duplicates(subset=["composite_text", "spendsense_category"]).copy()

    deepam_processed_path = os.path.join(DATA_PROCESSED_DIR, "eval_dataset_indian_deepam.csv")
    df_deepam.to_csv(deepam_processed_path, index=False)
    print(f"Saved Indian Deepam evaluation dataset: {deepam_processed_path} ({len(df_deepam)} rows)")

    # 4. Generate Splits for Primary Global Dataset
    # Split A: Stratified Random Split (Train 70%, Val 15%, Test 15%)
    X = df_us
    y = df_us["spendsense_category"]

    X_train_rand, X_temp_rand, y_train_rand, y_temp_rand = train_test_split(
        X, y, test_size=0.30, random_state=42, stratify=y
    )
    X_val_rand, X_test_rand, y_val_rand, y_test_rand = train_test_split(
        X_temp_rand, y_temp_rand, test_size=0.50, random_state=42, stratify=y_temp_rand
    )

    X_train_rand.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_random_train.csv"), index=False)
    X_val_rand.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_random_val.csv"), index=False)
    X_test_rand.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_random_test.csv"), index=False)
    print(f"Random Split generated: Train={len(X_train_rand)}, Val={len(X_val_rand)}, Test={len(X_test_rand)}")

    # Split B: Merchant-Aware Group Split (Train ~70%, Val ~15%, Test ~15%)
    # Merchants in Test/Val never appear in Train!
    groups = df_us["normalized_merchant"].values
    gss = GroupShuffleSplit(n_splits=1, train_size=0.70, random_state=42)
    train_idx, temp_idx = next(gss.split(X, y, groups=groups))

    X_train_grp = df_us.iloc[train_idx].copy()
    X_temp_grp = df_us.iloc[temp_idx].copy()

    temp_groups = X_temp_grp["normalized_merchant"].values
    gss_temp = GroupShuffleSplit(n_splits=1, train_size=0.50, random_state=42)
    val_sub_idx, test_sub_idx = next(gss_temp.split(X_temp_grp, X_temp_grp["spendsense_category"], groups=temp_groups))

    X_val_grp = X_temp_grp.iloc[val_sub_idx].copy()
    X_test_grp = X_temp_grp.iloc[test_sub_idx].copy()

    X_train_grp.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_merchant_aware_train.csv"), index=False)
    X_val_grp.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_merchant_aware_val.csv"), index=False)
    X_test_grp.to_csv(os.path.join(DATA_PROCESSED_DIR, "split_merchant_aware_test.csv"), index=False)

    train_merchants = set(X_train_grp["normalized_merchant"])
    test_merchants = set(X_test_grp["normalized_merchant"])
    overlap = len(train_merchants.intersection(test_merchants))
    print(f"Merchant-Aware Split: Train={len(X_train_grp)} ({len(train_merchants)} merchants), Val={len(X_val_grp)}, Test={len(X_test_grp)} ({len(test_merchants)} merchants)")
    print(f"Merchant Overlap between Train and Test: {overlap} (Strictly 0 leakage!)")

    # Generate Preprocessing Summary Manifest
    summary = {
        "preprocessing_version": "v1.0.0",
        "taxonomy": "SpendSense-9",
        "random_split": {
            "train_size": len(X_train_rand),
            "val_size": len(X_val_rand),
            "test_size": len(X_test_rand)
        },
        "merchant_aware_split": {
            "train_size": len(X_train_grp),
            "val_size": len(X_val_grp),
            "test_size": len(X_test_grp),
            "train_merchant_count": len(train_merchants),
            "test_merchant_count": len(test_merchants),
            "merchant_leakage_overlap": overlap
        },
        "category_distribution_train": X_train_rand["spendsense_category"].value_counts().to_dict(),
        "category_distribution_test": X_test_rand["spendsense_category"].value_counts().to_dict()
    }
    with open(os.path.join(DATA_PROCESSED_DIR, "preprocessing_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print("Preprocessing completed successfully!")

if __name__ == "__main__":
    preprocess_datasets()
