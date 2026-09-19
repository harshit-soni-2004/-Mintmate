import os
import json
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion
from scipy.sparse import hstack as sp_hstack, csr_matrix

PROCESSED_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "processed")
EMBEDDINGS_DIR = os.path.join(PROCESSED_DIR, "embeddings")
MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "semantic_v1")
JAVA_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "main", "resources", "model")
JAVA_TEST_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "test", "resources", "model")

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(JAVA_RESOURCES_DIR, exist_ok=True)
os.makedirs(JAVA_TEST_RESOURCES_DIR, exist_ok=True)

def main():
    print("Loading data for hybrid model export...")
    train_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_train.csv"))
    test_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_test.csv"))
    indian_sumeet = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_sumeet.csv"))

    X_train_emb = np.load(os.path.join(EMBEDDINGS_DIR, "train_rand_composite.npy"))
    X_test_emb = np.load(os.path.join(EMBEDDINGS_DIR, "test_rand_composite.npy"))
    X_sumeet_emb = np.load(os.path.join(EMBEDDINGS_DIR, "sumeet_composite.npy"))

    y_train = train_rand["spendsense_category"].values
    y_test = test_rand["spendsense_category"].values
    y_sumeet = indian_sumeet["spendsense_category"].values

    print("Fitting TF-IDF FeatureUnion (Phase 6F Exp 6 specs)...")
    word_vec = TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True, max_features=5000)
    char_vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=3, sublinear_tf=True, max_features=5000)
    
    tfidf_feat = FeatureUnion([
        ("word_tfidf", word_vec),
        ("char_tfidf", char_vec)
    ])

    tfidf_train = tfidf_feat.fit_transform(train_rand["composite_text"])
    tfidf_test = tfidf_feat.transform(test_rand["composite_text"])
    tfidf_sumeet = tfidf_feat.transform(indian_sumeet["composite_text"])

    X_train_hybrid = sp_hstack([csr_matrix(X_train_emb), tfidf_train])
    X_test_hybrid = sp_hstack([csr_matrix(X_test_emb), tfidf_test])
    X_sumeet_hybrid = sp_hstack([csr_matrix(X_sumeet_emb), tfidf_sumeet])

    print("Training Hybrid Logistic Regression (C=2.0, balanced)...")
    clf_hybrid = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_hybrid.fit(X_train_hybrid, y_train)

    train_acc = clf_hybrid.score(X_train_hybrid, y_train)
    test_acc = clf_hybrid.score(X_test_hybrid, y_test)
    sumeet_acc = clf_hybrid.score(X_sumeet_hybrid, y_sumeet)
    print(f"Hybrid Results -> Train Acc: {train_acc:.4f}, Test Acc: {test_acc:.4f}, Sumeet Acc: {sumeet_acc:.4f}")

    classes = list(clf_hybrid.classes_)
    fitted_word_vec = tfidf_feat.transformer_list[0][1]
    fitted_char_vec = tfidf_feat.transformer_list[1][1]

    hybrid_artifact = {
        "metadata": {
            "modelVersion": "v1.0.0-hybrid-minilm-tfidf",
            "baseModel": "sentence-transformers/all-MiniLM-L6-v2 + TF-IDF (word+char)",
            "embeddingDimension": 384,
            "algorithm": "Hybrid Logistic Regression (C=2.0, balanced) combining 384-d dense MiniLM embeddings with sparse TF-IDF n-grams",
            "classes": classes,
            "trainedAt": "2026-09-19T23:30:00Z"
        },
        "embedding_dim": 384,
        "word_vocabulary": {k: int(v) for k, v in fitted_word_vec.vocabulary_.items()},
        "word_idf": [float(x) for x in fitted_word_vec.idf_],
        "char_vocabulary": {k: int(v) for k, v in fitted_char_vec.vocabulary_.items()},
        "char_idf": [float(x) for x in fitted_char_vec.idf_],
        "intercept": [float(x) for x in clf_hybrid.intercept_],
        "coefficients": [[float(x) for x in row] for row in clf_hybrid.coef_]
    }

    hybrid_path = os.path.join(MODELS_DIR, "hybrid_model_v1.json")
    with open(hybrid_path, "w", encoding="utf-8") as f:
        json.dump(hybrid_artifact, f)
    print(f"Saved hybrid artifact to {hybrid_path} ({os.path.getsize(hybrid_path)/(1024*1024):.2f} MB)")

    java_hybrid_path = os.path.join(JAVA_RESOURCES_DIR, "hybrid_model_v1.json")
    with open(java_hybrid_path, "w", encoding="utf-8") as f:
        json.dump(hybrid_artifact, f)
    print(f"Saved hybrid artifact to Java resources: {java_hybrid_path}")

if __name__ == "__main__":
    main()
