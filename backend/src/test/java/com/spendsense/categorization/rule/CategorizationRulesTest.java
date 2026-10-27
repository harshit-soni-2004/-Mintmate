package com.spendsense.categorization.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

class CategorizationRulesTest {

    private TransactionNormalizer normalizer;
    private RideHailingAndTravelRule travelRule;
    private QuickCommerceAndFoodRule foodRule;
    private BillsAndUtilitiesRule billsRule;
    private ShoppingAndRetailRule shoppingRule;
    private EntertainmentAndStreamingRule entertainmentRule;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        travelRule = new RideHailingAndTravelRule();
        foodRule = new QuickCommerceAndFoodRule();
        billsRule = new BillsAndUtilitiesRule();
        shoppingRule = new ShoppingAndRetailRule();
        entertainmentRule = new EntertainmentAndStreamingRule();
    }

    private RuleContext createContext(String rawMerchant, String rawDescription) {
        NormalizedTransaction normalized = normalizer.normalize(rawMerchant, rawDescription);
        return new RuleContext(
                UUID.randomUUID(),
                normalized,
                BigDecimal.valueOf(150.00),
                "UPI",
                LocalDate.of(2026, 9, 15)
        );
    }

    @Nested
    @DisplayName("Ride Hailing and Travel Rule (Rapido, Uber, Ola, Metro, Fuel)")
    class RideHailingAndTravelTests {

        @Test
        @DisplayName("Matches Rapido across clean names and raw UPI narrations")
        void matchesRapido() {
            RuleContext ctx1 = createContext("Rapido", "Bike taxi ride");
            Optional<RuleMatchResult> res1 = travelRule.evaluate(ctx1);
            assertThat(res1).isPresent();
            assertThat(res1.get().category()).isEqualTo(TransactionCategory.TRAVEL);

            RuleContext ctx2 = createContext(
                    "WDL TFR UPI/DR/625481840735/Rapido/UT IB/rapido5223/UPI 0097694162092 AT 03117 VERSOVA (MUMBAI)",
                    null
            );
            Optional<RuleMatchResult> res2 = travelRule.evaluate(ctx2);
            assertThat(res2).isPresent();
            assertThat(res2.get().category()).isEqualTo(TransactionCategory.TRAVEL);
        }

        @Test
        @DisplayName("Does not match 'rapid' diagnostic medical tests as travel")
        void doesNotMatchRapidDiagnostics() {
            RuleContext ctx = createContext("Rapid Diagnostics", "Antigen blood test");
            assertThat(travelRule.evaluate(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Matches Uber ride but leaves Uber Eats for food rule")
        void handlesUberDisambiguation() {
            RuleContext rideCtx = createContext("Uber", "Uber Trip to Airport");
            Optional<RuleMatchResult> rideRes = travelRule.evaluate(rideCtx);
            assertThat(rideRes).isPresent();
            assertThat(rideRes.get().category()).isEqualTo(TransactionCategory.TRAVEL);

            RuleContext eatsCtx = createContext("Uber Eats", "Dinner burger delivery");
            assertThat(travelRule.evaluate(eatsCtx)).isEmpty();
        }

        @Test
        @DisplayName("Matches Ola Cabs but avoids 'Coca Cola', 'Solar', 'Viola', 'Poland'")
        void handlesOlaDisambiguation() {
            RuleContext olaCtx = createContext("Ola Cabs", "Cab ride to office");
            Optional<RuleMatchResult> olaRes = travelRule.evaluate(olaCtx);
            assertThat(olaRes).isPresent();
            assertThat(olaRes.get().category()).isEqualTo(TransactionCategory.TRAVEL);

            // Ambiguous non-travel words containing 'ola'
            assertThat(travelRule.evaluate(createContext("Coca Cola", "Beverage purchase"))).isEmpty();
            assertThat(travelRule.evaluate(createContext("Tata Solar", "Rooftop solar installation"))).isEmpty();
            assertThat(travelRule.evaluate(createContext("Viola Flowers", "Bouquet"))).isEmpty();
            assertThat(travelRule.evaluate(createContext("Poland Spring", "Water crate"))).isEmpty();
        }

        @Test
        @DisplayName("Matches public transit, Indian Railways, and fuel stations")
        void matchesTransitAndFuel() {
            assertThat(travelRule.evaluate(createContext("IRCTC", "Train ticket to Delhi"))).isPresent();
            assertThat(travelRule.evaluate(createContext("BMRCL Namma Metro", "Metro smart card recharge"))).isPresent();
            assertThat(travelRule.evaluate(createContext("HPCL Petrol Pump", "Fuel refuel"))).isPresent();
            assertThat(travelRule.evaluate(createContext("NHAI Fastag", "Toll plaza debit"))).isPresent();
        }
    }

    @Nested
    @DisplayName("Quick Commerce and Food Rule (Blinkit, Zepto, BigBasket, Swiggy, Zomato)")
    class QuickCommerceAndFoodTests {

        @Test
        @DisplayName("Matches Blinkit and prevents false match on 'blink' security or fitness")
        void matchesBlinkitSafely() {
            RuleContext blinkitCtx = createContext("Blinkit", "Instant grocery delivery");
            Optional<RuleMatchResult> res = foodRule.evaluate(blinkitCtx);
            assertThat(res).isPresent();
            assertThat(res.get().category()).isEqualTo(TransactionCategory.FOOD);

            RuleContext upiBlinkit = createContext(
                    "WDL TFR UPI/DR/625238169485/Blinkit/YESB/blinkit@yesbank/Payment",
                    null
            );
            assertThat(foodRule.evaluate(upiBlinkit)).isPresent();

            // 'Blink' alone should not trigger food rule
            assertThat(foodRule.evaluate(createContext("Blink Security", "Home camera"))).isEmpty();
            assertThat(foodRule.evaluate(createContext("Blink Fitness", "Gym membership"))).isEmpty();
        }

        @Test
        @DisplayName("Matches Zepto and BigBasket quick commerce")
        void matchesZeptoAndBigBasket() {
            assertThat(foodRule.evaluate(createContext("Zepto", "Grocery 10m delivery"))).isPresent();
            assertThat(foodRule.evaluate(createContext("BigBasket", "Weekly fruits and veggies"))).isPresent();
            assertThat(foodRule.evaluate(createContext("BB Daily", "Milk subscription"))).isPresent();
        }

        @Test
        @DisplayName("Matches Swiggy, Zomato, and Uber Eats")
        void matchesFoodDeliveryPlatforms() {
            assertThat(foodRule.evaluate(createContext("Swiggy", "Biryani order"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Swiggy Instamart", "Snacks delivery"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Zomato", "Lunch delivery"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Uber Eats", "Burger order"))).isPresent();
        }

        @Test
        @DisplayName("Matches restaurant and cafe chains")
        void matchesRestaurantsAndCafes() {
            assertThat(foodRule.evaluate(createContext("McDonald's", "Meal combo"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Domino's Pizza", "Pepperoni pizza"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Starbucks", "Cold brew coffee"))).isPresent();
            assertThat(foodRule.evaluate(createContext("Third Wave Coffee", "Espresso"))).isPresent();
        }
    }

    @Nested
    @DisplayName("Bills and Utilities Rule (Bescom, Power, Gas, Water, Telecom)")
    class BillsAndUtilitiesTests {

        @Test
        @DisplayName("Matches electricity boards and DISCOMs")
        void matchesPowerDiscoms() {
            RuleContext bescomCtx = createContext("Bescom", "Monthly electricity bill");
            Optional<RuleMatchResult> res = billsRule.evaluate(bescomCtx);
            assertThat(res).isPresent();
            assertThat(res.get().category()).isEqualTo(TransactionCategory.BILLS);

            assertThat(billsRule.evaluate(createContext("Tata Power", "Electric bill payment"))).isPresent();
            assertThat(billsRule.evaluate(createContext("Adani Electricity", "Mumbai power bill"))).isPresent();
        }

        @Test
        @DisplayName("Matches piped gas, LPG, and water boards")
        void matchesGasAndWater() {
            assertThat(billsRule.evaluate(createContext("Indane Gas", "LPG cylinder refill"))).isPresent();
            assertThat(billsRule.evaluate(createContext("Mahanagar Gas", "MGL piped gas bill"))).isPresent();
            assertThat(billsRule.evaluate(createContext("BWSSB", "Bangalore water supply bill"))).isPresent();
        }

        @Test
        @DisplayName("Matches telecom/broadband bills while excluding banking transfers")
        void matchesTelecomExcludingBanking() {
            assertThat(billsRule.evaluate(createContext("Airtel Broadband", "Monthly fiber bill"))).isPresent();
            assertThat(billsRule.evaluate(createContext("JioFiber", "Broadband recharge"))).isPresent();
            assertThat(billsRule.evaluate(createContext("Vi", "Postpaid mobile bill"))).isPresent();

            // Telecom payments bank transfers should not match BILLS
            assertThat(billsRule.evaluate(createContext("Airtel Payments Bank", "Savings account transfer"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Shopping and Retail Rule")
    class ShoppingAndRetailTests {

        @Test
        @DisplayName("Matches Amazon retail while excluding Amazon Prime Video")
        void handlesAmazonDisambiguation() {
            RuleContext retailCtx = createContext("Amazon", "Electronics headphones purchase");
            Optional<RuleMatchResult> res = shoppingRule.evaluate(retailCtx);
            assertThat(res).isPresent();
            assertThat(res.get().category()).isEqualTo(TransactionCategory.SHOPPING);

            RuleContext videoCtx = createContext("Amazon Prime Video", "Movie streaming subscription");
            assertThat(shoppingRule.evaluate(videoCtx)).isEmpty();
        }

        @Test
        @DisplayName("Matches Flipkart, Myntra, Croma, and D-Mart")
        void matchesRetailBrands() {
            assertThat(shoppingRule.evaluate(createContext("Flipkart", "Books order"))).isPresent();
            assertThat(shoppingRule.evaluate(createContext("Myntra", "Clothing purchase"))).isPresent();
            assertThat(shoppingRule.evaluate(createContext("Croma", "Laptop accessory"))).isPresent();
            assertThat(shoppingRule.evaluate(createContext("D-Mart", "Department store shopping"))).isPresent();
        }
    }

    @Nested
    @DisplayName("Entertainment and Streaming Rule")
    class EntertainmentAndStreamingTests {

        @Test
        @DisplayName("Matches Netflix, Spotify, Prime Video, and multiplexes")
        void matchesEntertainmentServices() {
            assertThat(entertainmentRule.evaluate(createContext("Netflix", "Monthly subscription"))).isPresent();
            assertThat(entertainmentRule.evaluate(createContext("Spotify", "Family plan audio"))).isPresent();
            assertThat(entertainmentRule.evaluate(createContext("Prime Video", "OTT subscription"))).isPresent();
            assertThat(entertainmentRule.evaluate(createContext("BookMyShow", "Movie tickets"))).isPresent();
            assertThat(entertainmentRule.evaluate(createContext("PVR Cinemas", "Cinema tickets and popcorn"))).isPresent();
        }
    }
}
