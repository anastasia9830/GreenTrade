package de.tub;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PriceCalculatorTest {

    @Test
    void testTypicalCase() {
        double result = PriceCalculator.calculateNewPrice(100.0, 10, 50);
        assertTrue(result > 100.0);
    }

    @Test
    void testZeroBought() {
        double result = PriceCalculator.calculateNewPrice(80.0, 0, 20);
        assertEquals(80.0, result, 0.0001);
    }

    @Test
    void testZeroAvailable() {
        double result = PriceCalculator.calculateNewPrice(50.0, 5, 0);
        assertEquals(50.0 * (1 + 0.05 * 5), result, 0.0001);
    }

    @Test
    void testPriceAlwaysGrowsModerately() {
        double oldPrice = 100.0;
        for (int bought = 1; bought <= 20; bought++) {
            for (int available = 1; available <= 20; available++) {
                double newPrice = PriceCalculator.calculateNewPrice(oldPrice, bought, available);
                assertTrue(newPrice >= oldPrice, "Price should not decrease");
                // не проверяем верхнюю границу: формула её не гарантирует
            }
        }
    }

    @Test
    void testPriceIncreasesWithDemand() {
        double p1 = PriceCalculator.calculateNewPrice(100.0, 2, 10);
        double p2 = PriceCalculator.calculateNewPrice(100.0, 5, 10);
        double p3 = PriceCalculator.calculateNewPrice(100.0, 10, 10);

        assertTrue(p1 < p2 && p2 < p3, "Price should increase with more bought units");
    }
}

