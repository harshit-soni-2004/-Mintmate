package com.spendsense.categorization.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure Java WordPiece Tokenizer compatible with BERT / all-MiniLM-L6-v2 vocabulary.
 * Thread-safe and free from native dependencies.
 */
public class BertTokenizer {

    public static final String PAD_TOKEN = "[PAD]";
    public static final String UNK_TOKEN = "[UNK]";
    public static final String CLS_TOKEN = "[CLS]";
    public static final String SEP_TOKEN = "[SEP]";

    private final Map<String, Long> vocab;
    private final long padId;
    private final long unkId;
    private final long clsId;
    private final long sepId;

    public record TokenizerResult(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {}

    public BertTokenizer(InputStream vocabInputStream) throws IOException {
        Map<String, Long> map = new HashMap<>(32000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(vocabInputStream, StandardCharsets.UTF_8))) {
            String line;
            long index = 0;
            while ((line = reader.readLine()) != null) {
                map.put(line.trim(), index);
                index++;
            }
        }
        this.vocab = Collections.unmodifiableMap(map);
        this.padId = vocab.getOrDefault(PAD_TOKEN, 0L);
        this.unkId = vocab.getOrDefault(UNK_TOKEN, 100L);
        this.clsId = vocab.getOrDefault(CLS_TOKEN, 101L);
        this.sepId = vocab.getOrDefault(SEP_TOKEN, 102L);
    }

    public BertTokenizer(Map<String, Long> vocab) {
        this.vocab = Collections.unmodifiableMap(new HashMap<>(vocab));
        this.padId = this.vocab.getOrDefault(PAD_TOKEN, 0L);
        this.unkId = this.vocab.getOrDefault(UNK_TOKEN, 100L);
        this.clsId = this.vocab.getOrDefault(CLS_TOKEN, 101L);
        this.sepId = this.vocab.getOrDefault(SEP_TOKEN, 102L);
    }

    /**
     * Encodes raw text into padded/truncated token arrays suitable for ONNX transformer input.
     */
    public TokenizerResult tokenize(String text, int maxSeqLength) {
        if (text == null) {
            text = "";
        }

        List<Long> tokenIds = new ArrayList<>();
        tokenIds.add(clsId);

        List<String> words = basicTokenize(text);
        for (String word : words) {
            wordPieceTokenize(word, tokenIds);
            if (tokenIds.size() >= maxSeqLength - 1) {
                break;
            }
        }

        // Truncate to leave room for [SEP]
        while (tokenIds.size() > maxSeqLength - 1) {
            tokenIds.remove(tokenIds.size() - 1);
        }
        tokenIds.add(sepId);

        int actualLen = tokenIds.size();
        long[] inputIds = new long[maxSeqLength];
        long[] attentionMask = new long[maxSeqLength];
        long[] tokenTypeIds = new long[maxSeqLength];

        for (int i = 0; i < maxSeqLength; i++) {
            if (i < actualLen) {
                inputIds[i] = tokenIds.get(i);
                attentionMask[i] = 1L;
            } else {
                inputIds[i] = padId;
                attentionMask[i] = 0L;
            }
            tokenTypeIds[i] = 0L;
        }

        return new TokenizerResult(inputIds, attentionMask, tokenTypeIds);
    }

    private List<String> basicTokenize(String text) {
        String clean = text.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (isPunctuation(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private void wordPieceTokenize(String word, List<Long> outputTokens) {
        if (word.length() > 100) {
            outputTokens.add(unkId);
            return;
        }

        boolean isBad = false;
        int start = 0;
        List<Long> subTokens = new ArrayList<>();

        while (start < word.length()) {
            int end = word.length();
            Long curSubTokenId = null;

            while (start < end) {
                String subStr = (start == 0)
                        ? word.substring(start, end)
                        : "##" + word.substring(start, end);

                if (vocab.containsKey(subStr)) {
                    curSubTokenId = vocab.get(subStr);
                    break;
                }
                end--;
            }

            if (curSubTokenId == null) {
                isBad = true;
                break;
            }

            subTokens.add(curSubTokenId);
            start = end;
        }

        if (isBad) {
            outputTokens.add(unkId);
        } else {
            outputTokens.addAll(subTokens);
        }
    }

    private boolean isPunctuation(char c) {
        // Standard ASCII punctuation + common symbols
        int type = Character.getType(c);
        return (c >= 33 && c <= 47) ||
                (c >= 58 && c <= 64) ||
                (c >= 91 && c <= 96) ||
                (c >= 123 && c <= 126) ||
                type == Character.CONNECTOR_PUNCTUATION ||
                type == Character.DASH_PUNCTUATION ||
                type == Character.START_PUNCTUATION ||
                type == Character.END_PUNCTUATION ||
                type == Character.INITIAL_QUOTE_PUNCTUATION ||
                type == Character.FINAL_QUOTE_PUNCTUATION ||
                type == Character.OTHER_PUNCTUATION;
    }

    public int getVocabSize() {
        return vocab.size();
    }
}
