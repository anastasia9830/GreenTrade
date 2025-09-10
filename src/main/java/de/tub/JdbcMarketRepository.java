package de.tub;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** JDBC repository for Market (DB mode). */
public class JdbcMarketRepository implements AutoCloseable {
    private final DataSource dataSource;

    public JdbcMarketRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /** Load all products with their offers. */
    public List<ProductModel> fetchAllModelsWithOffers() {
        final String sqlProducts = "SELECT id, name, category FROM products ORDER BY id";
        final String sqlOffers   = "SELECT product_id, seller, price, quantity FROM offers";

        Map<String, ProductModel> byId = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            // products
            try (PreparedStatement ps = conn.prepareStatement(sqlProducts);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getObject("id")); // works for INT or TEXT
                    ProductModel m = ProductModel.builder()
                            .id(id)
                            .name(rs.getString("name"))
                            .category(rs.getString("category"))
                            .build();
                    byId.put(id, m);
                }
            }
            // offers
            try (PreparedStatement ps = conn.prepareStatement(sqlOffers);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pid = String.valueOf(rs.getObject("product_id"));
                    ProductModel m = byId.get(pid);
                    if (m == null) continue;

                    ProductOffer offer = ProductOffer.builder()
                            .seller(rs.getString("seller"))
                            .price(rs.getDouble("price"))
                            .quantity(rs.getInt("quantity"))
                            .build();
                    m.addOffer(offer);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch data from DB", e);
        }

        return new ArrayList<>(byId.values());
    }

    /** Insert or update product (by id). */
    public void upsertProduct(String id, String name, String category) {
        final String sql = """
            INSERT INTO products(id,name,category) VALUES (?,?,?)
            ON CONFLICT (id) DO UPDATE
              SET name=EXCLUDED.name, category=EXCLUDED.category
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, normalizeId(id));
            ps.setString(2, name);
            ps.setString(3, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsertProduct failed", e);
        }
    }

    /** Insert or increase offer (conflict on (product_id, seller)). */
    public void upsertOffer(String productId, String seller, double price, int qty) {
        final String sql = """
            INSERT INTO offers(product_id,seller,price,quantity) VALUES (?,?,?,?)
            ON CONFLICT (product_id,seller) DO UPDATE
              SET price=EXCLUDED.price,
                  quantity=offers.quantity + EXCLUDED.quantity
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, normalizeId(productId));
            ps.setString(2, seller);
            ps.setDouble(3, price);
            ps.setInt(4, qty);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsertOffer failed", e);
        }
    }

    /** Atomic purchase: decrease qty, update listed price, record trade price. */
    public boolean buyFromOffer(String productName, String seller, int qty,
                                double executionPrice, double newListedPrice) {
        final String findId  = "SELECT id FROM products WHERE lower(name)=lower(?)";
        final String getQty  = "SELECT quantity FROM offers WHERE product_id=? AND seller=?";
        final String update  = "UPDATE offers SET quantity=quantity-?, price=? WHERE product_id=? AND seller=?";
        final String insHist = "INSERT INTO price_history(product_id,price) VALUES (?,?)";

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            // product id by name
            Object pidObj;
            try (PreparedStatement ps = c.prepareStatement(findId)) {
                ps.setString(1, productName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    pidObj = rs.getObject(1);
                }
            }

            // check available qty
            int available;
            try (PreparedStatement ps = c.prepareStatement(getQty)) {
                ps.setObject(1, pidObj);   // exactly same type as in DB
                ps.setString(2, seller);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    available = rs.getInt(1);
                }
            }
            if (qty <= 0 || available < qty) { c.rollback(); return false; }

            // decrease qty + set new listed price
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setInt(1, qty);
                ps.setDouble(2, newListedPrice);
                ps.setObject(3, pidObj);
                ps.setString(4, seller);
                ps.executeUpdate();
            }

            // record trade price (execution price)
            try (PreparedStatement ps = c.prepareStatement(insHist)) {
                ps.setObject(1, pidObj);
                ps.setDouble(2, executionPrice);
                ps.executeUpdate();
            }

            c.commit();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("buyFromOffer failed", e);
        }
    }

    /** Find product id by name (case-insensitive). */
    public String findProductIdByName(String name) {
        final String sql = "SELECT id FROM products WHERE lower(name)=lower(?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return String.valueOf(rs.getObject(1));
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findProductIdByName failed", e);
        }
    }

    /** Any products exist (health check). */
    public boolean hasAnyProducts() {
        final String sql = "SELECT EXISTS (SELECT 1 FROM products)";
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            throw new RuntimeException("hasAnyProducts failed", e);
        }
    }

    /** If DB id is integer, return Integer; else keep as String. */
    private Object normalizeId(String id) {
        try { return Integer.valueOf(id); } catch (Exception ignore) { return id; }
    }

    @Override public void close() { /* DataSource manages the pool */ }
    // JdbcMarketRepository.java

    /** Returns last N trade prices for a product (newest first). */
    public List<Double> fetchLastTradePrices(String productName, int limit) {
        final String sql = """
            SELECT ph.price
            FROM price_history ph
            JOIN products p ON p.id = ph.product_id
            WHERE lower(p.name) = lower(?)
            ORDER BY ph.id DESC
            LIMIT ?
        """;
        List<Double> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productName);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getDouble(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("fetchLastTradePrices failed", e);
        }
        return out;
    }

}
