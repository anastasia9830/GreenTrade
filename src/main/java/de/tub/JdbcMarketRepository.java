package de.tub;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class JdbcMarketRepository implements AutoCloseable {
    private final DataSource dataSource;

    public JdbcMarketRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /* ---------- helpers ---------- */

    /** Если id числовой — вернёт Integer; иначе — исходную строку. */
    private Object normalizeId(String id) {
        try { return Integer.valueOf(id); } catch (Exception ignore) { return id; }
    }

    

    /* ---------- reads ---------- */

    /** Все модели с офферами. */
    public List<ProductModel> fetchAllModelsWithOffers() {
        final String sqlProducts = "SELECT id, name, category FROM products ORDER BY id";
        final String sqlOffers   = "SELECT product_id, seller, price, quantity FROM offers";

        Map<String, ProductModel> byId = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            // products
            try (PreparedStatement ps = conn.prepareStatement(sqlProducts);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object idObj = rs.getObject("id");
                    String idStr = String.valueOf(idObj);
                    ProductModel m = ProductModel.builder()
                            .id(idStr)
                            .name(rs.getString("name"))
                            .category(rs.getString("category"))
                            .build();
                    byId.put(idStr, m);
                }
            }
            // offers
            try (PreparedStatement ps = conn.prepareStatement(sqlOffers);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pidStr = String.valueOf(rs.getObject("product_id"));
                    ProductModel m = byId.get(pidStr);
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

    /** Одна модель по имени (безопасно к регистру) + её офферы. */
    public ProductModel findModelByNameWithOffers(String name) {
        final String sqlP = "SELECT id, name, category FROM products WHERE lower(name)=lower(?)";
        final String sqlO = "SELECT seller, price, quantity FROM offers WHERE product_id=?";

        try (Connection c = dataSource.getConnection()) {
            Object pidObj;
            String idStr;
            String pname;
            String pcat;

            // product
            try (PreparedStatement ps = c.prepareStatement(sqlP)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    pidObj = rs.getObject("id");
                    idStr  = String.valueOf(pidObj);
                    pname  = rs.getString("name");
                    pcat   = rs.getString("category");
                }
            }

            ProductModel m = ProductModel.builder()
                    .id(idStr).name(pname).category(pcat)
                    .build();

            // offers
            try (PreparedStatement ps = c.prepareStatement(sqlO)) {
                ps.setObject(1, pidObj);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        m.addOffer(ProductOffer.builder()
                                .seller(rs.getString("seller"))
                                .price(rs.getDouble("price"))
                                .quantity(rs.getInt("quantity"))
                                .build());
                    }
                }
            }
            return m;
        } catch (SQLException e) {
            throw new RuntimeException("findModelByNameWithOffers failed", e);
        }
    }

    public String findProductIdByName(String productName) {
        final String sql = "SELECT id FROM products WHERE lower(name)=lower(?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return String.valueOf(rs.getObject(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findProductIdByName failed", e);
        }
    }

    /** Конкретный оффер по имени продукта и продавцу. */
    public ProductOffer getOffer(String productName, String seller) {
        final String sql = """
            SELECT o.seller, o.price, o.quantity
            FROM offers o
            JOIN products p ON p.id = o.product_id
            WHERE lower(p.name)=lower(?) AND lower(o.seller)=lower(?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productName);
            ps.setString(2, seller);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return ProductOffer.builder()
                        .seller(rs.getString("seller"))
                        .price(rs.getDouble("price"))
                        .quantity(rs.getInt("quantity"))
                        .build();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getOffer failed", e);
        }
    }

    /** (сумма quantity по всем офферам). */
    public int getTotalAvailableForProduct(String productName) {
        final String sql = """
            SELECT COALESCE(SUM(o.quantity),0)
            FROM offers o
            JOIN products p ON p.id = o.product_id
            WHERE lower(p.name)=lower(?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTotalAvailableForProduct failed", e);
        }
    }

    /** Последние N цен сделки (по продукту), новейшие первыми.Тут есть исправления Order by */
    public List<Double> getLastTradePrices(String productName, int limit) {
        final String sql = """
        SELECT h.price
        FROM price_history h
        JOIN products p ON p.id = h.product_id
        WHERE lower(p.name)=lower(?)
        ORDER BY h.created_at DESC
        LIMIT ?
        """;
        List<Double> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productName);
            ps.setInt(2, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getDouble(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getLastTradePrices failed", e);
        }
        return out;
    }

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

    /** Атомарная покупка + запись цены сделки. */
    public boolean buyFromOffer(String productName, String seller, int qty,
                                double executionPrice, double newListedPrice) {
        final String find    = "SELECT id FROM products WHERE lower(name)=lower(?)";
        final String getQty  = "SELECT quantity FROM offers WHERE product_id=? AND lower(seller)=lower(?)";
        final String dec     = "UPDATE offers SET quantity=quantity-?, price=? WHERE product_id=? AND lower(seller)=lower(?)";
        final String insHist = "INSERT INTO price_history(product_id,price) VALUES (?,?)";

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            Object pidObj;
            try (PreparedStatement ps = c.prepareStatement(find)) {
                ps.setString(1, productName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    pidObj = rs.getObject(1);
                }
            }

            int available;
            try (PreparedStatement ps = c.prepareStatement(getQty)) {
                ps.setObject(1, pidObj);
                ps.setString(2, seller);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    available = rs.getInt(1);
                }
            }
            if (qty <= 0 || available < qty) { c.rollback(); return false; }

            try (PreparedStatement ps = c.prepareStatement(dec)) {
                ps.setInt(1, qty);
                ps.setDouble(2, newListedPrice);
                ps.setObject(3, pidObj);
                ps.setString(4, seller);
                ps.executeUpdate();
            }
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

    public AuthorizedUsers authenticate(String login, String password) {
    final String sql = """
        SELECT login, role
        FROM users
        WHERE lower(login) = lower(?) 
          AND password_hash = crypt(?, password_hash)
        """;
    try (Connection c = dataSource.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, login);
        ps.setString(2, password);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            return new AuthorizedUsers(rs.getString("login"), rs.getString("role"));
        }
    } catch (SQLException e) {
        throw new RuntimeException("authenticate failed", e);
    }
}
    @Override public void close() { /* no-op */ }
}

