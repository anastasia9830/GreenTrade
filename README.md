Running with Docker (recommended):
./gradlew clean build
docker compose build app
docker compose up -d db
docker compose run --rm -it app

The app reads DB configuration from env (set in docker-compose.yml): 
DB_URL=jdbc:postgresql://db:5432/market
DB_USER=market
DB_PASSWORD=market

GreenTrade is a small console marketplace written in Java.

Core features:

1. List & search product models
2. Admin adds products; sellers create/update offers (price + quantity)
3. Buy from a seller: quantity decreases, price is re-calculated (see PriceCalculator)
4. Product price history (last trades)
5. Login with roles (admin, seller) when DB mode is enabled
6. Passwords are stored as bcrypt hashes using pgcrypto.

Tech

1. Java + Gradle
2.JDBC + HikariCP
3.PostgreSQL + Flyway migrations (src/main/resources/db/migration)
4. Docker, Docker Compose

Demo logins (DB mode):
Admin: claudia_schmidt / 1234
Seller: lenta / 9811
(Full list defined in V3__users.sql.)


Products & offers are inserted by V2__seed_demo.sql (e.g., Bread sold by zhabka)

DB checks:
docker compose exec db psql -U market -d market -c "SELECT COUNT(*) FROM products;"

docker compose exec db psql -U market -d market -c "SELECT COUNT(*) FROM offers;"


Console:
1) List all products
2) Search products
3) Add product (admin)
4) Manage your offers (seller)
5) Buy product
6) Sell product (seller)
7) Show price history
8) Login
9) Exit
Input is strict, line by line. Examples:
Add product (admin): ID, Name, Category, Initial price, Initial quantity
Sell (seller): Product, Quantity, Price
Buy: Product, Seller, Quantity
Press 1 to List all products (you should see demo items with sellers and quantities).
Press 5 for Buy product and answer the prompts:
[INFO] You chose: Buy product
[HINT] Enter line by line: Product, Seller, Quantity
Product: Bread
Seller: zhabka
How much do you want to buy: 3



NOTA BENE!
“(no items)”: DB not seeded or you’re in in-memory mode. Ensure migrations ran (look for Flyway logs) or seed demo (V2__seed_demo.sql).
“Invalid credentials.”: You’re in in-memory mode (no login) or used wrong password. In Docker, use demo accounts above.
Price history empty: It fills only after Buy operations.
