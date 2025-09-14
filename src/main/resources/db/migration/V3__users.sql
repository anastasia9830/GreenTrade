CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  login         TEXT UNIQUE NOT NULL,
  password_hash TEXT       NOT NULL,
  role          TEXT       NOT NULL CHECK (role IN ('admin','seller'))
);


INSERT INTO users (login, password_hash, role) VALUES
 ('claudia_schmidt', crypt('1234', gen_salt('bf')), 'admin'),
 ('maria_morozova',  crypt('5678', gen_salt('bf')), 'admin'),
 ('felix_becker',    crypt('1742', gen_salt('bf')), 'admin'),
 ('mark_edelstein',  crypt('3458', gen_salt('bf')), 'seller'),
 ('vkussvill',       crypt('1111', gen_salt('bf')), 'seller'),
 ('fresh_farms',     crypt('1151', gen_salt('bf')), 'seller'),
 ('prospekt',        crypt('4511', gen_salt('bf')), 'seller'),
 ('pyaterochka',     crypt('1781', gen_salt('bf')), 'seller'),
 ('lenta',           crypt('9811', gen_salt('bf')), 'seller'),
 ('zhabka',          crypt('1561', gen_salt('bf')), 'seller'),
 ('sofia_miller',    crypt('4561', gen_salt('bf')), 'seller'),
 ('karl_fisher',     crypt('1987', gen_salt('bf')), 'seller'),
 ('mia_mecklenburg', crypt('2022', gen_salt('bf')), 'seller'),
 ('ivan_nowak',      crypt('1997', gen_salt('bf')), 'seller'),
 ('kristina_tarakanova', crypt('2002', gen_salt('bf')), 'seller'),
 ('life_gmbh',       crypt('4567', gen_salt('bf')), 'seller'),
 ('frish',           crypt('5674', gen_salt('bf')), 'seller'),
 ('gesundheit_gmbh', crypt('1987', gen_salt('bf')), 'seller'),
 ('oleniy_kopyta',   crypt('1923', gen_salt('bf')), 'seller'),
 ('mir',             crypt('2027', gen_salt('bf')), 'seller'),
 ('molto_bene',      crypt('2019', gen_salt('bf')), 'seller'),
 ('tvoy_den',        crypt('2025', gen_salt('bf')), 'seller'),
 ('arizona',         crypt('2021', gen_salt('bf')), 'seller')
ON CONFLICT (login) DO NOTHING;
