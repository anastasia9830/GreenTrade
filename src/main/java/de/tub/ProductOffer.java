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
public class ProductOffer {

    private String seller;

    @Builder.Default
    private double price = 0.0;

    @Builder.Default
    private int quantity = 0;

    @Builder.Default
    private List<Double> priceHistory = new ArrayList<>();

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
