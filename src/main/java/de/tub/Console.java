package de.tub;

import lombok.extern.java.Log;

import java.util.List;
import java.util.Objects;

@Log
public class Console {

    private final Market market;
    private final java.util.Scanner scanner;
    private AuthorizedUsers currentUser;

    // --- constructors ---
    public Console() {
        this(new Market(), new java.util.Scanner(System.in));
    }

    public Console(Market market, java.util.Scanner scanner) {
        this.market = Objects.requireNonNull(market);
        this.scanner = Objects.requireNonNull(scanner);
    }

    // for tests
    public void setCurrentUser(AuthorizedUsers user) { this.currentUser = user; }

    // ---------- strict input helpers (fixed order) ----------
    private String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("Please enter a non-empty value.");
        }
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter an integer number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim().replace(',', '.');
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a decimal number, e.g. 1.23");
            }
        }
    }

    // ---------- actions ----------
    /** Admin adds/updates a product; optionally creates initial "Stock" offer. */
    public void addProduct() {
        String id       = readNonEmpty("ID: ");
        String name     = readNonEmpty("Name: ");
        String category = readNonEmpty("Category: ");
        double price    = readDouble("Initial price: ");
        int qty         = readInt("Initial quantity: ");

        market.addProductModel(id, name, category, 0);

        ProductOffer stock = ProductOffer.builder()
                .seller("Stock")
                .price(price)
                .quantity(qty)
                .build();

        market.addOfferToExistingProduct(name, stock);
        System.out.println("[OK] Product added/updated.");
    }

    /** Seller creates/updates own offer (fixed order: Product, Quantity, Price). */
    public void sellItem() {
        ensureLoggedIn("seller");
        if (!isRole("seller")) {
            printlnError("Access denied (seller required).");
            return;
        }

        String product = readNonEmpty("Product: ");
        int qty        = readInt("Quantity to add: ");
        double price   = readDouble("New price: ");

        ProductOffer offer = ProductOffer.builder()
                .seller(currentUser != null ? currentUser.getLogin() : "unknown")
                .price(price)
                .quantity(qty)
                .build();

        boolean ok = market.addOfferToExistingProduct(product, offer);
        if (!ok) ok = market.updateOffer(product, offer.getSeller(), qty, price);

        System.out.println(ok ? "[OK] Offer upserted." : "[FAIL] Offer update failed.");
    }

    /** Buy from a seller (fixed order: Product, Seller, Quantity). */
    public void buyItem() {
        String product = readNonEmpty("Product: ");
        String seller  = readNonEmpty("Seller: ");
        int qty        = readInt("How much do you want to buy: ");

        boolean ok = market.buyFromOffer(product, seller, qty);
        if (ok) {
            System.out.println("[OK] Bought " + qty + " of " + product + " from " + seller);
            ProductOffer o = market.getOffer(product, seller);
            if (o != null) {
                System.out.println("[INFO] New listed price: " + o.getPrice()
                        + ", remaining qty: " + o.getQuantity());
                System.out.println("[INFO] Offer price history: " + o.getPriceHistory());
            }
        } else {
            ProductModel m = market.findModelByName(product);
            if (m == null) {
                System.out.println("[FAIL] Product not found: " + product);
            } else {
                ProductOffer o = market.getOffer(product, seller);
                if (o == null) {
                    System.out.println("[FAIL] Seller offer not found: " + seller + " for " + product);
                    System.out.println("[HINT] Available sellers: " +
                            m.getOffers().stream().map(ProductOffer::getSeller).toList());
                } else if (qty <= 0) {
                    System.out.println("[FAIL] Quantity must be positive.");
                } else if (o.getQuantity() < qty) {
                    System.out.println("[FAIL] Not enough stock in seller offer. Available: " + o.getQuantity());
                } else {
                    System.out.println("[FAIL] Purchase failed (unknown reason).");
                }
            }
        }
    }

    /** Show last 3 trade execution prices (product-level). */
    private void showHistory() {
        String name = readNonEmpty("Product name: ");
        List<Double> last3 = market.getLastTradePrices(name, 3);
        if (last3 == null || last3.isEmpty()) {
            System.out.println("No trade history yet.");
            return;
        }
        String s = last3.stream()
                .map(d -> String.format(java.util.Locale.US, "%.2f", d))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        System.out.println("Last 3 trade prices for \"" + name + "\": " + s);
    }

    // ---------- main loop ----------
    public void start() {
        clearScreen();
        printBanner();

        while (true) {
            printMenu();
            int choice = readMenuChoice(1, 9);

            switch (choice) {
                case 1 -> {
                    printlnInfo("You chose: List all products");
                    listItems();
                    promptEnterToContinue();
                }
                case 2 -> {
                    printlnInfo("You chose: Search products");
                    searchItems();
                    promptEnterToContinue();
                }
                case 3 -> {
                    printlnInfo("You chose: Add product (admin)");
                    ensureLoggedIn("admin");
                    if (isRole("admin")) {
                        printlnHint("Enter line by line: ID, Name, Category, Initial price, Initial quantity");
                        addProduct();
                    } else {
                        printlnError("Access denied (admin required).");
                    }
                    promptEnterToContinue();
                }
                case 4 -> {
                    printlnInfo("You chose: Manage your offers (seller)");
                    ensureLoggedIn("seller");
                    if (isRole("seller")) {
                        printlnHint("Enter line by line: Product, Quantity, Price");
                        sellItem();
                    } else {
                        printlnError("Access denied (seller required).");
                    }
                    promptEnterToContinue();
                }
                case 5 -> {
                    printlnInfo("You chose: Buy product");
                    printlnHint("Enter line by line: Product, Seller, Quantity");
                    buyItem();
                    promptEnterToContinue();
                }
                case 6 -> {
                    printlnInfo("You chose: Sell product (seller)");
                    ensureLoggedIn("seller");
                    if (isRole("seller")) {
                        printlnHint("Enter line by line: Product, Quantity, Price");
                        sellItem();
                    } else {
                        printlnError("Access denied (seller required).");
                    }
                    promptEnterToContinue();
                }
                case 7 -> {
                    printlnInfo("You chose: Show price history");
                    showHistory();
                    promptEnterToContinue();
                }
                case 8 -> {
                    printlnInfo("You chose: Login");
                    login();
                    promptEnterToContinue();
                }
                case 9 -> {
                    printlnInfo("Bye!");
                    return;
                }
                default -> printlnError("Unknown option.");
            }
            clearScreen();
        }
    }

    // ---------- auth helpers ----------
    private boolean isRole(String role) {
        return currentUser != null && role.equalsIgnoreCase(currentUser.getRole());
    }

    private void ensureLoggedIn(String requiredRole) {
        if (!isRole(requiredRole)) login();
    }

    /** DB-backed login via Market (bcrypt/pgcrypto). */
    private void login() {
        String login = readNonEmpty("Login: ");
        String password = readNonEmpty("Password: ");

        AuthorizedUsers u = market.login(login, password); // repo.authenticate(...)
        if (u == null) {
            System.out.println("Invalid credentials.");
            currentUser = null;
        } else {
            currentUser = u;
            System.out.println("Logged in as " + u.getLogin() + " (" + u.getRole() + ")");
        }
    }

    // ---------- list/search ----------
    private void listItems() {
        List<ProductModel> models = market.listAllModels();
        if (models.isEmpty()) {
            System.out.println("(no items)");
            return;
        }
        for (ProductModel m : models) {
            System.out.println(m);
            if (m.getOffers() != null) {
                for (ProductOffer o : m.getOffers()) System.out.println("  -> " + o);
            }
        }
    }

    private void searchItems() {
        String q = readNonEmpty("Search by name or category: ");
        List<ProductModel> results = market.searchModels(q);
        if (results == null || results.isEmpty()) {
            System.out.println("No results.");
            return;
        }
        System.out.println("Found:");
        for (ProductModel model : results) {
            System.out.println("  Product: " + model.getName() + " | Category: " + model.getCategory());
            if (model.getOffers() != null) {
                for (ProductOffer o : model.getOffers()) {
                    System.out.println("    -> Seller: " + o.getSeller()
                            + ", Price: " + o.getPrice()
                            + ", Quantity: " + o.getQuantity());
                }
            }
        }
    }

    // ---------- UI helpers ----------
    private void printBanner() {
        System.out.println("""
            ==========================================
                     Welcome to the Market
            ==========================================
            """);
    }

    private void printMenu() {
        System.out.println("""
            What do you want to do?
              1) List all products
              2) Search products
              3) Add product (admin)
              4) Manage your offers (seller)
              5) Buy product
              6) Sell product (seller)
              7) Show price history
              8) Login
              9) Exit
            """);
        if (currentUser != null) {
            System.out.println("Current user: " + currentUser.getLogin()
                    + " [" + currentUser.getRole() + "]");
        } else {
            System.out.println("You are not logged in. Some actions will require login.");
        }
        System.out.print("\nYour choice (1-9): ");
    }

    private int readMenuChoice(int min, int max) {
        while (true) {
            String raw = scanner.nextLine().trim();
            try {
                int n = Integer.parseInt(raw);
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) {}
            System.out.print("Enter a number from " + min + " to " + max + ": ");
        }
    }

    private void promptEnterToContinue() {
        System.out.print("\nPress Enter to continue…");
        scanner.nextLine();
    }

    private void clearScreen() {
        try {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        } catch (Exception ignored) {}
    }

    private void printlnInfo(String msg)  { System.out.println("[INFO] " + msg); }
    private void printlnError(String msg) { System.out.println("[ERROR] " + msg); }
    private void printlnHint(String msg)  { System.out.println("[HINT] " + msg); }
}
