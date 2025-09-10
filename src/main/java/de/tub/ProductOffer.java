package de.tub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an individual offer from a seller for a specific product model.
 * - price           : current listed price
 * - quantity        : available units in this offer
 * - priceHistory    : last 3 listed prices for this offer (not trade prices)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductOffer {

    private String seller;

    @Builder.Default
    private double price = 0.0;

    @Builder.Default
    private int quantity = 0;

    /** Always non-null to avoid NPEs. */
    @Builder.Default
    private List<Double> priceHistory = new ArrayList<>();

    /** Append a listed price and keep only the last 3 entries. */
    public void addListedPriceToHistory(double listedPrice) {
        priceHistory.add(listedPrice);
        if (priceHistory.size() > 3) {
            priceHistory.remove(0);
        }
    }

    @Override
    public String toString() {
        return String.format("Seller: %s | Price: %.2f€ | Quantity: %d", seller, price, quantity);
    }
}
