import os
import json
import joblib
import numpy as np
import pandas as pd
from sklearn.pipeline import Pipeline, FeatureUnion
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.svm import LinearSVC
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import (
    accuracy_score,
    precision_recall_fscore_support,
    classification_report,
    confusion_matrix,
    brier_score_loss
)

PROCESSED_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "processed")
MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "baseline_v1")
REPORTS_DIR = os.path.join(os.path.dirname(__file__), "..", "reports")

CATEGORIES = [
    "BILLS", "EDUCATION", "ENTERTAINMENT", "FOOD",
    "HEALTH", "OTHER", "PERSONAL", "SHOPPING", "TRAVEL"
]

def build_feature_extractor(use_chars=True):
    if use_chars:
        return FeatureUnion([
            ("word_tfidf", TfidfVectorizer(
                ngram_range=(1, 2),
                min_df=2,
                sublinear_tf=True
            )),
            ("char_tfidf", TfidfVectorizer(
                analyzer="char_wb",
                ngram_range=(3, 5),
                min_df=3,
                sublinear_tf=True
            ))
        ])
    else:
        return TfidfVectorizer(
            ngram_range=(1, 2),
            min_df=2,
            sublinear_tf=True
        )

def evaluate_predictions(y_true, y_pred, y_prob=None):
    acc = accuracy_score(y_true, y_pred)
    macro_p, macro_r, macro_f1, _ = precision_recall_fscore_support(y_true, y_pred, average="macro", zero_division=0)
    micro_p, micro_r, micro_f1, _ = precision_recall_fscore_support(y_true, y_pred, average="micro", zero_division=0)

    per_class_p, per_class_r, per_class_f1, per_class_sup = precision_recall_fscore_support(
        y_true, y_pred, labels=CATEGORIES, zero_division=0
    )

    per_category = {}
    for i, cat in enumerate(CATEGORIES):
        per_category[cat] = {
            "precision": round(float(per_class_p[i]), 4),
            "recall": round(float(per_class_r[i]), 4),
            "f1": round(float(per_class_f1[i]), 4),
            "support": int(per_class_sup[i])
        }

    cm = confusion_matrix(y_true, y_pred, labels=CATEGORIES).tolist()

    res = {
        "accuracy": round(float(acc), 4),
        "macro_precision": round(float(macro_p), 4),
        "macro_recall": round(float(macro_r), 4),
        "macro_f1": round(float(macro_f1), 4),
        "micro_f1": round(float(micro_f1), 4),
        "per_category": per_category,
        "confusion_matrix": cm
    }

    # Confidence calibration evaluation if probabilities are available
    if y_prob is not None:
        confidences = np.max(y_prob, axis=1)
        correct_mask = (np.array(y_true) == np.array(y_pred)).astype(int)

        # Confidence bins
        bins = [
            ("HIGH (>= 0.85)", confidences >= 0.85),
            ("MEDIUM (0.60 - 0.85)", (confidences >= 0.60) & (confidences < 0.85)),
            ("LOW (< 0.60)", confidences < 0.60)
        ]
        bin_stats = {}
        for bname, mask in bins:
            count = int(np.sum(mask))
            if count > 0:
                bin_acc = round(float(np.mean(correct_mask[mask])), 4)
                mean_conf = round(float(np.mean(confidences[mask])), 4)
            else:
                bin_acc = 0.0
                mean_conf = 0.0
            bin_stats[bname] = {
                "count": count,
                "percentage": round(count / len(y_true) * 100, 2),
                "accuracy": bin_acc,
                "mean_confidence": mean_conf
            }
        res["confidence_calibration"] = bin_stats

    return res

def run_training_and_benchmarks():
    os.makedirs(MODELS_DIR, exist_ok=True)
    os.makedirs(REPORTS_DIR, exist_ok=True)

    # Load data
    train_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_train.csv"))
    val_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_val.csv"))
    test_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_test.csv"))

    train_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_train.csv"))
    val_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_val.csv"))
    test_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_test.csv"))

    indian_sumeet = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_sumeet.csv"))
    indian_deepam = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_deepam.csv"))

    print("Loaded all partitions successfully.")

    # 1. Train Model A: Logistic Regression (Random Split)
    print("\n--- Training Model 1: Logistic Regression (Word + Char n-grams) ---")
    pipe_lr = Pipeline([
        ("features", build_feature_extractor(use_chars=True)),
        ("clf", LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42))
    ])
    pipe_lr.fit(train_rand["composite_text"], train_rand["spendsense_category"])

    pred_lr_val = pipe_lr.predict(val_rand["composite_text"])
    prob_lr_val = pipe_lr.predict_proba(val_rand["composite_text"])
    metrics_lr_val = evaluate_predictions(val_rand["spendsense_category"], pred_lr_val, prob_lr_val)

    pred_lr_test = pipe_lr.predict(test_rand["composite_text"])
    prob_lr_test = pipe_lr.predict_proba(test_rand["composite_text"])
    metrics_lr_test = evaluate_predictions(test_rand["spendsense_category"], pred_lr_test, prob_lr_test)
    print(f"LR Random Split Test Accuracy: {metrics_lr_test['accuracy']}, Macro F1: {metrics_lr_test['macro_f1']}")

    # 2. Train Model B: Linear SVM with Platt Scaling Calibration
    print("\n--- Training Model 2: Calibrated Linear SVM ---")
    base_svm = LinearSVC(C=1.0, max_iter=2000, random_state=42)
    calibrated_svm = CalibratedClassifierCV(estimator=base_svm, method="sigmoid", cv=3)
    pipe_svm = Pipeline([
        ("features", build_feature_extractor(use_chars=True)),
        ("clf", calibrated_svm)
    ])
    pipe_svm.fit(train_rand["composite_text"], train_rand["spendsense_category"])

    pred_svm_test = pipe_svm.predict(test_rand["composite_text"])
    prob_svm_test = pipe_svm.predict_proba(test_rand["composite_text"])
    metrics_svm_test = evaluate_predictions(test_rand["spendsense_category"], pred_svm_test, prob_svm_test)
    print(f"SVM Random Split Test Accuracy: {metrics_svm_test['accuracy']}, Macro F1: {metrics_svm_test['macro_f1']}")

    # 3. Leakage & Split Analysis: Train on Merchant-Aware Split (100% Unseen Merchants)
    print("\n--- Training on Merchant-Aware Split (Measuring Unseen Merchant Generalization) ---")
    pipe_lr_grp = Pipeline([
        ("features", build_feature_extractor(use_chars=True)),
        ("clf", LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42))
    ])
    pipe_lr_grp.fit(train_grp["composite_text"], train_grp["spendsense_category"])

    pred_lr_grp_test = pipe_lr_grp.predict(test_grp["composite_text"])
    prob_lr_grp_test = pipe_lr_grp.predict_proba(test_grp["composite_text"])
    metrics_lr_grp_test = evaluate_predictions(test_grp["spendsense_category"], pred_lr_grp_test, prob_lr_grp_test)
    print(f"LR Merchant-Aware (Unseen) Test Accuracy: {metrics_lr_grp_test['accuracy']}, Macro F1: {metrics_lr_grp_test['macro_f1']}")

    # 4. Ablation Study
    print("\n--- Running Ablation Studies ---")
    # Ablation A: Word-only (no character n-grams)
    pipe_word_only = Pipeline([
        ("features", build_feature_extractor(use_chars=False)),
        ("clf", LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42))
    ])
    pipe_word_only.fit(train_rand["composite_text"], train_rand["spendsense_category"])
    pred_ablation_word = pipe_word_only.predict(test_rand["composite_text"])
    metrics_ablation_word = evaluate_predictions(test_rand["spendsense_category"], pred_ablation_word)
    print(f"Ablation (Word-only) Test Accuracy: {metrics_ablation_word['accuracy']}, Macro F1: {metrics_ablation_word['macro_f1']}")

    # Ablation B: Merchant-only vs Composite Text (with description)
    pipe_merchant_only = Pipeline([
        ("features", build_feature_extractor(use_chars=True)),
        ("clf", LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42))
    ])
    pipe_merchant_only.fit(train_rand["normalized_merchant"], train_rand["spendsense_category"])
    pred_ablation_merchant = pipe_merchant_only.predict(test_rand["normalized_merchant"])
    metrics_ablation_merchant = evaluate_predictions(test_rand["spendsense_category"], pred_ablation_merchant)
    print(f"Ablation (Merchant-only) Test Accuracy: {metrics_ablation_merchant['accuracy']}, Macro F1: {metrics_ablation_merchant['macro_f1']}")

    # 5. Indian / UPI Domain Transfer Evaluation
    print("\n--- Evaluating Cross-Market Generalization on Indian / UPI Transactions ---")
    pred_indian_sumeet = pipe_lr.predict(indian_sumeet["composite_text"])
    prob_indian_sumeet = pipe_lr.predict_proba(indian_sumeet["composite_text"])
    metrics_indian_sumeet = evaluate_predictions(indian_sumeet["spendsense_category"], pred_indian_sumeet, prob_indian_sumeet)
    print(f"Indian Sumeet Zero-Shot Accuracy: {metrics_indian_sumeet['accuracy']}, Macro F1: {metrics_indian_sumeet['macro_f1']}")

    pred_indian_deepam = pipe_lr.predict(indian_deepam["composite_text"])
    metrics_indian_deepam = evaluate_predictions(indian_deepam["spendsense_category"], pred_indian_deepam)
    print(f"Indian Deepam Zero-Shot Accuracy: {metrics_indian_deepam['accuracy']}, Macro F1: {metrics_indian_deepam['macro_f1']}")

    # 6. Save Winner Model Artifact and Metadata
    print("\n--- Saving Model Artifact & Comprehensive Report ---")
    winner_pipeline = pipe_lr
    model_artifact_path = os.path.join(MODELS_DIR, "spend_sense_baseline_model.joblib")
    joblib.dump(winner_pipeline, model_artifact_path)
    print(f"Serialized baseline model artifact to {model_artifact_path}")

    experiment_summary = {
        "model_metadata": {
            "model_version": "v1.0.0-baseline-tfidf-lr",
            "model_type": "LogisticRegression",
            "feature_pipeline_version": "v1.0.0",
            "dataset_version": "us_bank_categories_v2_processed_v1",
            "preprocessing_version": "v1.0.0",
            "random_seed": 42,
            "classes": CATEGORIES,
            "hyperparameters": {
                "C": 2.0,
                "class_weight": "balanced",
                "max_iter": 1000,
                "word_ngram_range": [1, 2],
                "char_ngram_range": [3, 5],
                "sublinear_tf": True
            }
        },
        "benchmarks": {
            "logistic_regression_random_split_val": metrics_lr_val,
            "logistic_regression_random_split_test": metrics_lr_test,
            "calibrated_svm_random_split_test": metrics_svm_test,
            "logistic_regression_merchant_aware_unseen_test": metrics_lr_grp_test,
            "ablation_word_only_test": metrics_ablation_word,
            "ablation_merchant_only_test": metrics_ablation_merchant,
            "indian_domain_sumeet_test": metrics_indian_sumeet,
            "indian_domain_deepam_test": metrics_indian_deepam
        }
    }

    metrics_json_path = os.path.join(REPORTS_DIR, "baseline_experiment_results.json")
    with open(metrics_json_path, "w", encoding="utf-8") as f:
        json.dump(experiment_summary, f, indent=2)
    print(f"Full experiment metrics saved to {metrics_json_path}")

if __name__ == "__main__":
    run_training_and_benchmarks()
