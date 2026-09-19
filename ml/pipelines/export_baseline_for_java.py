import os
import json
import joblib
import numpy as np

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "baseline_v1")
JAVA_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "main", "resources", "model")
JAVA_TEST_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "test", "resources", "model")

def export():
    os.makedirs(JAVA_RESOURCES_DIR, exist_ok=True)
    os.makedirs(JAVA_TEST_RESOURCES_DIR, exist_ok=True)

    joblib_path = os.path.join(MODELS_DIR, "spend_sense_baseline_model.joblib")
    if not os.path.exists(joblib_path):
        raise FileNotFoundError(f"Missing joblib model at {joblib_path}")

    print(f"Loading pipeline from {joblib_path}...")
    pipe = joblib.load(joblib_path)

    word_vec = pipe.named_steps["features"].transformer_list[0][1]
    char_vec = pipe.named_steps["features"].transformer_list[1][1]
    clf = pipe.named_steps["clf"]

    classes = list(clf.classes_)

    artifact = {
        "metadata": {
            "modelVersion": "v1.0.0-baseline-tfidf-lr",
            "datasetVersion": "us_bank_categories_v2_processed_v1",
            "preprocessingVersion": "v1.0.0",
            "algorithm": "Multiclass Logistic Regression (Balanced, C=2.0) with Word+Char TF-IDF",
            "trainedAt": "2026-09-13T17:34:00Z",
            "classes": classes
        },
        "word_vocabulary": {k: int(v) for k, v in word_vec.vocabulary_.items()},
        "word_idf": [float(x) for x in word_vec.idf_],
        "char_vocabulary": {k: int(v) for k, v in char_vec.vocabulary_.items()},
        "char_idf": [float(x) for x in char_vec.idf_],
        "intercept": [float(x) for x in clf.intercept_],
        "coefficients": [[float(x) for x in row] for row in clf.coef_]
    }

    # Write to ML models dir
    ml_artifact_path = os.path.join(MODELS_DIR, "baseline_model_artifact.json")
    with open(ml_artifact_path, "w", encoding="utf-8") as f:
        json.dump(artifact, f)
    print(f"Saved ML artifact to {ml_artifact_path} ({os.path.getsize(ml_artifact_path) / (1024*1024):.2f} MB)")

    # Write to Spring Boot resources
    java_model_path = os.path.join(JAVA_RESOURCES_DIR, "baseline_model_v1.json")
    with open(java_model_path, "w", encoding="utf-8") as f:
        json.dump(artifact, f)
    print(f"Saved Java model artifact to {java_model_path} ({os.path.getsize(java_model_path) / (1024*1024):.2f} MB)")

    # Generate test verification vectors
    test_cases = [
        "starbucks coffee seattle",
        "uber trip help.uber.com",
        "walmart supercenter store",
        "netflix streaming sub",
        "duke energy electric web payment",
        "mayo clinic medical pmt web",
        "coursera online course subscription",
        "great clips hair salon haircut",
        "bank fee monthly service charge",
        "upi swiggy 123456 okhdfcbank",
        "upi zomato pay 789012 icici",
        "paytm billpay electricity",
        "irctc railway ticket new delhi",
        "upi p sharma merchant okaxis",
        "xyzqwk 987234"
    ]

    test_vectors = []
    for text in test_cases:
        pred = pipe.predict([text])[0]
        probs = pipe.predict_proba([text])[0]
        prob_map = {classes[i]: float(probs[i]) for i in range(len(classes))}
        test_vectors.append({
            "inputText": text,
            "expectedCategory": pred,
            "confidence": float(np.max(probs)),
            "probabilities": prob_map
        })

    test_vectors_path = os.path.join(JAVA_TEST_RESOURCES_DIR, "baseline_test_vectors.json")
    with open(test_vectors_path, "w", encoding="utf-8") as f:
        json.dump(test_vectors, f, indent=2)
    print(f"Saved test verification vectors to {test_vectors_path}")

if __name__ == "__main__":
    export()
