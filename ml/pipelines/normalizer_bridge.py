import re
from typing import NamedTuple, List, Tuple

class NormalizedTransaction(NamedTuple):
    raw_merchant: str
    raw_description: str
    normalized_merchant: str
    normalized_description: str
    composite_text: str
    tokens: List[str]

class TransactionNormalizer:
    """
    Python implementation of the SpendSense Phase 6B TransactionNormalizer.
    Guarantees strict parity between backend runtime normalization and offline ML training.
    """
    PAYMENT_PREFIX_PATTERN = re.compile(
        r'^(?:UPI|POS|NEFT|IMPS|RTGS|ACH|ATM)(?:[/_\-\s]+(?:DR|CR|P2A|P2U|WDL))?[/_\-\s]+',
        re.IGNORECASE
    )
    SIGN_PREFIX_PATTERN = re.compile(
        r'^\s*\[(?:debit|credit)\]\s*',
        re.IGNORECASE
    )
    VPA_HANDLE_PATTERN = re.compile(r'@[a-zA-Z0-9.\-_]+')
    SEPARATOR_PATTERN = re.compile(r'[*\/\\_\-:#~|,;]+')
    CLEAN_CHARS_PATTERN = re.compile(r"[^a-z0-9&'\s]")

    NOISE_TAGS = {'ref', 'refno', 'txnid', 'txn', 'rrn', 'utr', 'dr', 'cr'}
    BOILERPLATE_MERCHANT_SUFFIXES = {
        'in', 'order', 'orders', 'online', 'pay', 'pvt', 'ltd', 'limited', 'corp', 'inc', 'llp'
    }

    def normalize(self, raw_merchant: str, raw_description: str = "") -> NormalizedTransaction:
        clean_raw_merchant = (raw_merchant or "").strip()
        clean_raw_description = (raw_description or "").strip()

        if not clean_raw_merchant:
            norm_desc = self.normalize_text(clean_raw_description) if clean_raw_description else ""
            comp = norm_desc if norm_desc else "unknown"
            return NormalizedTransaction(
                raw_merchant="",
                raw_description=clean_raw_description,
                normalized_merchant="unknown",
                normalized_description=norm_desc,
                composite_text=comp,
                tokens=[]
            )

        merchant_tokens = self._clean_and_tokenize(clean_raw_merchant, is_merchant=True)
        if not merchant_tokens:
            fallback = clean_raw_merchant.lower()
            norm_desc = self.normalize_text(clean_raw_description) if clean_raw_description else ""
            return NormalizedTransaction(
                raw_merchant=clean_raw_merchant,
                raw_description=clean_raw_description,
                normalized_merchant=fallback,
                normalized_description=norm_desc,
                composite_text=fallback,
                tokens=[fallback]
            )

        normalized_merchant = self._derive_normalized_merchant(merchant_tokens)
        description_tokens = self._clean_and_tokenize(clean_raw_description, is_merchant=False)
        normalized_description = " ".join(description_tokens)

        all_tokens = list(merchant_tokens) + description_tokens
        composite_text = " ".join(all_tokens)

        return NormalizedTransaction(
            raw_merchant=clean_raw_merchant,
            raw_description=clean_raw_description,
            normalized_merchant=normalized_merchant,
            normalized_description=normalized_description,
            composite_text=composite_text,
            tokens=merchant_tokens
        )

    def normalize_merchant(self, raw_merchant: str) -> str:
        if not raw_merchant or not raw_merchant.strip():
            return "unknown"
        return self.normalize(raw_merchant).normalized_merchant

    def normalize_text(self, text: str) -> str:
        if not text or not text.strip():
            return ""
        tokens = self._clean_and_tokenize(text, is_merchant=False)
        return " ".join(tokens)

    def _clean_and_tokenize(self, text: str, is_merchant: bool) -> List[str]:
        if not text or not text.strip():
            return []

        cleaned = text.strip()
        cleaned = self.SIGN_PREFIX_PATTERN.sub('', cleaned)

        if is_merchant:
            cleaned = self.PAYMENT_PREFIX_PATTERN.sub('', cleaned)

        cleaned = self.VPA_HANDLE_PATTERN.sub(' ', cleaned)
        cleaned = self.SEPARATOR_PATTERN.sub(' ', cleaned)
        cleaned = cleaned.lower()
        cleaned = self.CLEAN_CHARS_PATTERN.sub(' ', cleaned)

        raw_tokens = cleaned.split()
        valid_tokens = []
        for token in raw_tokens:
            token = token.strip()
            if not token:
                continue
            if not self._is_noise_token(token):
                valid_tokens.add(token) if hasattr(valid_tokens, 'add') else valid_tokens.append(token)

        return valid_tokens

    def _is_noise_token(self, token: str) -> bool:
        if token in self.NOISE_TAGS:
            return True
        if len(token) >= 3 and token.isdigit():
            return True
        if len(token) >= 5 and any(c.isalpha() for c in token) and any(c.isdigit() for c in token):
            return True
        return False

    def _derive_normalized_merchant(self, tokens: List[str]) -> str:
        if not tokens:
            return "unknown"
        if len(tokens) == 1:
            return tokens[0]

        res = list(tokens)
        while len(res) > 1:
            if res[-1] in self.BOILERPLATE_MERCHANT_SUFFIXES:
                res.pop()
            else:
                break
        return " ".join(res)
