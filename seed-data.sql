-- Aurora Marketplace — local demo seed
-- Idempotent-ish: clears demo rows then reinserts. Safe on a fresh DB.

-- Promote the admin test user (registered via API so the BCrypt hash is valid).
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@aurora.test';

-- Catalog: one category + one brand, then a handful of products.
INSERT INTO categories (id, name, slug, active)
VALUES ('11111111-1111-1111-1111-111111111111', 'Electronics', 'electronics', TRUE)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO brands (id, name, slug, active)
VALUES ('22222222-2222-2222-2222-222222222222', 'Aurora Labs', 'aurora-labs', TRUE)
ON CONFLICT (slug) DO NOTHING;

DO $$
DECLARE
    cat UUID := '11111111-1111-1111-1111-111111111111';
    br  UUID := '22222222-2222-2222-2222-222222222222';
    p   UUID;
    v   UUID;
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            ('Aurora Wireless Headphones', 'aurora-wireless-headphones', 'Noise-cancelling over-ear headphones with 40h battery.', 199.99, TRUE,  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600'),
            ('Aurora Smartwatch S2',       'aurora-smartwatch-s2',       'AMOLED fitness smartwatch with GPS and SpO2.',          149.50, TRUE,  'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600'),
            ('Aurora 4K Action Camera',    'aurora-4k-action-camera',    'Waterproof 4K60 action cam with stabilization.',        249.00, FALSE, 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=600'),
            ('Aurora Mechanical Keyboard', 'aurora-mechanical-keyboard', 'Hot-swappable RGB mechanical keyboard, 75% layout.',     89.99, TRUE,  'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600'),
            ('Aurora USB-C Hub 8-in-1',    'aurora-usbc-hub-8in1',       '8-in-1 USB-C hub: HDMI, SD, Ethernet, 100W PD.',         59.95, FALSE, 'https://images.unsplash.com/photo-1625842268584-8f3296236761?w=600'),
            ('Aurora Bluetooth Speaker',   'aurora-bluetooth-speaker',   'Portable 360° speaker, IPX7, 24h playtime.',             79.00, TRUE,  'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=600')
        ) AS t(name, slug, descr, price, featured, img)
    LOOP
        p := gen_random_uuid();
        v := gen_random_uuid();

        INSERT INTO products (id, name, slug, description, short_description, base_price, active, featured, category_id, brand_id)
        VALUES (p, rec.name, rec.slug, rec.descr, rec.descr, rec.price, TRUE, rec.featured, cat, br);

        INSERT INTO product_variants (id, product_id, sku, name, active)
        VALUES (v, p, 'SKU-' || upper(substr(rec.slug, 1, 12)) || '-' || substr(p::text, 1, 4), 'Default', TRUE);

        INSERT INTO inventory (id, variant_id, available_quantity, reserved_quantity, low_stock_threshold)
        VALUES (gen_random_uuid(), v, 100, 0, 5);

        INSERT INTO product_images (id, product_id, url, alt_text, position, main_image)
        VALUES (gen_random_uuid(), p, rec.img, rec.name, 0, TRUE);
    END LOOP;
END $$;

SELECT 'users' AS entity, count(*) FROM users
UNION ALL SELECT 'products', count(*) FROM products
UNION ALL SELECT 'variants', count(*) FROM product_variants
UNION ALL SELECT 'images', count(*) FROM product_images;
