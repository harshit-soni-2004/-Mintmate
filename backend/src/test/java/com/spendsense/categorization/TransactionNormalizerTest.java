package com.spendsense.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionNormalizerTest {

    private TransactionNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
    }

    @Nested
    @DisplayName("Case & Whitespace Normalization")
    class CaseAndWhitespaceTests {

        @Test
        @DisplayName("Case normalization: converts to lowercase")
        void normalizesCase() {
            assertThat(normalizer.normalizeMerchant("SwIgGy")).isEqualTo("swiggy");
            assertThat(normalizer.normalizeMerchant("AMAZON")).isEqualTo("amazon");
            assertThat(normalizer.normalizeMerchant("NeTfLiX")).isEqualTo("netflix");
        }

        @Test
        @DisplayName("Whitespace normalization: collapses multiple spaces and trims")
        void collapsesWhitespace() {
            var result = normalizer.normalize("  SWIGGY    IN  ", "  LUNCH   ORDER  ");
            assertThat(result.normalizedMerchant()).isEqualTo("swiggy");
            assertThat(result.compositeText()).isEqualTo("swiggy in lunch order");
        }
    }

    @Nested
    @DisplayName("Punctuation & Noise Separation")
    class PunctuationAndNoiseTests {

        @Test
        @DisplayName("Punctuation normalization replaces delimiters with single spaces")
        void normalizesPunctuationDelimiters() {
            assertThat(normalizer.normalizeMerchant("SWIGGY*ORDER*123")).isEqualTo("swiggy");
            assertThat(normalizer.normalizeMerchant("UBER *TRIP")).isEqualTo("uber trip");
            assertThat(normalizer.normalizeMerchant("ZOMATO_ONLINE_ORDER")).isEqualTo("zomato");
        }

        @Test
        @DisplayName("Removes transaction IDs and reference numbers from composite text")
        void removesNumericIdsFromCompositeText() {
            var result = normalizer.normalize("SWIGGY*ORDER*123456789", null);
            // Retains "swiggy order" while removing the 9-digit transaction ID "123456789"
            assertThat(result.compositeText()).isEqualTo("swiggy order");
            assertThat(result.normalizedMerchant()).isEqualTo("swiggy");
        }

        @Test
        @DisplayName("Filters mixed alphanumeric hash/reference tokens")
        void filtersAlphanumericReferenceTokens() {
            var result = normalizer.normalize("NETFLIX txn98234 ref8921a", "Order #ord99128");
            assertThat(result.normalizedMerchant()).isEqualTo("netflix");
            assertThat(result.compositeText()).isEqualTo("netflix order");
        }
    }

    @Nested
    @DisplayName("Payment Rails & Indian UPI Coverage")
    class PaymentRailsTests {

        @ParameterizedTest(name = "Normalizes UPI pattern: {0}")
        @ValueSource(strings = {
                "SWIGGY*ORDER*12345",
                "SWIGGY IN",
                "UPI-SWIGGY-834723",
                "UPI/DR/123456789/SWIGGY",
                "UPI/CR/987654321/SWIGGY",
                "UPI/SWIGGY/425512345678/Pay",
                "swiggy@okhdfcbank",
                "swiggy@paytm",
                "Swiggy"
        })
        void upiMerchantVariantsConvergeToSwiggy(String input) {
            assertThat(normalizer.normalizeMerchant(input)).isEqualTo("swiggy");
        }

        @Test
        @DisplayName("Handles POS prefix and card authorization patterns")
        void handlesPosPrefix() {
            assertThat(normalizer.normalizeMerchant("POS-12345-NETFLIX")).isEqualTo("netflix");
            assertThat(normalizer.normalizeMerchant("POS/SWIGGY BANGALORE/98231")).isEqualTo("swiggy bangalore");
        }

        @Test
        @DisplayName("Handles NEFT, IMPS, and ATM transaction prefixes")
        void handlesBankTransferPrefixes() {
            assertThat(normalizer.normalizeMerchant("NEFT-CR-HDFC000123-SWIGGY")).isEqualTo("swiggy");
            assertThat(normalizer.normalizeMerchant("IMPS/P2A/425512345678/SWIGGY")).isEqualTo("swiggy");
            assertThat(normalizer.normalizeMerchant("ATM-WDL-HDFC BANK")).isEqualTo("hdfc bank");
        }
    }

    @Nested
    @DisplayName("Information Preservation & Distinct Merchants")
    class InformationPreservationTests {

        @Test
        @DisplayName("AMAZON PRIME preserves 'prime' to distinguish subscription from general shopping")
        void preservesAmazonPrime() {
            var result = normalizer.normalize("AMAZON PRIME", null);
            assertThat(result.normalizedMerchant()).isEqualTo("amazon prime");
            assertThat(result.compositeText()).isEqualTo("amazon prime");
        }

        @Test
        @DisplayName("UBER INDIA preserves 'india' token")
        void preservesUberIndia() {
            var result = normalizer.normalize("UBER INDIA", null);
            assertThat(result.normalizedMerchant()).isEqualTo("uber india");
        }

        @Test
        @DisplayName("UBER *TRIP preserves 'trip' to enable travel vs food discrimination")
        void preservesUberTrip() {
            var result = normalizer.normalize("UBER *TRIP", null);
            assertThat(result.normalizedMerchant()).isEqualTo("uber trip");
        }

        @Test
        @DisplayName("AMZN MKTPL INDIA preserves marketplace information")
        void preservesAmznMktplIndia() {
            var result = normalizer.normalize("AMZN MKTPL INDIA", null);
            assertThat(result.normalizedMerchant()).isEqualTo("amzn mktpl india");
        }

        @Test
        @DisplayName("Preserves short numeric brand names like 7-Eleven and 24-Seven")
        void preservesShortNumericBrandNames() {
            assertThat(normalizer.normalizeMerchant("7-ELEVEN")).isEqualTo("7 eleven");
            assertThat(normalizer.normalizeMerchant("24 SEVEN")).isEqualTo("24 seven");
            assertThat(normalizer.normalizeMerchant("3M INDIA")).isEqualTo("3m india");
            assertThat(normalizer.normalizeMerchant("H&M")).isEqualTo("h&m");
        }

        @Test
        @DisplayName("Similar merchants do not falsely merge into a single key")
        void doesNotFalselyMergeDistinctMerchants() {
            String uberIndia = normalizer.normalizeMerchant("UBER INDIA");
            String uberTrip = normalizer.normalizeMerchant("UBER *TRIP");
            String uberEats = normalizer.normalizeMerchant("UBER EATS");

            assertThat(uberIndia).isNotEqualTo(uberTrip);
            assertThat(uberTrip).isNotEqualTo(uberEats);
            assertThat(uberIndia).isNotEqualTo(uberEats);
        }
    }

    @Nested
    @DisplayName("Merchant + Description Combination for ML")
    class MerchantAndDescriptionTests {

        @Test
        @DisplayName("Combines merchant and description without duplicate noise")
        void combinesMerchantAndDescription() {
            var result = normalizer.normalize("Swiggy", "Dinner biryani delivery order #99482");
            assertThat(result.normalizedMerchant()).isEqualTo("swiggy");
            assertThat(result.normalizedDescription()).isEqualTo("dinner biryani delivery order");
            assertThat(result.compositeText()).isEqualTo("swiggy dinner biryani delivery order");
            assertThat(result.tokens()).containsExactly("swiggy");
        }
    }

    @Nested
    @DisplayName("Edge Cases, Idempotency & Determinism")
    class RobustnessTests {

        @Test
        @DisplayName("Handles null and blank inputs safely")
        void handlesNullAndBlank() {
            var nullResult = normalizer.normalize(null, null);
            assertThat(nullResult.normalizedMerchant()).isEqualTo("unknown");
            assertThat(nullResult.compositeText()).isEqualTo("unknown");

            var blankResult = normalizer.normalize("   ", "   ");
            assertThat(blankResult.normalizedMerchant()).isEqualTo("unknown");

            assertThat(normalizer.normalizeMerchant(null)).isEqualTo("unknown");
            assertThat(normalizer.normalizeMerchant("   ")).isEqualTo("unknown");
            assertThat(normalizer.normalizeText(null)).isEmpty();
        }

        @Test
        @DisplayName("Normalizer is idempotent: normalize(normalize(x)) == normalize(x)")
        void isIdempotent() {
            String[] testInputs = {
                    "SWIGGY*ORDER*12345",
                    "AMAZON PRIME",
                    "UBER INDIA",
                    "POS-12345-NETFLIX",
                    "7-ELEVEN",
                    "H&M"
            };

            for (String input : testInputs) {
                String pass1 = normalizer.normalizeMerchant(input);
                String pass2 = normalizer.normalizeMerchant(pass1);
                assertThat(pass2).as("Idempotency failed for: " + input).isEqualTo(pass1);
            }
        }

        @Test
        @DisplayName("Normalizer is deterministic across repeated executions")
        void isDeterministic() {
            String input = "UPI/DR/123456789/SWIGGY";
            String expected = normalizer.normalizeMerchant(input);
            for (int i = 0; i < 50; i++) {
                assertThat(normalizer.normalizeMerchant(input)).isEqualTo(expected);
            }
        }
    }
}
