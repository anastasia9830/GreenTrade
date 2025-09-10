package de.tub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a product (model) defined by the exchange (admin).
 * Sellers submit offers (price + quantity) for this model.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductModel {

    private String id;       // assigned by exchange/admin
    private String name;     // assigned by exchange/admin
    private String category; // assigned by exchange/admin

    /**
     * Offers from different sellers for this product model.
     * Always non-null.
     */
    @Builder.Default
    private List<ProductOffer> offers = new ArrayList<>();

    /**
     * Product-level trade history: last 3 execution prices (the prices at which trades actually happened).
     * Always non-null.
     */
    @Builder.Default
    private List<Double> priceHistory = new ArrayList<>();

    /** Average of current listed offer prices (simple mean). Returns 0.0 if there are no offers. */
    public double getMarketPrice() {
        return offers.stream()
                .mapToDouble(ProductOffer::getPrice)
                .average()
                .orElse(0.0);
    }

    /** Sum of all quantities across all current offers (available stock in the marketplace). */
    public int getAvailableQuantity() {
        return offers.stream().mapToInt(ProductOffer::getQuantity).sum();
    }

    /** Append a trade price and keep only the last 3 entries. */
    public void addPriceToHistory(double price) {
        priceHistory.add(price);
        if (priceHistory.size() > 3) {
            priceHistory.remove(0);
        }
    }

    /**
     * Add a new offer if there is no existing offer from the same seller.
     * @return true if added; false if an offer from this seller already exists.
     */
    public boolean addOffer(ProductOffer offer) {
        boolean exists = offers.stream()
                .anyMatch(o -> o.getSeller() != null
                        && offer.getSeller() != null
                        && o.getSeller().equalsIgnoreCase(offer.getSeller()));
        if (exists) return false;
        offers.add(offer);
        return true;
    }

    @Override
    public String toString() {
        return String.format(
                "ID: %s | Product: %s | Category: %s | Market Price: %.2f€ | Offers: %d | Available: %d",
                id, name, category, getMarketPrice(), offers.size(), getAvailableQuantity()
        );
    }
}
