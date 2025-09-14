package de.tub;

import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log
public class Market {

    private final List<ProductModel> models = new ArrayList<>();

    private final JdbcMarketRepository repo;

    public Market() { this.repo = null; }

    public Market(JdbcMarketRepository repo) { this.repo = Objects.requireNonNull(repo); }

    private boolean isDbMode() { return repo != null; }


    public AuthorizedUsers login(String login, String password) {
        if (!isDbMode()) return null;
        return repo.authenticate(login, password);
    }


    public void addProductModel(String id, String name, String category, int initialQuantity) {
        if (isDbMode()) {
            repo.upsertProduct(id, name, category);
            if (initialQuantity > 0) {
                repo.upsertOffer(id, "Stock", 10.0, initialQuantity);
            }
            return;
        }
        // in-memory
        if (findModelByName(name) != null) return;
        ProductModel m = ProductModel.builder()
                .id(id).name(name).category(category)
                .build();
        if (initialQuantity > 0) {
            m.addOffer(ProductOffer.builder()
                    .seller("Stock").price(10.0).quantity(initialQuantity)
                    .priceHistory(new ArrayList<>(List.of(10.0)))
                    .build());
        }
        models.add(m);
    }

    public List<ProductModel> listAllModels() {
        if (isDbMode()) return repo.fetchAllModelsWithOffers();
        return models;
    }

    public List<ProductModel> searchModels(String query) {
        if (isDbMode()) {
            String q = (query == null ? "" : query.trim().toLowerCase());
            List<ProductModel> all = repo.fetchAllModelsWithOffers();
            if (q.isEmpty()) return all;
            return all.stream()
                    .filter(m -> m.getName().toLowerCase().contains(q) ||
                                 m.getCategory().toLowerCase().contains(q))
                    .toList();
        }
        String ql = query.toLowerCase();
        return models.stream()
                .filter(m -> m.getName().toLowerCase().contains(ql) ||
                             m.getCategory().toLowerCase().contains(ql))
                .toList();
    }

    public ProductModel findModelByName(String name) {
        if (isDbMode()) return repo.findModelByNameWithOffers(name);
        return models.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public ProductOffer getOffer(String productName, String seller) {
        if (isDbMode()) return repo.getOffer(productName, seller);
        ProductModel model = findModelByName(productName);
        if (model == null) return null;
        return model.getOffers().stream()
                .filter(o -> o.getSeller().equalsIgnoreCase(seller))
                .findFirst().orElse(null);
    }

    public boolean buyFromOffer(String productName, String seller, int qty) {
        if (qty <= 0) return false;

        if (isDbMode()) {
            ProductOffer offer = repo.getOffer(productName, seller);
            if (offer == null || offer.getQuantity() < qty) return false;

            double executionPrice = offer.getPrice();
            int totalBefore = repo.getTotalAvailableForProduct(productName);
            int availableAfter = totalBefore - qty;
            double newListedPrice = PriceCalculator.calculateNewPrice(executionPrice, qty, availableAfter);

            return repo.buyFromOffer(productName, seller, qty, executionPrice, newListedPrice);
        }

        ProductModel model = findModelByName(productName);
        if (model == null) return false;

        ProductOffer offer = model.getOffers().stream()
                .filter(o -> o.getSeller() != null && o.getSeller().equalsIgnoreCase(seller))
                .findFirst().orElse(null);
        if (offer == null || offer.getQuantity() < qty) return false;

        double executionPrice = offer.getPrice();
        offer.setQuantity(offer.getQuantity() - qty);

        int availableAfter = model.getOffers().stream().mapToInt(ProductOffer::getQuantity).sum();
        double newPrice = PriceCalculator.calculateNewPrice(executionPrice, qty, availableAfter);
        offer.setPrice(newPrice);

        List<Double> ph = model.getPriceHistory();
        if (ph == null) ph = new ArrayList<>();
        ph.add(executionPrice);
        if (ph.size() > 3) ph.subList(0, ph.size() - 3).clear();
        model.setPriceHistory(ph);

        List<Double> oh = offer.getPriceHistory();
        if (oh == null) oh = new ArrayList<>();
        if (oh.size() >= 3) oh.remove(0);
        oh.add(newPrice);
        offer.setPriceHistory(oh);

        return true;
    }

    public boolean updateOffer(String productName, String seller, int addedQuantity, double newPrice) {
        if (isDbMode()) {
            String id = repo.findProductIdByName(productName);
            if (id == null) { log.warning("Product not found: " + productName); return false; }
            if (addedQuantity <= 0) { log.warning("Quantity must be positive."); return false; }
            repo.upsertOffer(id, seller, newPrice, addedQuantity);
            return true;
        }

        // ---- in-memory fallback ----
        ProductModel model = findModelByName(productName);
        if (model == null) { log.warning("Product not found."); return false; }

        ProductOffer existing = model.getOffers().stream()
                .filter(o -> o.getSeller().equalsIgnoreCase(seller))
                .findFirst().orElse(null);

        if (existing != null) {
            if (addedQuantity < -existing.getQuantity()) {
                log.warning("Cannot reduce quantity below 0.");
                return false;
            }
            existing.setQuantity(existing.getQuantity() + addedQuantity);
            existing.setPrice(newPrice);
            updatePriceHistory(existing, newPrice);
            return true;
        } else {
            if (addedQuantity <= 0) return false;
            return model.addOffer(ProductOffer.builder()
                    .seller(seller).price(newPrice).quantity(addedQuantity)
                    .priceHistory(new ArrayList<>(List.of(newPrice))).build());
        }
    }

    public boolean addOfferToExistingProduct(String productName, ProductOffer offer) {
        if (isDbMode()) {
            String id = repo.findProductIdByName(productName);
            if (id == null) return false;
            repo.upsertOffer(id, offer.getSeller(), offer.getPrice(), offer.getQuantity());
            return true;
        }
        ProductModel model = findModelByName(productName);
        if (model == null) return false;
        return model.addOffer(offer);
    }

    public List<Double> getOfferPriceHistory(String productName, String seller) {
        ProductOffer offer = getOffer(productName, seller);
        return (offer != null) ? offer.getPriceHistory() : null;
    }

    private void updatePriceHistory(ProductOffer offer, double newPrice) {
        List<Double> history = offer.getPriceHistory();
        if (history == null) history = new ArrayList<>();
        if (history.size() >= 3) history.remove(0);
        history.add(newPrice);
        offer.setPriceHistory(history);
    }

    public List<Double> getLastTradePrices(String productName, int limit) {

        if (limit <= 0) return java.util.Collections.emptyList();
        if (isDbMode()) return repo.getLastTradePrices(productName, limit);

        ProductModel m = findModelByName(productName);
        if (m == null) return java.util.Collections.emptyList();
        List<Double> ph = m.getPriceHistory();
        if (ph == null || ph.isEmpty()) return java.util.Collections.emptyList();

        int from = Math.max(0, ph.size() - limit);
        List<Double> tail = new ArrayList<>(ph.subList(from, ph.size())); 
        java.util.Collections.reverse(tail); 
        return tail;
    }
}
