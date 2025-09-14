package de.tub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductModel {

    private String id;       
    private String name;     
    private String category; 

    @Builder.Default
    private List<ProductOffer> offers = new ArrayList<>();

    @Builder.Default
    private List<Double> priceHistory = new ArrayList<>();

    public double getMarketPrice() {
        return offers.stream()
                .mapToDouble(ProductOffer::getPrice)
                .average()
                .orElse(0.0);
    }

    public int getAvailableQuantity() {
        return offers.stream().mapToInt(ProductOffer::getQuantity).sum();
    }

    public void addPriceToHistory(double price) {
        priceHistory.add(price);
        if (priceHistory.size() > 3) {
            priceHistory.remove(0);
        }
    }

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
