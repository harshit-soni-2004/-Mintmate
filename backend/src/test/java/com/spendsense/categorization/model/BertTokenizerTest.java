package com.spendsense.categorization.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BertTokenizer WordPiece Tests")
class BertTokenizerTest {

    private BertTokenizer tokenizer;

    @BeforeEach
    void setUp() throws IOException {
        ClassPathResource res = new ClassPathResource("model/minilm_vocab.txt");
        try (InputStream is = res.getInputStream()) {
            tokenizer = new BertTokenizer(is);
        }
    }

    @Test
    @DisplayName("Vocabulary loads successfully with expected standard tokens")
    void testVocabLoad() {
        assertThat(tokenizer.getVocabSize()).isGreaterThanOrEqualTo(30522);
    }

    @Test
    @DisplayName("Tokenizes simple sentence with CLS, SEP, and padding mask")
    void testBasicTokenization() {
        BertTokenizer.TokenizerResult result = tokenizer.tokenize("starbucks coffee seattle", 16);

        assertThat(result.inputIds()).hasSize(16);
        assertThat(result.attentionMask()).hasSize(16);
        assertThat(result.tokenTypeIds()).hasSize(16);

        // First token is [CLS] (id 101)
        assertThat(result.inputIds()[0]).isEqualTo(101L);
        assertThat(result.attentionMask()[0]).isEqualTo(1L);

        // Tokens for starbucks, coffee, seattle followed by [SEP] (id 102)
        // Ensure non-zero real tokens
        assertThat(result.inputIds()[1]).isGreaterThan(0L);
        assertThat(result.inputIds()[2]).isGreaterThan(0L);
        assertThat(result.inputIds()[3]).isGreaterThan(0L);

        // Pad tokens have attention_mask = 0
        assertThat(result.attentionMask()[15]).isEqualTo(0L);
        assertThat(result.inputIds()[15]).isEqualTo(0L);
    }

    @Test
    @DisplayName("Handles unknown words gracefully using subwords or [UNK]")
    void testUnknownWordPiece() {
        BertTokenizer.TokenizerResult result = tokenizer.tokenize("zyxwvutsrqponmlkjihgfedcba", 16);
        assertThat(result.inputIds()[0]).isEqualTo(101L);
        assertThat(result.attentionMask()[0]).isEqualTo(1L);
    }

    @Test
    @DisplayName("Handles null and empty string safely")
    void testNullAndEmpty() {
        BertTokenizer.TokenizerResult nullResult = tokenizer.tokenize(null, 8);
        assertThat(nullResult.inputIds()[0]).isEqualTo(101L);
        assertThat(nullResult.inputIds()[1]).isEqualTo(102L);
        assertThat(nullResult.attentionMask()[0]).isEqualTo(1L);
        assertThat(nullResult.attentionMask()[1]).isEqualTo(1L);
        assertThat(nullResult.attentionMask()[2]).isEqualTo(0L);

        BertTokenizer.TokenizerResult emptyResult = tokenizer.tokenize("", 8);
        assertThat(emptyResult.inputIds()[0]).isEqualTo(101L);
        assertThat(emptyResult.inputIds()[1]).isEqualTo(102L);
    }
}
