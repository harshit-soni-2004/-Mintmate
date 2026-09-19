import os
import torch
from transformers import AutoModel, AutoTokenizer

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "semantic_v1")
JAVA_RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "main", "resources", "model")

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(JAVA_RESOURCES_DIR, exist_ok=True)

class MiniLMWithMeanPoolingAndNorm(torch.nn.Module):
    def __init__(self, base_model):
        super().__init__()
        self.base_model = base_model

    def forward(self, input_ids, attention_mask, token_type_ids):
        outputs = self.base_model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids
        )
        token_embeddings = outputs.last_hidden_state  # [batch, seq_len, 384]
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, dim=1)
        sum_mask = torch.clamp(input_mask_expanded.sum(dim=1), min=1e-9)
        mean_pooled = sum_embeddings / sum_mask
        # L2 normalize
        normalized_embeddings = torch.nn.functional.normalize(mean_pooled, p=2, dim=1)
        return normalized_embeddings

def export():
    print("Loading MiniLM model and tokenizer...")
    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    base_model = AutoModel.from_pretrained(model_name)
    tokenizer = AutoTokenizer.from_pretrained(model_name)

    wrapper = MiniLMWithMeanPoolingAndNorm(base_model)
    wrapper.eval()

    dummy_text = "sample transaction merchant description"
    inputs = tokenizer(dummy_text, padding="max_length", max_length=64, return_tensors="pt")

    onnx_path = os.path.join(MODELS_DIR, "minilm_l6_v2.onnx")
    java_onnx_path = os.path.join(JAVA_RESOURCES_DIR, "minilm_l6_v2.onnx")

    print(f"Exporting ONNX to {onnx_path}...")
    torch.onnx.export(
        wrapper,
        (inputs["input_ids"], inputs["attention_mask"], inputs["token_type_ids"]),
        onnx_path,
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["embeddings"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence_length"},
            "attention_mask": {0: "batch_size", 1: "sequence_length"},
            "token_type_ids": {0: "batch_size", 1: "sequence_length"},
            "embeddings": {0: "batch_size"}
        },
        opset_version=14,
        do_constant_folding=True
    )
    print(f"ONNX export complete: size = {os.path.getsize(onnx_path)/(1024*1024):.2f} MB")

    # Also save vocab.txt into java resources for the tokenizer
    vocab_src = os.path.join(os.path.expanduser("~/.cache/huggingface/hub/models--sentence-transformers--all-MiniLM-L6-v2/snapshots/1110a243fdf4706b3f48f1d95db1a4f5529b4d41/vocab.txt"))
    if not os.path.exists(vocab_src):
        # export from tokenizer
        vocab_dict = tokenizer.get_vocab()
        # sorted by id
        inv_vocab = sorted(vocab_dict.items(), key=lambda x: x[1])
        with open(os.path.join(MODELS_DIR, "minilm_vocab.txt"), "w", encoding="utf-8") as f:
            for word, _ in inv_vocab:
                f.write(word + "\n")
    else:
        import shutil
        shutil.copyfile(vocab_src, os.path.join(MODELS_DIR, "minilm_vocab.txt"))
        shutil.copyfile(vocab_src, os.path.join(JAVA_RESOURCES_DIR, "minilm_vocab.txt"))

    print("Vocab saved.")

if __name__ == "__main__":
    export()
