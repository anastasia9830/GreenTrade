package de.tub;

/**
написать объяснение почему такая цена
 */

public class PriceCalculator {
    public static double calculateNewPrice(double oldPrice, int bought, int available) {
        return oldPrice * (1 + 0.05 * bought / (available + 1));
    }
}
