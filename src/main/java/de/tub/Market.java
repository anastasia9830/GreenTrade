package de.tub;

import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Market can work in two modes:
 *  - In-memory (no repo): behaves like before
 *  - DB-backed (repo != null): reads/writes via JdbcMarketRepository
 */
@Log
public class Market {

    // in-memory storage (used only when repo == null)
    private final List<ProductModel> models = new ArrayList<>();

    // DB repo (nullable)
    private final JdbcMarketRepository repo;

    /** In-memory mode. */
    public Market() {
        this.repo = null;
    }

    /** DB mode. */
    public Market(JdbcMarketRepository repo) {
        this.repo = Objects.requireNonNull(repo);
    }

    private boolean isDbMode() { return repo != null; }

    /** Admin: add product model; if initialQuantity>0, create "Stock" offer. */
    public void addProductModel(String id, String name, String category, int initialQuantity) {
        if (isDbMode()) {
            // upsert product
            repo.upsertProduct(id, name, category);
            // optional starting stock
            if (initialQuantity > 0) {
                repo.upsertOffer(id, "Stock", 10.0, initialQuantity);
            }
            return;
        }

        // ---- in-memory fallback ----
        if (findModelByName(name) != null) return;
        ProductModel m = ProductModel.builder()
                .id(id).name(name).category(category)
                .build();
        if (initialQuantity > 0) {
            ProductOffer stock = ProductOffer.builder()
                    .seller("Stock")
                    .price(10.0)
                    .quantity(initialQuantity)
                    .priceHistory(new ArrayList<>(List.of(10.0)))
                    .build();
            m.addOffer(stock);
        }
        models.add(m);
    }

    /** List all product models (with offers). */
    public List<ProductModel> listAllModels() {
        if (isDbMode()) {
            return repo.fetchAllModelsWithOffers();
        }
        return models;
    }

    /** Search models by name OR category (case-insensitive). */
    public List<ProductModel> searchModels(String query) {
        if (isDbMode()) {
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) return repo.fetchAllModelsWithOffers();
            // простая фильтрация на стороне приложения
            String ql = q.toLowerCase();
            return repo.fetchAllModelsWithOffers().stream()
                    .filter(m -> m.getName().toLowerCase().contains(ql)
                              || m.getCategory().toLowerCase().contains(ql))
                    .toList();
        }
        String ql = query.toLowerCase();
        return models.stream()
                .filter(m -> m.getName().toLowerCase().contains(ql)
                          || m.getCategory().toLowerCase().contains(ql))
                .toList();
    }

    /** Get specific offer by model name and seller. */
    public ProductOffer getOffer(String productName, String seller) {
        if (isDbMode()) {
            // загрузим модели и найдём нужный
            return repo.fetchAllModelsWithOffers().stream()
                    .filter(m -> m.getName().equalsIgnoreCase(productName))
                    .flatMap(m -> m.getOffers().stream())
                    .filter(o -> o.getSeller().equalsIgnoreCase(seller))
                    .findFirst()
                    .orElse(null);
        }

        ProductModel model = findModelByName(productName);
        if (model == null) return null;
        return model.getOffers().stream()
                .filter(o -> o.getSeller().equalsIgnoreCase(seller))
                .findFirst().orElse(null);
    }

    /** Buy from seller’s offer and record trade price history (last 3). */
    public boolean buyFromOffer(String productName, String seller, int qty) {
        if (qty <= 0) return false;

        if (isDbMode()) {
            // Нужно вычислить executionPrice (текущая цена оффера) и newListedPrice (после сделки)
            ProductOffer offer = getOffer(productName, seller);
            if (offer == null || offer.getQuantity() < qty) return false;

            double executionPrice = offer.getPrice();

            // посчитаем доступный остаток ПОСЛЕ сделки (для перерасчёта цены)
            // берём текущее состояние всего продукта
            ProductModel model = findModelByName(productName);
            if (model == null) return false;
            int availableAfter = model.getOffers().stream().mapToInt(ProductOffer::getQuantity).sum() - qty;

            double newListedPrice = PriceCalculator.calculateNewPrice(executionPrice, qty, availableAfter);

            // транзакция в БД (уменьшить qty, обновить цену оффера, записать цену сделки в history)
            return repo.buyFromOffer(productName, seller, qty, executionPrice, newListedPrice);
        }

        // ---- in-memory fallback ----
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

        // PRODUCT-level last 3 execution prices
        List<Double> ph = model.getPriceHistory();
        if (ph == null) ph = new ArrayList<>();
        ph.add(executionPrice);
        if (ph.size() > 3) ph.subList(0, ph.size() - 3).clear();
        model.setPriceHistory(ph);

        // OFFER-level last 3 listed prices
        List<Double> oh = offer.getPriceHistory();
        if (oh == null) oh = new ArrayList<>();
        if (oh.size() >= 3) oh.remove(0);
        oh.add(newPrice);
        offer.setPriceHistory(oh);

        return true;
    }

    /** Create or update seller’s offer. */
    public boolean updateOffer(String productName, String seller, int addedQuantity, double newPrice) {
        if (isDbMode()) {
            // найдём id по имени
            String id = repo.findProductIdByName(productName);
            if (id == null) {
                log.warning("Product not found: " + productName);
                return false;
            }
            if (addedQuantity <= 0) {
                log.warning("Initial/added quantity must be positive.");
                return false;
            }
            repo.upsertOffer(id, seller, newPrice, addedQuantity);
            return true;
        }

        // ---- in-memory fallback ----
        ProductModel model = findModelByName(productName);
        if (model == null) {
            log.warning("Product not found.");
            return false;
        }
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
            log.info("Offer updated.");
        } else {
            if (addedQuantity <= 0) {
                log.warning("Initial quantity must be positive.");
                return false;
            }
            ProductOffer newOffer = ProductOffer.builder()
                    .seller(seller)
                    .price(newPrice)
                    .quantity(addedQuantity)
                    .priceHistory(new ArrayList<>(List.of(newPrice)))
                    .build();
            return model.addOffer(newOffer);
        }
        return true;
    }

    /** Add a new offer to an existing product (used by sellItem when no previous offer exists). */
    public boolean addOfferToExistingProduct(String productName, ProductOffer offer) {
        if (isDbMode()) {
            String id = repo.findProductIdByName(productName);
            if (id == null) return false;
            repo.upsertOffer(id, offer.getSeller(), offer.getPrice(), offer.getQuantity());
            return true;
        }

        // ---- in-memory fallback ----
        ProductModel model = findModelByName(productName);
        if (model == null) return false;
        return model.addOffer(offer);
    }

    /** Offer price history (in-memory only). DB history смотри в таблице price_history. */
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

    /** Find model by name (case-insensitive). */
    public ProductModel findModelByName(String name) {
        if (isDbMode()) {
            return repo.fetchAllModelsWithOffers().stream()
                    .filter(m -> m.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
        return models.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** helper for Console.search (optional) */
    public List<ProductOffer> getOffersByNameOrCategory(String q) {
        return searchModels(q).stream().flatMap(m -> m.getOffers().stream()).toList();
    }
    // Market.java

    /** Last N trade prices (newest first). Backed by DB (if enabled) or in-memory product history. */
    public List<Double> getLastTradePrices(String productName, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();

        if (isDbMode()) {
            return repo.fetchLastTradePrices(productName, limit);
        }

        // in-memory fallback
        ProductModel m = findModelByName(productName);
        if (m == null) return java.util.Collections.emptyList();
        List<Double> ph = m.getPriceHistory();
        if (ph == null || ph.isEmpty()) return java.util.Collections.emptyList();

        int from = Math.max(0, ph.size() - limit);
        List<Double> tail = new ArrayList<>(ph.subList(from, ph.size())); // newest at end
        java.util.Collections.reverse(tail); // newest first
        return tail;
    }

}
