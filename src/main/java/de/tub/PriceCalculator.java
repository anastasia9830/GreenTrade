package de.tub;

/**
 * Berechnet den neuen Preis anhand der Nachfrage (bought) und des aktuellen Angebots (available).
 * цена изменяется исходя из спроса и предложения
 * Used internally by the Market class.

 */

public class PriceCalculator {
    public static double calculateNewPrice(double oldPrice, int bought, int available) {
        return oldPrice * (1 + 0.05 * bought / (available + 1));
    }
}
