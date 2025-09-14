package de.tub;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConsoleTest {

    @Test
    void testAddProduct_createsProductSuccessfully() {
        Market market = new Market();
        Scanner scanner = new Scanner("""
            1234
            TestProduct
            Food
            4.0
            100
        """);
        Console console = new Console(market, scanner);
        //console.setCurrentUser(new AuthorizedUsers("admin", "1234", "admin"));

        console.addProduct();

        List<ProductModel> products = market.listAllModels();
        assertEquals(1, products.size());
        assertEquals("TestProduct", products.get(0).getName());
    }

    @Test
    void testSellItem_createsOfferSuccessfully() {
        Market market = new Market();
        market.addProductModel("p001", "Apple", "Fruit", 100);

        Scanner scanner = new Scanner("""
            Apple
            20
            2.5
        """);
        Console console = new Console(market, scanner);
        console.setCurrentUser(new AuthorizedUsers("seller1", null, "seller"));

        console.sellItem();

        ProductOffer offer = market.getOffer("Apple", "seller1");
        assertNotNull(offer);
        assertEquals(20, offer.getQuantity());
        assertEquals(2.5, offer.getPrice());
    }

    @Test
    void testBuyItem_updatesQuantityAndPrice() {
        Market market = new Market();
        market.addProductModel("p002", "Milk", "Dairy", 100);
        ProductOffer offer = ProductOffer.builder()
                .seller("seller1")
                .price(1.5)
                .quantity(10)
                .priceHistory(new ArrayList<>(List.of(1.5)))
                .build();
        market.addOfferToExistingProduct("Milk", offer);

        Scanner scanner = new Scanner("""
            Milk
            seller1
            3
        """);
        Console console = new Console(market, scanner);
        //console.setCurrentUser(new AuthorizedUsers("buyer1", "pass", "buyer"));

        console.buyItem();

        ProductOffer updated = market.getOffer("Milk", "seller1");
        assertEquals(7, updated.getQuantity());
        assertTrue(updated.getPrice() > 1.5); // Цена должна возрасти
    }
}
