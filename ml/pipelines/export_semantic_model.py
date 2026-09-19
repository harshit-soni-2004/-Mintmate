import os
import sys
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

CATEGORIES = [
    "BILLS", "EDUCATION", "ENTERTAINMENT", "FOOD",
    "HEALTH", "OTHER", "PERSONAL", "SHOPPING", "TRAVEL"
]

def main():
    print("Loading datasets and cached embeddings...")
    train_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_train.csv"))
    test_rand = pd.read_csv(os.path.join(PROCESSED_DIR, "split_random_test.csv"))
    indian_sumeet = pd.read_csv(os.path.join(PROCESSED_DIR, "eval_dataset_indian_sumeet.csv"))

    X_train_emb = np.load(os.path.join(EMBEDDINGS_DIR, "train_rand_composite.npy"))
    X_test_emb = np.load(os.path.join(EMBEDDINGS_DIR, "test_rand_composite.npy"))
    X_sumeet_emb = np.load(os.path.join(EMBEDDINGS_DIR, "sumeet_composite.npy"))

    y_train = train_rand["spendsense_category"].values
    y_test = test_rand["spendsense_category"].values
    y_sumeet = indian_sumeet["spendsense_category"].values

    print("Fitting Exp 1: Semantic Logistic Regression (C=2.0, balanced)...")
    clf_sem = LogisticRegression(C=2.0, max_iter=1000, class_weight="balanced", random_state=42)
    clf_sem.fit(X_train_emb, y_train)

    train_acc = clf_sem.score(X_train_emb, y_train)
    test_acc = clf_sem.score(X_test_emb, y_test)
    sumeet_acc = clf_sem.score(X_sumeet_emb, y_sumeet)
    print(f"Exp 1 Results -> Train Acc: {train_acc:.4f}, Test Acc: {test_acc:.4f}, Sumeet Acc: {sumeet_acc:.4f}")

    # Export pure semantic artifact
    sem_classes = list(clf_sem.classes_)
    sem_artifact = {
        "metadata": {
            "modelVersion": "v1.0.0-semantic-minilm-l6",
            "baseModel": "sentence-transformers/all-MiniLM-L6-v2",
            "embeddingDimension": 384,
            "algorithm": "Multiclass Logistic Regression (C=2.0, balanced) on all-MiniLM-L6-v2 embeddings",
            "classes": sem_classes,
            "trainedAt": "2026-09-19T23:20:00Z"
        },
        "intercept": [float(x) for x in clf_sem.intercept_],
        "coefficients": [[float(x) for x in row] for row in clf_sem.coef_]
    }

    sem_artifact_path = os.path.join(MODELS_DIR, "semantic_classifier_v1.json")
    with open(sem_artifact_path, "w", encoding="utf-8") as f:
        json.dump(sem_artifact, f, indent=2)
    print(f"Saved semantic classifier artifact to {sem_artifact_path}")

    java_sem_path = os.path.join(JAVA_RESOURCES_DIR, "semantic_classifier_v1.json")
    with open(java_sem_path, "w", encoding="utf-8") as f:
        json.dump(sem_artifact, f, indent=2)
    print(f"Saved to Java resources: {java_sem_path}")

    # Test vectors for Java testing
    sample_texts = [
        "semester fee via Udemy",
        "UPI/Barbeque Nation/arjun.265@oksbi/Payment/KOTAK",
        "starbucks coffee seattle",
        "uber trip ride",
        "netflix entertainment stream",
        "walmart supercenter groceries"
    ]
    from sentence_transformers import SentenceTransformer
    st_model = SentenceTransformer("all-MiniLM-L6-v2", device="cpu")
    sample_embs = st_model.encode(sample_texts, normalize_embeddings=True)
    sample_probs = clf_sem.predict_proba(sample_embs)
    sample_preds = clf_sem.predict(sample_embs)

    test_vectors = []
    for i, text in enumerate(sample_texts):
        prob_dict = {sem_classes[c]: float(sample_probs[i][c]) for c in range(len(sem_classes))}
        test_vectors.append({
            "inputText": text,
            "embedding": [float(x) for x in sample_embs[i]],
            "expectedCategory": str(sample_preds[i]),
            "confidence": float(np.max(sample_probs[i])),
            "probabilities": prob_dict
        })

    tv_path = os.path.join(JAVA_TEST_RESOURCES_DIR, "semantic_test_vectors.json")
    with open(tv_path, "w", encoding="utf-8") as f:
        json.dump(test_vectors, f, indent=2)
    print(f"Saved semantic test vectors to {tv_path}")

if __name__ == "__main__":
    main()
