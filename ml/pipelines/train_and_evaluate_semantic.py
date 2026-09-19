import os
import sys
import time
import json
import tracemalloc
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.svm import LinearSVC
from sklearn.calibration import CalibratedClassifierCV
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion
from sklearn.metrics import (
    accuracy_score,
    precision_recall_fscore_support,
    confusion_matrix,
    brier_score_loss
)
from sentence_transformers import SentenceTransformer

PROCESSED_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "processed")
EMBEDDINGS_DIR = os.path.join(PROCESSED_DIR, "embeddings")
REPORTS_DIR = os.path.join(os.path.dirname(__file__), "..", "reports")
MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "semantic_v1")

CATEGORIES = [
    "BILLS", "EDUCATION", "ENTERTAINMENT", "FOOD",
    "HEALTH", "OTHER", "PERSONAL", "SHOPPING", "TRAVEL"
]

def ensure_dirs():
    os.makedirs(EMBEDDINGS_DIR, exist_ok=True)
    os.makedirs(REPORTS_DIR, exist_ok=True)
    os.makedirs(MODELS_DIR, exist_ok=True)

def get_or_compute_embeddings(model, texts, cache_name):
    cache_path = os.path.join(EMBEDDINGS_DIR, f"{cache_name}.npy")
    if os.path.exists(cache_path):
        print(f"Loading cached embeddings from {cache_path}...")
        return np.load(cache_path)
    
    print(f"Computing embeddings for {cache_name} ({len(texts)} samples)...")
    t0 = time.time()
    embeddings = model.encode(
        texts.tolist() if isinstance(texts, pd.Series) else list(texts),
        batch_size=128,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=True
    )
    t1 = time.time()
    print(f"Computed embeddings in {t1 - t0:.2f}s ({len(texts)/(t1 - t0):.1f} samples/sec)")
    np.save(cache_path, embeddings)
    return embeddings

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

    if y_prob is not None:
        confidences = np.max(y_prob, axis=1)
        correct_mask = (np.array(y_true) == np.array(y_pred)).astype(int)

        # Multi-class Brier score
        # One-hot encode y_true
        y_true_indices = np.array([CATEGORIES.index(c) for c in y_true])
        y_true_onehot = np.zeros_like(y_prob)
        for idx, cat_idx in enumerate(y_true_indices):
            y_true_onehot[idx, cat_idx] = 1.0
        brier = float(np.mean(np.sum((y_prob - y_true_onehot) ** 2, axis=1)))
        res["brier_score"] = round(brier, 4)

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

def extract_structured_features(df, scaler=None, ohe=None, fit=False):
    # 1. Text source column
    raw_col = "description" if "description" in df.columns else ("text" if "text" in df.columns else "composite_text")
    raw_texts = df[raw_col].fillna("").astype(str).str.lower()

    # 2. Payment Channel extraction from text
    def detect_channel(txt):
        if "upi" in txt or any(v in txt for v in ["@ok", "@ybl", "@paytm", "@axl", "@ibl", "upi/"]):
            return "UPI"
        elif "[debit]" in txt or "debit" in txt or "atm" in txt:
            return "DEBIT"
        elif "[credit]" in txt or "credit" in txt:
            return "CREDIT"
        elif any(w in txt for w in ["ach", "ppd", "direct dep", "neft", "rtgs", "imps", "transfer", "eft"]):
            return "TRANSFER_ACH"
        elif any(w in txt for w in ["bill", "pymt", "autopay"]):
            return "BILL_PAY"
        else:
            return "GENERAL"

    channels = raw_texts.apply(detect_channel).values.reshape(-1, 1)
    if fit:
        ohe = OneHotEncoder(handle_unknown="ignore", sparse_output=False)
        channel_ohe = ohe.fit_transform(channels)
    else:
        channel_ohe = ohe.transform(channels)

    # 3. Surface structural features
    char_lens = raw_texts.str.len().values.reshape(-1, 1)
    word_counts = raw_texts.str.split().str.len().values.reshape(-1, 1)
    has_vpa = raw_texts.str.contains("@", regex=False).astype(float).values.reshape(-1, 1)
    has_digits = raw_texts.str.contains(r"\d", regex=True).astype(float).values.reshape(-1, 1)

    numeric_feats = np.hstack([char_lens, word_counts, has_vpa, has_digits])
    if fit:
        scaler = StandardScaler()
        norm_numerics = scaler.fit_transform(numeric_feats)
    else:
        norm_numerics = scaler.transform(numeric_feats)

    struct_feats = np.hstack([norm_numerics, channel_ohe])
    return struct_feats, scaler, ohe

def run_semantic_experiments():
    ensure_dirs()
    print("===============================================================")
    print("PHASE 6F — ADVANCED SEMANTIC MODEL EXPERIMENT PIPELINE")
    print("===============================================================")

    # 1. Load Preprocessed Datasets
    print("\n[Step 1] Loading datasets...")
    train_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_train.csv"))
    val_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_val.csv"))
    test_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_test.csv"))

    train_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_train.csv"))
    val_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_val.csv"))
    test_grp = pd.read_csv(os.path.join(PROCESSED_DIR, "split_merchant_aware_test.csv"))

    indian_sumeet = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_sumeet.csv"))
    indian_deepam = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_deepam.csv"))

    print(f"Loaded train_rand ({len(train_rand)}), val_rand ({len(val_rand)}), test_rand ({len(test_rand)})")
    print(f"Loaded train_grp ({len(train_grp)}), test_grp ({len(test_grp)})")
    print(f"Loaded indian_sumeet ({len(indian_sumeet)}), indian_deepam ({len(indian_deepam)})")

    # 2. Load Model & Benchmark Loading Time / Footprint
    print("\n[Step 2] Initializing SentenceTransformer('all-MiniLM-L6-v2')...")
    tracemalloc.start()
    t_load_0 = time.time()
    model = SentenceTransformer("all-MiniLM-L6-v2", device="cpu")
    t_load_1 = time.time()
    current_mem, peak_mem = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    model_load_time_sec = round(t_load_1 - t_load_0, 3)
    model_peak_ram_mb = round(peak_mem / (1024 * 1024), 2)
    print(f"Model loaded in {model_load_time_sec}s. Peak memory during load: {model_peak_ram_mb} MB")

    # 3. Compute or Load Cached Embeddings
    print("\n[Step 3] Computing / Loading Embeddings...")
    X_train_emb = get_or_compute_embeddings(model, train_rand["composite_text"], "train_rand_composite")
    X_val_emb = get_or_compute_embeddings(model, val_rand["composite_text"], "val_rand_composite")
    X_test_emb = get_or_compute_embeddings(model, test_rand["composite_text"], "test_rand_composite")

    X_train_grp_emb = get_or_compute_embeddings(model, train_grp["composite_text"], "train_grp_composite")
    X_test_grp_emb = get_or_compute_embeddings(model, test_grp["composite_text"], "test_grp_composite")

    X_sumeet_emb = get_or_compute_embeddings(model, indian_sumeet["composite_text"], "sumeet_composite")
    X_deepam_emb = get_or_compute_embeddings(model, indian_deepam["composite_text"], "deepam_composite")

    # Also for input representation ablation (normalized_merchant)
    X_train_merchant_emb = get_or_compute_embeddings(model, train_rand["normalized_merchant"], "train_rand_merchant")
    X_test_merchant_emb = get_or_compute_embeddings(model, test_rand["normalized_merchant"], "test_rand_merchant")

    y_train = train_rand["spendsense_category"].values
    y_val = val_rand["spendsense_category"].values
    y_test = test_rand["spendsense_category"].values

    y_train_grp = train_grp["spendsense_category"].values
    y_test_grp = test_grp["spendsense_category"].values

    y_sumeet = indian_sumeet["spendsense_category"].values
    y_deepam = indian_deepam["spendsense_category"].values

    results = {}

    # =========================================================================
    # EXPERIMENT 1: Semantic Embedding + Logistic Regression
    # =========================================================================
    print("\n--- EXPERIMENT 1: Semantic Embedding + Logistic Regression ---")
    clf_lr = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_lr.fit(X_train_emb, y_train)

    pred_lr_test = clf_lr.predict(X_test_emb)
    prob_lr_test = clf_lr.predict_proba(X_test_emb)
    res_lr_test = evaluate_predictions(y_test, pred_lr_test, prob_lr_test)

    pred_lr_sumeet = clf_lr.predict(X_sumeet_emb)
    prob_lr_sumeet = clf_lr.predict_proba(X_sumeet_emb)
    res_lr_sumeet = evaluate_predictions(y_sumeet, pred_lr_sumeet, prob_lr_sumeet)

    pred_lr_deepam = clf_lr.predict(X_deepam_emb)
    prob_lr_deepam = clf_lr.predict_proba(X_deepam_emb)
    res_lr_deepam = evaluate_predictions(y_deepam, pred_lr_deepam, prob_lr_deepam)

    print(f"[Exp 1 - In-Domain Test] Accuracy: {res_lr_test['accuracy']}, Macro F1: {res_lr_test['macro_f1']}, Brier: {res_lr_test.get('brier_score')}")
    print(f"[Exp 1 - Indian Sumeet Zero-Shot] Accuracy: {res_lr_sumeet['accuracy']}, Macro F1: {res_lr_sumeet['macro_f1']}, Brier: {res_lr_sumeet.get('brier_score')}")
    print(f"[Exp 1 - Indian Deepam Zero-Shot] Accuracy: {res_lr_deepam['accuracy']}, Macro F1: {res_lr_deepam['macro_f1']}")

    results["exp1_semantic_lr"] = {
        "model_name": "SentenceTransformer(all-MiniLM-L6-v2) + LogisticRegression",
        "in_domain_test": res_lr_test,
        "indian_sumeet": res_lr_sumeet,
        "indian_deepam": res_lr_deepam
    }

    # =========================================================================
    # EXPERIMENT 2: Semantic Embedding + Calibrated Linear SVM
    # =========================================================================
    print("\n--- EXPERIMENT 2: Semantic Embedding + Calibrated Linear SVM ---")
    base_svm = LinearSVC(C=1.0, max_iter=2000, random_state=42)
    clf_svm = CalibratedClassifierCV(estimator=base_svm, method="sigmoid", cv=3)
    clf_svm.fit(X_train_emb, y_train)

    pred_svm_test = clf_svm.predict(X_test_emb)
    prob_svm_test = clf_svm.predict_proba(X_test_emb)
    res_svm_test = evaluate_predictions(y_test, pred_svm_test, prob_svm_test)

    pred_svm_sumeet = clf_svm.predict(X_sumeet_emb)
    prob_svm_sumeet = clf_svm.predict_proba(X_sumeet_emb)
    res_svm_sumeet = evaluate_predictions(y_sumeet, pred_svm_sumeet, prob_svm_sumeet)

    print(f"[Exp 2 - In-Domain Test] Accuracy: {res_svm_test['accuracy']}, Macro F1: {res_svm_test['macro_f1']}")
    print(f"[Exp 2 - Indian Sumeet Zero-Shot] Accuracy: {res_svm_sumeet['accuracy']}, Macro F1: {res_svm_sumeet['macro_f1']}")

    results["exp2_semantic_calibrated_svm"] = {
        "model_name": "SentenceTransformer(all-MiniLM-L6-v2) + CalibratedLinearSVC",
        "in_domain_test": res_svm_test,
        "indian_sumeet": res_svm_sumeet
    }

    # =========================================================================
    # EXPERIMENT 3: Merchant-Aware Split (Unseen Merchant Generalization)
    # =========================================================================
    print("\n--- EXPERIMENT 3: Merchant-Aware Split (Unseen Merchant Generalization) ---")
    clf_lr_grp = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_lr_grp.fit(X_train_grp_emb, y_train_grp)

    pred_lr_grp_test = clf_lr_grp.predict(X_test_grp_emb)
    prob_lr_grp_test = clf_lr_grp.predict_proba(X_test_grp_emb)
    res_lr_grp_test = evaluate_predictions(y_test_grp, pred_lr_grp_test, prob_lr_grp_test)

    print(f"[Exp 3 - Unseen Merchant Test] Accuracy: {res_lr_grp_test['accuracy']}, Macro F1: {res_lr_grp_test['macro_f1']}")

    results["exp3_unseen_merchants"] = {
        "model_name": "Semantic LR on Merchant-Aware Split",
        "unseen_merchant_test": res_lr_grp_test
    }

    # =========================================================================
    # EXPERIMENT 4: Representation Ablation (Merchant-only vs Composite Text)
    # =========================================================================
    print("\n--- EXPERIMENT 4: Representation Ablation (Merchant-only vs Composite) ---")
    clf_merchant_only = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_merchant_only.fit(X_train_merchant_emb, y_train)

    pred_merchant_test = clf_merchant_only.predict(X_test_merchant_emb)
    res_merchant_test = evaluate_predictions(y_test, pred_merchant_test)

    # Indian sumeet merchant only
    X_sumeet_merchant_emb = get_or_compute_embeddings(model, indian_sumeet["normalized_merchant"], "sumeet_merchant")
    pred_merchant_sumeet = clf_merchant_only.predict(X_sumeet_merchant_emb)
    res_merchant_sumeet = evaluate_predictions(y_sumeet, pred_merchant_sumeet)

    print(f"[Exp 4 - Merchant-only Test] Accuracy: {res_merchant_test['accuracy']}, Macro F1: {res_merchant_test['macro_f1']}")
    print(f"[Exp 4 - Merchant-only Sumeet] Accuracy: {res_merchant_sumeet['accuracy']}, Macro F1: {res_merchant_sumeet['macro_f1']}")

    results["exp4_representation_ablation"] = {
        "merchant_only_test": res_merchant_test,
        "merchant_only_sumeet": res_merchant_sumeet
    }

    # =========================================================================
    # EXPERIMENT 5: Feature Fusion Ablation (Embedding + Structured Features)
    # =========================================================================
    print("\n--- EXPERIMENT 5: Feature Fusion Ablation (Embedding + Structured) ---")
    struct_train, scaler, ohe = extract_structured_features(train_rand, fit=True)
    struct_test, _, _ = extract_structured_features(test_rand, scaler=scaler, ohe=ohe, fit=False)
    struct_sumeet, _, _ = extract_structured_features(indian_sumeet, scaler=scaler, ohe=ohe, fit=False)

    X_train_fused = np.hstack([X_train_emb, struct_train])
    X_test_fused = np.hstack([X_test_emb, struct_test])
    X_sumeet_fused = np.hstack([X_sumeet_emb, struct_sumeet])

    clf_fused = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_fused.fit(X_train_fused, y_train)

    pred_fused_test = clf_fused.predict(X_test_fused)
    prob_fused_test = clf_fused.predict_proba(X_test_fused)
    res_fused_test = evaluate_predictions(y_test, pred_fused_test, prob_fused_test)

    pred_fused_sumeet = clf_fused.predict(X_sumeet_fused)
    prob_fused_sumeet = clf_fused.predict_proba(X_sumeet_fused)
    res_fused_sumeet = evaluate_predictions(y_sumeet, pred_fused_sumeet, prob_fused_sumeet)

    print(f"[Exp 5 - Fused Test] Accuracy: {res_fused_test['accuracy']}, Macro F1: {res_fused_test['macro_f1']}")
    print(f"[Exp 5 - Fused Sumeet] Accuracy: {res_fused_sumeet['accuracy']}, Macro F1: {res_fused_sumeet['macro_f1']}")

    results["exp5_feature_fusion"] = {
        "embedding_plus_structured_test": res_fused_test,
        "embedding_plus_structured_sumeet": res_fused_sumeet
    }

    # =========================================================================
    # EXPERIMENT 6: Hybrid Architecture (Embedding + TF-IDF)
    # =========================================================================
    print("\n--- EXPERIMENT 6: Hybrid Architecture (Embedding + TF-IDF) ---")
    tfidf_feat = FeatureUnion([
        ("word_tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True, max_features=5000)),
        ("char_tfidf", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=3, sublinear_tf=True, max_features=5000))
    ])
    tfidf_train_sparse = tfidf_feat.fit_transform(train_rand["composite_text"])
    tfidf_test_sparse = tfidf_feat.transform(test_rand["composite_text"])
    tfidf_sumeet_sparse = tfidf_feat.transform(indian_sumeet["composite_text"])

    from scipy.sparse import hstack as sp_hstack, csr_matrix
    X_train_hybrid = sp_hstack([csr_matrix(X_train_emb), tfidf_train_sparse])
    X_test_hybrid = sp_hstack([csr_matrix(X_test_emb), tfidf_test_sparse])
    X_sumeet_hybrid = sp_hstack([csr_matrix(X_sumeet_emb), tfidf_sumeet_sparse])

    clf_hybrid = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_hybrid.fit(X_train_hybrid, y_train)

    pred_hybrid_test = clf_hybrid.predict(X_test_hybrid)
    prob_hybrid_test = clf_hybrid.predict_proba(X_test_hybrid)
    res_hybrid_test = evaluate_predictions(y_test, pred_hybrid_test, prob_hybrid_test)

    pred_hybrid_sumeet = clf_hybrid.predict(X_sumeet_hybrid)
    prob_hybrid_sumeet = clf_hybrid.predict_proba(X_sumeet_hybrid)
    res_hybrid_sumeet = evaluate_predictions(y_sumeet, pred_hybrid_sumeet, prob_hybrid_sumeet)

    print(f"[Exp 6 - Hybrid Test] Accuracy: {res_hybrid_test['accuracy']}, Macro F1: {res_hybrid_test['macro_f1']}")
    print(f"[Exp 6 - Hybrid Sumeet] Accuracy: {res_hybrid_sumeet['accuracy']}, Macro F1: {res_hybrid_sumeet['macro_f1']}")

    results["exp6_hybrid_tfidf_embedding"] = {
        "hybrid_test": res_hybrid_test,
        "hybrid_sumeet": res_hybrid_sumeet
    }

    # =========================================================================
    # EXPERIMENT 7: Indian Domain Adaptation Experiment
    # =========================================================================
    print("\n--- EXPERIMENT 7: Indian Domain Adaptation Experiment ---")
    # Split indian_sumeet deterministically: 60% adaptation train (486), 40% held-out test (324)
    from sklearn.model_selection import train_test_split
    ind_train_df, ind_test_df = train_test_split(
        indian_sumeet, test_size=0.40, random_state=42, stratify=indian_sumeet["spendsense_category"]
    )
    print(f"Indian Split: {len(ind_train_df)} train adaptation rows, {len(ind_test_df)} strictly held-out test rows.")

    X_ind_train_emb = get_or_compute_embeddings(model, ind_train_df["composite_text"], "ind_train_composite")
    X_ind_test_emb = get_or_compute_embeddings(model, ind_test_df["composite_text"], "ind_test_composite")
    y_ind_train = ind_train_df["spendsense_category"].values
    y_ind_test = ind_test_df["spendsense_category"].values

    # Model A: Global data ONLY (Zero-shot evaluated on held-out test)
    clf_zero_shot = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_zero_shot.fit(X_train_emb, y_train)
    pred_zs = clf_zero_shot.predict(X_ind_test_emb)
    prob_zs = clf_zero_shot.predict_proba(X_ind_test_emb)
    res_zs = evaluate_predictions(y_ind_test, pred_zs, prob_zs)

    # Model B: Domain Adapted (Global + Indian adaptation train)
    X_adapted_train = np.vstack([X_train_emb, X_ind_train_emb])
    y_adapted_train = np.concatenate([y_train, y_ind_train])

    clf_adapted = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_adapted.fit(X_adapted_train, y_adapted_train)
    pred_adapt = clf_adapted.predict(X_ind_test_emb)
    prob_adapt = clf_adapted.predict_proba(X_ind_test_emb)
    res_adapt = evaluate_predictions(y_ind_test, pred_adapt, prob_adapt)

    # Also evaluate Baseline TF-IDF Zero-Shot vs Adapted on held-out test for direct comparison
    pipe_baseline_zs = FeatureUnion([
        ("word_tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True)),
        ("char_tfidf", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=3, sublinear_tf=True))
    ])
    X_base_tr = pipe_baseline_zs.fit_transform(train_rand["composite_text"])
    X_base_te = pipe_baseline_zs.transform(ind_test_df["composite_text"])
    clf_base_zs = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_base_zs.fit(X_base_tr, y_train)
    pred_base_zs = clf_base_zs.predict(X_base_te)
    res_base_zs = evaluate_predictions(y_ind_test, pred_base_zs)

    # Baseline adapted
    combined_texts = pd.concat([train_rand["composite_text"], ind_train_df["composite_text"]], ignore_index=True)
    combined_labels = np.concatenate([y_train, y_ind_train])
    X_base_adapt_tr = pipe_baseline_zs.fit_transform(combined_texts)
    X_base_adapt_te = pipe_baseline_zs.transform(ind_test_df["composite_text"])
    clf_base_adapt = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_base_adapt.fit(X_base_adapt_tr, combined_labels)
    pred_base_adapt = clf_base_adapt.predict(X_base_adapt_te)
    res_base_adapt = evaluate_predictions(y_ind_test, pred_base_adapt)

    print(f"[Exp 7 - Baseline TF-IDF Zero-Shot on Indian Held-Out]: Acc: {res_base_zs['accuracy']}, Macro F1: {res_base_zs['macro_f1']}")
    print(f"[Exp 7 - Baseline TF-IDF Adapted on Indian Held-Out]:   Acc: {res_base_adapt['accuracy']}, Macro F1: {res_base_adapt['macro_f1']}")
    print(f"[Exp 7 - Semantic Zero-Shot on Indian Held-Out]:         Acc: {res_zs['accuracy']}, Macro F1: {res_zs['macro_f1']}")
    print(f"[Exp 7 - Semantic Adapted on Indian Held-Out]:           Acc: {res_adapt['accuracy']}, Macro F1: {res_adapt['macro_f1']}")

    results["exp7_domain_adaptation"] = {
        "baseline_tfidf_zero_shot": res_base_zs,
        "baseline_tfidf_adapted": res_base_adapt,
        "semantic_zero_shot": res_zs,
        "semantic_adapted": res_adapt,
        "adaptation_samples": len(ind_train_df),
        "held_out_test_samples": len(ind_test_df)
    }

    # =========================================================================
    # EXPERIMENT 8: Inference Latency, Memory & Footprint Benchmarking
    # =========================================================================
    print("\n--- EXPERIMENT 8: Latency, Memory & Model Footprint ---")
    # Single-item inference latency benchmark
    test_samples = test_rand["composite_text"].iloc[:300].tolist()
    
    # Warmup
    for s in test_samples[:10]:
        _ = clf_lr.predict_proba(model.encode([s], normalize_embeddings=True))

    latencies_single_ms = []
    latencies_emb_only_ms = []
    latencies_clf_only_ms = []

    for s in test_samples[10:210]:
        t0 = time.perf_counter()
        emb = model.encode([s], normalize_embeddings=True)
        t1 = time.perf_counter()
        prob = clf_lr.predict_proba(emb)
        t2 = time.perf_counter()

        latencies_single_ms.append((t2 - t0) * 1000.0)
        latencies_emb_only_ms.append((t1 - t0) * 1000.0)
        latencies_clf_only_ms.append((t2 - t1) * 1000.0)

    p50 = round(float(np.percentile(latencies_single_ms, 50)), 2)
    p95 = round(float(np.percentile(latencies_single_ms, 95)), 2)
    p99 = round(float(np.percentile(latencies_single_ms, 99)), 2)
    mean_lat = round(float(np.mean(latencies_single_ms)), 2)

    emb_p50 = round(float(np.percentile(latencies_emb_only_ms, 50)), 2)
    clf_p50 = round(float(np.percentile(latencies_clf_only_ms, 50)), 2)

    # Batch throughput
    t_batch_0 = time.time()
    _ = model.encode(test_samples, batch_size=64, normalize_embeddings=True)
    t_batch_1 = time.time()
    batch_throughput = round(len(test_samples) / (t_batch_1 - t_batch_0), 1)

    # Artifact size on disk
    hf_cache_dir = os.path.expanduser(r"~/.cache/huggingface/hub/models--sentence-transformers--all-MiniLM-L6-v2")
    disk_size_mb = 0.0
    if os.path.exists(hf_cache_dir):
        total_size = sum(os.path.getsize(os.path.join(dirpath, f)) for dirpath, _, filenames in os.walk(hf_cache_dir) for f in filenames)
        disk_size_mb = round(total_size / (1024 * 1024), 2)
    else:
        disk_size_mb = 80.0  # approximate standard size

    benchmark_stats = {
        "model_load_time_sec": model_load_time_sec,
        "peak_ram_mb": model_peak_ram_mb,
        "model_disk_size_mb": disk_size_mb,
        "single_item_latency_p50_ms": p50,
        "single_item_latency_p95_ms": p95,
        "single_item_latency_p99_ms": p99,
        "single_item_latency_mean_ms": mean_lat,
        "embedding_p50_ms": emb_p50,
        "classifier_p50_ms": clf_p50,
        "batch_throughput_samples_per_sec": batch_throughput
    }
    print(f"Single-item Latency (P50): {p50}ms | P95: {p95}ms | P99: {p99}ms")
    print(f"Batch Throughput: {batch_throughput} sentences/sec")
    print(f"Model Disk Footprint: {disk_size_mb} MB")

    results["benchmarks"] = benchmark_stats

    # =========================================================================
    # EXPERIMENT 9: Qualitative Error Mining & Comparison
    # =========================================================================
    print("\n--- EXPERIMENT 9: Qualitative Error Mining ---")
    # Load Phase 6C baseline model to get its exact predictions on indian_sumeet
    import joblib
    baseline_model_path = os.path.join(os.path.dirname(__file__), "..", "models", "baseline_v1", "spend_sense_baseline_model.joblib")
    baseline_pipe = joblib.load(baseline_model_path)
    base_sumeet_preds = baseline_pipe.predict(indian_sumeet["composite_text"])
    base_sumeet_probs = baseline_pipe.predict_proba(indian_sumeet["composite_text"])

    sem_sumeet_preds = pred_lr_sumeet
    sem_sumeet_probs = prob_lr_sumeet

    y_true_s = indian_sumeet["spendsense_category"].values

    base_correct = (base_sumeet_preds == y_true_s)
    sem_correct = (sem_sumeet_preds == y_true_s)

    # 1. Semantic Correct, Baseline Wrong
    sem_wins_idx = np.where(~base_correct & sem_correct)[0]
    # 2. Baseline Correct, Semantic Wrong
    base_wins_idx = np.where(base_correct & ~sem_correct)[0]
    # 3. Both Wrong
    both_wrong_idx = np.where(~base_correct & ~sem_correct)[0]
    # 4. Both Correct
    both_correct_idx = np.where(base_correct & sem_correct)[0]

    def extract_examples(indices, limit=5):
        examples = []
        for idx in indices[:limit]:
            examples.append({
                "index": int(idx),
                "raw_description": str(indian_sumeet["text"].iloc[idx]),
                "normalized_merchant": str(indian_sumeet["normalized_merchant"].iloc[idx]),
                "composite_text": str(indian_sumeet["composite_text"].iloc[idx]),
                "ground_truth": str(y_true_s[idx]),
                "baseline_pred": str(base_sumeet_preds[idx]),
                "baseline_conf": round(float(np.max(base_sumeet_probs[idx])), 4),
                "semantic_pred": str(sem_sumeet_preds[idx]),
                "semantic_conf": round(float(np.max(sem_sumeet_probs[idx])), 4)
            })
        return examples

    error_analysis = {
        "total_indian_samples": len(indian_sumeet),
        "both_correct_count": int(np.sum(both_correct_idx)),
        "semantic_wins_count": int(len(sem_wins_idx)),
        "baseline_wins_count": int(len(base_wins_idx)),
        "both_wrong_count": int(len(both_wrong_idx)),
        "semantic_wins_examples": extract_examples(sem_wins_idx, 6),
        "baseline_wins_examples": extract_examples(base_wins_idx, 6),
        "both_wrong_examples": extract_examples(both_wrong_idx, 6)
    }

    results["error_analysis"] = error_analysis

    # 4. Save comprehensive results JSON
    results_json_path = os.path.join(REPORTS_DIR, "semantic_experiment_results.json")
    with open(results_json_path, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
    print(f"\n[Done] Saved all experiment metrics to {results_json_path}")

if __name__ == "__main__":
    run_semantic_experiments()
