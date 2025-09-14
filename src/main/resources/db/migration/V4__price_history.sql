ALTER TABLE price_history
    ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_price_history_prod_created
    ON price_history(product_id, created_at DESC);
