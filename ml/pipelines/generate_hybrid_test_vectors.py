import os
import json
import numpy as np
import joblib
from sentence_transformers import SentenceTransformer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion
from scipy.sparse import hstack as sp_hstack, csr_matrix

JAVA_TEST_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "test", "resources", "model")
HYBRID_JSON_PATH = os.path.join(os.path.dirname(__file__), "..", "models", "semantic_v1", "hybrid_model_v1.json")

with open(HYBRID_JSON_PATH, "r", encoding="utf-8") as f:
    artifact = json.load(f)

classes = artifact["metadata"]["classes"]
word_vocab = artifact["word_vocabulary"]
word_idf = artifact["word_idf"]
char_vocab = artifact["char_vocabulary"]
char_idf = artifact["char_idf"]
intercept = np.array(artifact["intercept"])
coef = np.array(artifact["coefficients"])

# Verify manual scoring math on sample texts
model = SentenceTransformer("all-MiniLM-L6-v2", device="cpu")

sample_texts = [
    "semester fee via Udemy",
    "UPI/Barbeque Nation/arjun.265@oksbi/Payment/KOTAK",
    "starbucks coffee seattle",
    "uber trip help.uber.com",
    "netflix streaming sub",
    "walmart supercenter groceries",
    "duke energy electric web payment",
    "mayo clinic medical pmt web",
    "great clips hair salon haircut"
]

embs = model.encode(sample_texts, normalize_embeddings=True)

test_vectors = []
for i, text in enumerate(sample_texts):
    emb = embs[i]
    test_vectors.append({
        "inputText": text,
        "embedding": [float(x) for x in emb]
    })

tv_path = os.path.join(JAVA_TEST_RESOURCES_DIR, "hybrid_test_vectors.json")
with open(tv_path, "w", encoding="utf-8") as f:
    json.dump(test_vectors, f, indent=2)

print(f"Saved hybrid test vectors to {tv_path}")
