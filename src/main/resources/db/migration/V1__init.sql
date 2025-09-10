-- products: one row per product
CREATE TABLE IF NOT EXISTS products (
  id        TEXT PRIMARY KEY,
  name      TEXT NOT NULL UNIQUE,
  category  TEXT NOT NULL
);

-- offers: one row per (product, seller)
CREATE TABLE IF NOT EXISTS offers (
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  seller     TEXT NOT NULL,
  price      DOUBLE PRECISION NOT NULL,
  quantity   INTEGER NOT NULL,
  PRIMARY KEY (product_id, seller)
);

-- price_history: executed trade prices per product
CREATE TABLE IF NOT EXISTS price_history (
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  price      DOUBLE PRECISION NOT NULL,
  ts         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_name     ON products(lower(name));
CREATE INDEX IF NOT EXISTS idx_products_category ON products(lower(category));
CREATE INDEX IF NOT EXISTS idx_offers_product    ON offers(product_id);
CREATE INDEX IF NOT EXISTS idx_price_hist_prod   ON price_history(product_id, ts DESC);
