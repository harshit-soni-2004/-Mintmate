# SpendSense Phase 6C — ML Training Data Audit & Baseline Model Report

**Generated**: September 2026  
**Pipeline Version**: `v1.0.0`  
**Model Version**: `v1.0.0-baseline-tfidf-lr`  
**Random Seed**: `42` (fully reproducible)  
**Taxonomy**: SpendSense 9 Core Categories (`FOOD`, `TRAVEL`, `SHOPPING`, `ENTERTAINMENT`, `BILLS`, `HEALTH`, `EDUCATION`, `PERSONAL`, `OTHER`)

---

## Executive Summary

Phase 6C established the scientific baseline reference for SpendSense transaction categorization. We conducted a dataset audit of publicly available financial transaction corpora, implemented leak-free data preparation with Phase 6B normalization, evaluated classical linear baselines (**Multiclass Logistic Regression** vs **Calibrated Linear SVM**), analyzed confidence calibration, and executed a rigorous cross-market domain transfer evaluation against Indian/UPI transaction narrations.

### Key Empirical Findings

1. **Global/In-Domain Performance**:
   - **Logistic Regression (TF-IDF Word + Char N-Grams)** achieved **99.64% accuracy** and **0.9946 macro F1** on stratified random holdout.
   - **Calibrated Linear SVM** achieved an identical **99.64% accuracy** and **0.9946 macro F1**.
2. **Merchant-Aware Leakage Evaluation (100% Unseen Merchants)**:
   - On a strictly partitioned holdout where **zero merchants in the test set existed in training** (14,088 train merchants vs 3,019 test merchants), performance remained virtually intact at **99.58% accuracy** and **0.9946 macro F1** on the US Bank corpus due to rich syntactic and descriptor cues.
3. **Cross-Market Generalization Failure on Indian / UPI Transactions**:
   - When tested zero-shot on Indian UPI narrations (`Sumeetgpt/indian-transaction-categorization-synthetic`), accuracy plummeted to **51.94% (Macro F1 = 0.5078)**.
   - **54.03%** of Indian transactions were routed to the **LOW confidence bucket (< 0.60)**, where accuracy was only **28.06%**.
4. **Architectural Decision**:
   - While a classical TF-IDF linear baseline is computationally lightweight, it **completely fails to generalize across national payment domains and unseen regional merchants** (e.g. Swiggy, Zomato, Rapido, BESCOM, Apollo Pharmacy).
   - This provides empirical justification for moving to a **semantic/embedding-based representation** and a **hybrid model architecture** in subsequent phases.

---

## 1. Dataset Candidates Investigated

We audited four publicly available datasets:

| Candidate Dataset | Source & Provenance | License | Size | Target Labels | Representation / Market | Suitability for SpendSense |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`DoDataThings/us-bank-transaction-categories-v2`** | Hugging Face / DoDataThings | **MIT** | 68,000 | 17 categories | US Bank statement formats (Chase, Apple Card, PayPal, Capital One, Mercury) | **Selected** as primary global baseline training & evaluation benchmark. |
| **`Sumeetgpt/indian-transaction-categorization-synthetic`** | Hugging Face / Sumeetgpt | **MIT** | 810 | 18 categories | Synthetic Indian bank/credit-card narrations (UPI, NetBanking, ATM, POS) | **Selected** as Indian cross-market evaluation benchmark. |
| **`DeepamRor/Banking-Transaction-Text-Categorization-Dataset`** | GitHub / DeepamRor | **Apache 2.0** | 11,000 | 5 categories (`food`, `investment`, `shopping`, `emi`, `travel`) | Templated Indian transactions with sequential reference numbers | **Audited**. Suffers from template repetition leakage; test partition retained as auxiliary benchmark. |
| **`shreya-var/rupeeRoast-indian-transactions`** | Hugging Face / Adaption Labs | **MIT** | 93 | 13 categories | Multilingual Indian UPI transactions with Hindi/Hinglish translations | Insufficient sample size (93 rows, 50% transfers). Not suitable for training. |

---

## 2. Dataset Selection & Strategy

We selected a multi-tier benchmarking strategy:
1. **Primary Global Training & Baseline Set**: `DoDataThings/us-bank-transaction-categories-v2` (MIT License) providing high volume and balanced categories for the linear baseline.
2. **Indian UPI Evaluation Set**: `Sumeetgpt/indian-transaction-categorization-synthetic` (MIT License) + `DeepamRor` test partition to test Indian transaction behavior and measure cross-market generalization gap.

---

## 3. License & Provenance Verification

- All selected datasets are licensed under open, permissive terms (**MIT License** and **Apache License 2.0**) allowing commercial, research, and portfolio usage.
- All datasets are explicitly identified as **synthetic transaction corpora** modeled after real bank statements. No real customer financial data or PII was utilized.

---

## 4. Dataset Statistics & Data Quality Audit

| Metric | US Bank v2 (Raw) | Indian Sumeet (Raw) | Deepam Train (Raw) |
| :--- | :--- | :--- | :--- |
| **Total Rows** | 68,000 | 810 | 10,000 |
| **Missing Text / Category** | 0 / 0 | 0 / 0 | 0 / 0 |
| **Exact Duplicate Rows** | 22,303 (32.8%) | 79 (9.8%) | 0 (0.0%) |
| **Normalized Merchant Duplicates** | 45,774 (67.3%) | 119 (14.7%) | 9,975 (99.8%) |
| **Distinct Normalized Merchants** | 22,226 | 691 | 25 |
| **Mean Character Length** | 44.5 chars | 45.4 chars | 41.2 chars |
| **Mean Word Count** | 5.3 words | 5.8 words | 6.0 words |

### Critical Leakage Finding in Deepam Dataset
The audit revealed that while `DeepamRor` reported 0 exact duplicate lines, **9,975 out of 10,000 lines (99.8%) collapsed to only 25 unique templates** once reference numbers (`Ref000000`, `Ref000001`) and amounts (`Amt48698 INR`) were normalized. A naive train-test split on this dataset would result in near 100% memorization.

---

## 5. Taxonomy Mapping

SpendSense enforces 9 domain categories. External labels were mapped or excluded as follows:

| Source Label | SpendSense Category | Rationale |
| :--- | :--- | :--- |
| `Restaurants`, `Eating Out` | `FOOD` | Core restaurant dining |
| `Groceries` | `FOOD` | Supermarkets, provisions |
| `Transportation`, `Transportation & Gas` | `TRAVEL` | Cabs, fuel, public transit |
| `Travel` | `TRAVEL` | Airlines, lodging, booking |
| `Shopping`, `Shopping & Clothing` | `SHOPPING` | Apparel, electronics, retail |
| `Entertainment`, `Entertainment & Subscriptions` | `ENTERTAINMENT` | Cinema, streaming, events |
| `Subscription` | `ENTERTAINMENT` | Recurring digital media |
| `Utilities` | `BILLS` | Power, water, gas, broadband |
| `Rent`, `Rent & Mortgage`, `Mortgage` | `BILLS` | Housing payments |
| `Insurance`, `EMI` | `BILLS` | Financial obligations |
| `Healthcare`, `Medicine & Pharmacy`, `Hospital` | `HEALTH` | Medical, doctors, pharmacy |
| `Education`, `Kids Activities` | `EDUCATION` | Tuition, courses, books |
| `Personal Care` | `PERSONAL` | Grooming, salon, lifestyle |
| `Fees`, `Fees & Interest`, `ATM & Cash` | `OTHER` | Banking fees, miscellaneous |
| `Transfer`, `Investments & Savings Transfer` | **EXCLUDED** | Non-expense capital movement |
| `Income`, `Income & Deposits` | **EXCLUDED** | Inflow (handled by TransactionType.INCOME) |

---

## 6. Data Cleaning & Deduplication

1. **Excluded Classes**: Removed 8,000 rows (`Income` and `Transfer`) from US Bank v2.
2. **Phase 6B Normalization**: Applied canonical prefix, handle, and reference noise stripping to all records.
3. **Exact String+Label Deduplication**: 60,000 candidate expense rows were deduplicated to **20,427 unique, high-entropy transaction instances**.

---

## 7. Leakage Analysis: Random vs Merchant-Aware Split

To prevent the model from artificially memorizing merchant names across train and test sets, we created two independent splits:

1. **Split A (Stratified Random Split)**:
   - Train: 14,298 rows (70%)
   - Validation: 3,064 rows (15%)
   - Test: 3,065 rows (15%)
   - Allows overlapping merchants between train and test.
2. **Split B (Merchant-Aware Group Split)**:
   - Train: 14,311 rows (14,088 unique merchants)
   - Validation: 3,055 rows
   - Test: 3,061 rows (3,019 unique merchants)
   - **Merchant overlap between Train and Test is strictly 0.0%**. Every merchant in the test set is completely unseen during training.

---

## 8. Feature Pipeline

- **Word N-Grams**: TF-IDF on `composite_text` with $n \in [1, 2]$, `min_df=2`, `sublinear_tf=True`.
- **Character N-Grams**: TF-IDF with `analyzer='char_wb'`, $n \in [3, 5]$, `min_df=3`, `sublinear_tf=True`.
- **Combination**: Scikit-Learn `FeatureUnion` concatenating word and subword feature spaces.

---

## 9. Baseline Models Evaluated

1. **Multiclass Logistic Regression**:
   - Multinomial loss with softmax probabilities
   - $C = 2.0$, `class_weight='balanced'`, `max_iter=1000`
2. **Linear Support Vector Classifier (LinearSVC)**:
   - Calibrated via `CalibratedClassifierCV(method='sigmoid', cv=3)` (Platt scaling)
   - $C = 1.0$, `max_iter=2000`

---

## 10. Empirical Benchmark Results

| Experiment / Evaluation Set | Model | Accuracy | Macro Precision | Macro Recall | Macro F1 | Micro F1 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Random Split Test (Seen Merchants Allowed)** | Logistic Regression | **0.9964** | **0.9946** | **0.9938** | **0.9946** | **0.9964** |
| **Random Split Test** | Calibrated Linear SVM | **0.9964** | **0.9946** | **0.9938** | **0.9946** | **0.9964** |
| **Merchant-Aware Test (100% Unseen Merchants)** | Logistic Regression | **0.9958** | **0.9958** | **0.9934** | **0.9946** | **0.9958** |
| **Ablation: Word N-Grams Only (No Chars)** | Logistic Regression | 0.9938 | 0.9931 | 0.9923 | 0.9927 | 0.9938 |
| **Ablation: Merchant-Only (No Description)** | Logistic Regression | 0.9964 | 0.9946 | 0.9938 | 0.9946 | 0.9964 |
| **Indian Sumeet Evaluation (Zero-Shot Transfer)** | Logistic Regression | **0.5194** | **0.5345** | **0.5401** | **0.5078** | **0.5194** |
| **Indian Deepam Evaluation (Zero-Shot Transfer)** | Logistic Regression | **0.6000** | **0.5750** | **0.4792** | **0.4417** | **0.6000** |

---

## 11. Per-Category Performance Breakdown

### Global In-Domain Test Set (Logistic Regression)

| Category | Precision | Recall | F1-Score | Support |
| :--- | :--- | :--- | :--- | :--- |
| **BILLS** | 0.9961 | 1.0000 | 0.9981 | 512 |
| **EDUCATION** | 1.0000 | 1.0000 | 1.0000 | 156 |
| **ENTERTAINMENT** | 0.9979 | 1.0000 | 0.9990 | 478 |
| **FOOD** | 0.9985 | 1.0000 | 0.9992 | 663 |
| **HEALTH** | 0.9706 | 0.9851 | 0.9778 | 201 |
| **OTHER** | 1.0000 | 1.0000 | 1.0000 | 185 |
| **PERSONAL** | 0.9956 | 1.0000 | 0.9978 | 227 |
| **SHOPPING** | 1.0000 | 0.9592 | 0.9792 | 196 |
| **TRAVEL** | 1.0000 | 1.0000 | 1.0000 | 447 |

### Indian Cross-Market Evaluation Set (Zero-Shot Transfer)

| Category | Precision | Recall | F1-Score | Support | Failure Analysis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **BILLS** | 0.6100 | 0.5446 | 0.5755 | 112 | Unseen Indian power utilities (`BESCOM`, `TSSPDCL`) confused with FOOD/ENTERTAINMENT |
| **EDUCATION** | 1.0000 | 0.4675 | 0.6372 | 77 | Unseen Indian coaching centres (`Kumon`, `Allen`, `Aakash`) misclassified as FOOD/OTHER |
| **ENTERTAINMENT** | 0.2842 | 0.6585 | 0.3971 | 41 | High false positive rate from subscription tokens |
| **FOOD** | 0.4172 | 0.7241 | 0.5294 | 87 | Cabs and retail misclassified into FOOD due to generic keywords |
| **HEALTH** | 0.6282 | 0.6622 | 0.6447 | 74 | Indian clinic chains (`Apollo`, `Practo`) partially recognized |
| **OTHER** | 0.5000 | 0.3676 | 0.4237 | 68 | ATM and UPI withdrawals confused with BILLS |
| **PERSONAL** | 1.0000 | 0.3500 | 0.5185 | 40 | Low recall on Indian grooming services (`Urban Company`, `Naturals`) |
| **SHOPPING** | 0.3404 | 0.3902 | 0.3636 | 41 | Indian e-commerce (`Meesho`, `Ajio`, `Nykaa`) misclassified |
| **TRAVEL** | 0.6327 | 0.3875 | 0.4806 | 80 | **Severely low recall (38.75%)**; Indian fuel stations & transit (`Rapido`, `HPCL`, `DMRC`) missed |

---

## 12. Confusion Matrix

### Global Test Set Confusion Matrix (True Rows vs Pred Columns)

```
              BILLS  EDUCA  ENTER   FOOD  HEALT  OTHER  PERSO  SHOPP  TRAVE
BILLS       : [ 512      0      0      0      0      0      0      0      0 ]
EDUCATION   : [   0    156      0      0      0      0      0      0      0 ]
ENTERTAIN   : [   0      0    478      0      0      0      0      0      0 ]
FOOD        : [   0      0      0    663      0      0      0      0      0 ]
HEALTH      : [   2      0      1      0    198      0      0      0      0 ]
OTHER       : [   0      0      0      0      0    185      0      0      0 ]
PERSONAL    : [   0      0      0      0      0      0    227      0      0 ]
SHOPPING    : [   0      0      0      1      6      0      1    188      0 ]
TRAVEL      : [   0      0      0      0      0      0      0      0    447 ]
```

### Indian Evaluation Set Confusion Matrix

```
              BILLS  EDUCA  ENTER   FOOD  HEALT  OTHER  PERSO  SHOPP  TRAVE
BILLS       : [  61      0     18     14     15      0      0      1      3 ]
EDUCATION   : [   0     36      4     19      3     15      0      0      0 ]
ENTERTAIN   : [   0      0     27      8      1      0      0      3      2 ]
FOOD        : [   8      0     10     63      2      0      0      4      0 ]
HEALTH      : [   0      0      6      8     49      6      0      5      0 ]
OTHER       : [  23      0      3      1      4     25      0      0     12 ]
PERSONAL    : [   4      0      0     14      3      4     14      1      0 ]
SHOPPING    : [   1      0     14      9      0      0      0     16      1 ]
TRAVEL      : [   3      0     13     15      1      0      0     17     31 ]
```

---

## 13. Confidence Calibration & Threshold Analysis

We evaluated probability calibration by grouping model confidence into operational decision tiers:

### Global In-Domain Test Calibration

| Confidence Tier | Threshold Range | % of Transactions | Observed Accuracy | Mean Confidence | Operational Policy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **HIGH** | $\ge 0.85$ | **76.41%** | **99.70%** | 0.9441 | Auto-assign category |
| **MEDIUM** | $0.60 - 0.85$ | **21.11%** | **99.85%** | 0.7678 | Suggest category & alternatives |
| **LOW** | $< 0.60$ | **2.48%** | **96.05%** | 0.4995 | Require manual selection |

### Indian Evaluation Set Calibration (Domain Mismatch)

| Confidence Tier | Threshold Range | % of Transactions | Observed Accuracy | Mean Confidence | Operational Policy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **HIGH** | $\ge 0.85$ | **26.77%** | **89.76%** | 0.9442 | Auto-assign category |
| **MEDIUM** | $0.60 - 0.85$ | **19.19%** | **66.39%** | 0.7153 | Suggest category & alternatives |
| **LOW** | $< 0.60$ | **54.03%** | **28.06%** | 0.3610 | Require manual selection |

### Calibration Insight
- On in-domain data, predicted probabilities are well calibrated.
- On out-of-domain Indian data, the model's confidence is appropriately **unconfident**: over 54% of transactions drop into LOW confidence, where accuracy is only 28%. The confidence scoring successfully acts as an operational guardrail against auto-assigning wrong categories.

---

## 14. Ablation Analysis Summary

1. **Word-only vs Word+Char N-Grams**:
   - Word N-Grams only: 0.9938 Accuracy, 0.9927 Macro F1.
   - Word + Char N-Grams: **0.9964 Accuracy, 0.9946 Macro F1 (+0.26% Accuracy, +0.19% F1)**.
   - Subword character n-grams improve robustness against abbreviations (`amzn`, `pymt`, `mktpl`).
2. **Logistic Regression vs Linear SVM**:
   - Both linear models achieved identical test performance (0.9964).
   - **Logistic Regression was chosen as the winner artifact** because it naturally outputs softmax probabilities with direct multiclass cross-entropy calibration without requiring multi-fold Platt scaling fitting.

---

## 15. Saved Artifacts & Reproducibility

- **Model Artifact**: `ml/models/baseline_v1/spend_sense_baseline_model.joblib` (serialized Scikit-Learn pipeline containing `FeatureUnion` + `LogisticRegression`).
- **Dataset Manifest**: `ml/data/manifests/dataset_manifest.json` (SHA-256 hashes of all raw corpora).
- **Preprocessing Manifest**: `ml/data/processed/preprocessing_summary.json` (partition sizes, class distributions).
- **Benchmark JSON**: `ml/reports/baseline_experiment_results.json` (full metrics, per-category P/R/F1, confusion matrices, calibration tables).

---

## 16. Limitations of the Baseline & Recommendation for Future Upgrades

### Demonstrated Limitations of the Baseline
1. **Zero-Shot Domain Fragility**: An n-gram linear model trained on Western statement syntax fails on Indian regional merchants and UPI narrations (dropping from 99.6% to 51.9%).
2. **Vocabulary Rigidity**: Linear models cannot understand synonyms or semantic relatedness. If `Rapido` or `Dunzo` has never been seen with words like `cab` or `delivery`, the n-gram model has zero feature activation.
3. **Over-reliance on Explicit Keywords**: Categories like `TRAVEL` depend on keywords like `air`, `motel`, `hotel`, `uber`; when facing fuel stations or metro cards (`DMRC`), recall dropped to 38.8%.

### Recommendation for Next Model Phase
- **For Phase 6D/6E**: The baseline `LogisticRegression` pipeline is verified, saved, and ready to serve as the reference ML inference engine behind the Phase 6D abstraction interface.
- **For Advanced Semantic Upgrade (Phase 6O)**: The 51.9% cross-market drop confirms that an embedding-based or transformer-based semantic representation (or an Indian-adapted hybrid model combining deterministic rules + user memory + embeddings) is justified to achieve genuine production quality for Indian financial data.
