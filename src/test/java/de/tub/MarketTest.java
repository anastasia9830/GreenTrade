package de.tub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketTest {

    private Market market;

    @BeforeEach
    void setUp() {
        market = new Market();
    }

    @Test
    void addOffer_shouldAddNewModel() {
        ProductOffer offer = ProductOffer.builder()
                .seller("alice")
                .price(10.0)
                .quantity(5)
                .priceHistory(new ArrayList<>(List.of(10.0)))
                .build();

        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", offer);

        List<ProductModel> models = market.listAllModels();
        assertEquals(1, models.size());
        assertEquals("Banana", models.get(0).getName());
        assertEquals("123", models.get(0).getId());
    }

    @Test
    void addOffer_shouldNotAddDuplicateModel() {
        ProductOffer offer = ProductOffer.builder()
                .seller("alice")
                .price(10.0)
                .quantity(5)
                .priceHistory(new ArrayList<>(List.of(10.0)))
                .build();

        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", offer);
        market.addProductModel("123", "Banana", "Fruit", 100);  

        assertEquals(1, market.listAllModels().size());
    }

    @Test
    void buy_shouldSucceed_whenStockIsSufficient() {
        ProductOffer offer = ProductOffer.builder()
                .seller("alice")
                .price(100.0)
                .quantity(10)
                .priceHistory(new ArrayList<>(List.of(100.0)))
                .build();

        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", offer);

        boolean result = market.buyFromOffer("Banana", "alice", 5);

        assertTrue(result);
        assertEquals(5, offer.getQuantity());
        assertEquals(2, offer.getPriceHistory().size());
    }

    @Test
    void buy_shouldFail_whenOfferNotFound() {
        boolean result = market.buyFromOffer("Apple", "unknown", 3);
        assertFalse(result);
    }

    @Test
    void updateOffer_shouldCreateOfferIfNotExists() {
        ProductOffer base = ProductOffer.builder()
                .seller("alice")
                .price(100.0)
                .quantity(10)
                .priceHistory(new ArrayList<>(List.of(100.0)))
                .build();
        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", base);

        boolean updated = market.updateOffer("Banana", "bob", 5, 50.0);

        assertTrue(updated);
        assertNotNull(market.getOffer("Banana", "bob"));
        assertEquals(50.0, market.getOffer("Banana", "bob").getPrice());
    }

    @Test
    void getOffer_shouldReturnCorrectOffer() {
        ProductOffer offer = ProductOffer.builder()
                .seller("alice")
                .price(10.0)
                .quantity(5)
                .priceHistory(new ArrayList<>(List.of(10.0)))
                .build();

        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", offer);

        ProductOffer fetched = market.getOffer("Banana", "alice");
        assertNotNull(fetched);
        assertEquals("alice", fetched.getSeller());
    }

    @Test
    void getOffer_shouldReturnNullIfNotFound() {
        assertNull(market.getOffer("NoProduct", "nobody"));
    }

    @Test
    void history_shouldContainMax3Entries() {
        ProductOffer offer = ProductOffer.builder()
                .seller("alice")
                .price(10.0)
                .quantity(5)
                .priceHistory(new ArrayList<>(List.of(10.0)))
                .build();

        market.addProductModel("123", "Banana", "Fruit", 100);
        market.addOfferToExistingProduct("Banana", offer);
        market.updateOffer("Banana", "alice", 1, 11.0);
        market.updateOffer("Banana", "alice", 1, 12.0);
        market.updateOffer("Banana", "alice", 1, 13.0);

        List<Double> history = market.getOfferPriceHistory("Banana", "alice");
        assertEquals(3, history.size());
        assertEquals(11.0, history.get(0)); 
    }
}

