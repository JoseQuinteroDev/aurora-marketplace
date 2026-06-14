-- Aurora Marketplace — local demo seed
-- Idempotent: skips products whose slug already exists, so it is safe to re-run.
-- Designed for a fresh DB (Flyway builds the schema; this fills the catalog).

-- Promote the admin test user (registered via API so the BCrypt hash is valid).
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@aurora.test';

-- Categories (slug is what the storefront filters on).
INSERT INTO categories (id, name, slug, active) VALUES
    (gen_random_uuid(), 'Audio',        'audio',        TRUE),
    (gen_random_uuid(), 'Computing',    'computing',    TRUE),
    (gen_random_uuid(), 'Wearables',    'wearables',    TRUE),
    (gen_random_uuid(), 'Cameras',      'cameras',      TRUE),
    (gen_random_uuid(), 'Smart Home',   'smart-home',   TRUE),
    (gen_random_uuid(), 'Gaming',       'gaming',       TRUE),
    (gen_random_uuid(), 'Travel',       'travel',       TRUE),
    (gen_random_uuid(), 'Accessories',  'accessories',  TRUE)
ON CONFLICT (slug) DO NOTHING;

-- Brands.
INSERT INTO brands (id, name, slug, active) VALUES
    (gen_random_uuid(), 'Aurora Labs', 'aurora-labs', TRUE),
    (gen_random_uuid(), 'Northwind',   'northwind',   TRUE),
    (gen_random_uuid(), 'Lumen',       'lumen',       TRUE),
    (gen_random_uuid(), 'Vantage',     'vantage',     TRUE),
    (gen_random_uuid(), 'Cobalt',      'cobalt',      TRUE),
    (gen_random_uuid(), 'Pulse',       'pulse',       TRUE),
    (gen_random_uuid(), 'Atlas',       'atlas',       TRUE),
    (gen_random_uuid(), 'Nimbus',      'nimbus',      TRUE),
    (gen_random_uuid(), 'Halo',        'halo',        TRUE),
    (gen_random_uuid(), 'Strato',      'strato',      TRUE),
    (gen_random_uuid(), 'Verge',       'verge',       TRUE),
    (gen_random_uuid(), 'Forge',       'forge',       TRUE)
ON CONFLICT (slug) DO NOTHING;

DO $$
DECLARE
    p   UUID;
    v   UUID;
    cat UUID;
    br  UUID;
    img TEXT;
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            -- name, slug, description, price, featured, category-slug, brand-slug
            ('Aurora Wireless Headphones','aurora-wireless-headphones','Noise-cancelling over-ear headphones with 40h battery.',199.99,TRUE,'audio','aurora-labs'),
            ('Northwind Studio Monitors','northwind-studio-monitors','Pair of active studio monitors with balanced, detailed sound.',349.00,FALSE,'audio','northwind'),
            ('Pulse Bluetooth Speaker','pulse-bluetooth-speaker','Portable 360° speaker, IPX7 waterproof, 24h playtime.',79.00,TRUE,'audio','pulse'),
            ('Lumen Earbuds Pro','lumen-earbuds-pro','True-wireless earbuds with adaptive noise cancelling.',149.00,TRUE,'audio','lumen'),
            ('Halo Soundbar 5.1','halo-soundbar-51','Immersive 5.1 soundbar with wireless subwoofer.',299.00,FALSE,'audio','halo'),
            ('Atlas Turntable','atlas-turntable','Belt-drive turntable with built-in preamp.',229.00,FALSE,'audio','atlas'),

            ('Aurora Mechanical Keyboard','aurora-mechanical-keyboard','Hot-swappable RGB mechanical keyboard, 75% layout.',89.99,TRUE,'computing','aurora-labs'),
            ('Vantage UltraBook 14','vantage-ultrabook-14','Thin-and-light 14" laptop with all-day battery.',1299.00,TRUE,'computing','vantage'),
            ('Cobalt 27" 4K Monitor','cobalt-27-4k-monitor','27-inch 4K IPS monitor with USB-C 90W power delivery.',449.00,TRUE,'computing','cobalt'),
            ('Aurora USB-C Hub 8-in-1','aurora-usbc-hub-8in1','8-in-1 USB-C hub: HDMI, SD, Ethernet and 100W PD.',59.95,FALSE,'computing','aurora-labs'),
            ('Forge Wireless Mouse','forge-wireless-mouse','Ergonomic wireless mouse with silent clicks.',39.00,FALSE,'computing','forge'),
            ('Strato Docking Station','strato-docking-station','Triple-display USB-C docking station.',189.00,FALSE,'computing','strato'),
            ('Vantage Mini PC','vantage-mini-pc','Compact desktop with 16GB RAM and 512GB SSD.',699.00,FALSE,'computing','vantage'),

            ('Aurora Smartwatch S2','aurora-smartwatch-s2','AMOLED fitness smartwatch with GPS and SpO2.',149.50,TRUE,'wearables','aurora-labs'),
            ('Pulse Fitness Band 3','pulse-fitness-band-3','Slim activity band with 14-day battery life.',49.00,FALSE,'wearables','pulse'),
            ('Lumen Smart Ring','lumen-smart-ring','Sleep and recovery tracking in a titanium smart ring.',279.00,TRUE,'wearables','lumen'),
            ('Halo Sport Watch','halo-sport-watch','Rugged GPS watch for trail running and triathlon.',329.00,FALSE,'wearables','halo'),

            ('Aurora 4K Action Camera','aurora-4k-action-camera','Waterproof 4K60 action cam with stabilization.',249.00,TRUE,'cameras','aurora-labs'),
            ('Vantage Mirrorless X','vantage-mirrorless-x','24MP mirrorless camera with a versatile kit lens.',899.00,TRUE,'cameras','vantage'),
            ('Nimbus Drone Air','nimbus-drone-air','Foldable 4K camera drone with 34-min flight time.',759.00,TRUE,'cameras','nimbus'),
            ('Cobalt Webcam 4K','cobalt-webcam-4k','4K webcam with auto-framing and dual microphones.',129.00,FALSE,'cameras','cobalt'),
            ('Atlas Instant Camera','atlas-instant-camera','Instant film camera with a retro design.',89.00,FALSE,'cameras','atlas'),

            ('Lumen Smart Bulb 4-pack','lumen-smart-bulb-4pack','Color smart bulbs with app and voice control.',59.00,FALSE,'smart-home','lumen'),
            ('Nimbus Video Doorbell','nimbus-video-doorbell','1080p video doorbell with night vision.',119.00,TRUE,'smart-home','nimbus'),
            ('Atlas Smart Thermostat','atlas-smart-thermostat','Learning thermostat that trims your energy use.',179.00,FALSE,'smart-home','atlas'),
            ('Halo Robot Vacuum','halo-robot-vacuum','LiDAR robot vacuum with a self-empty base.',429.00,TRUE,'smart-home','halo'),
            ('Cobalt Smart Plug 2-pack','cobalt-smart-plug-2pack','Energy-monitoring smart plugs, pack of two.',24.99,FALSE,'smart-home','cobalt'),

            ('Pulse Gaming Headset','pulse-gaming-headset','Surround gaming headset with a detachable mic.',99.00,TRUE,'gaming','pulse'),
            ('Forge Pro Gamepad','forge-pro-gamepad','Pro controller with mappable back buttons.',79.00,FALSE,'gaming','forge'),
            ('Cobalt Gaming Monitor 240Hz','cobalt-gaming-monitor-240hz','27" 240Hz 1ms gaming monitor.',379.00,TRUE,'gaming','cobalt'),
            ('Vantage Gaming Laptop 16','vantage-gaming-laptop-16','16" gaming laptop with RTX graphics.',1799.00,TRUE,'gaming','vantage'),
            ('Strato Streaming Mic','strato-streaming-mic','USB condenser mic for streaming and podcasts.',119.00,FALSE,'gaming','strato'),

            ('Atlas Carry-On Pro','atlas-carry-on-pro','Hardshell carry-on with TSA lock and USB port.',229.00,TRUE,'travel','atlas'),
            ('Nimbus Travel Backpack 30L','nimbus-travel-backpack-30l','Expandable 30L backpack, laptop-ready.',139.00,TRUE,'travel','nimbus'),
            ('Verge Packing Cubes Set','verge-packing-cubes-set','Set of six lightweight packing cubes.',39.00,FALSE,'travel','verge'),
            ('Strato Travel Adapter','strato-travel-adapter','Universal adapter with 3 USB ports and GaN PD.',45.00,FALSE,'travel','strato'),
            ('Lumen Neck Pillow','lumen-neck-pillow','Memory-foam travel pillow with a carry clip.',29.00,FALSE,'travel','lumen'),

            ('Aurora Power Bank 20K','aurora-power-bank-20k','20,000mAh power bank with 65W USB-C output.',69.00,TRUE,'accessories','aurora-labs'),
            ('Forge Wireless Charger','forge-wireless-charger','3-in-1 wireless charging stand.',49.00,FALSE,'accessories','forge'),
            ('Verge Laptop Sleeve 14','verge-laptop-sleeve-14','Felt and leather laptop sleeve.',35.00,FALSE,'accessories','verge'),
            ('Cobalt USB-C Cable 3-pack','cobalt-usbc-cable-3pack','Braided 100W USB-C cables, pack of three.',19.00,FALSE,'accessories','cobalt'),
            ('Halo Phone Stand','halo-phone-stand','Adjustable aluminum phone stand.',22.00,FALSE,'accessories','halo'),
            ('Strato Desk Mat XL','strato-desk-mat-xl','Extended desk mat with stitched edges.',29.00,FALSE,'accessories','strato')
        ) AS t(name, slug, descr, price, featured, cat_slug, brand_slug)
    LOOP
        -- Idempotent: leave existing products untouched.
        CONTINUE WHEN EXISTS (SELECT 1 FROM products WHERE slug = rec.slug);

        SELECT id INTO cat FROM categories WHERE slug = rec.cat_slug;
        SELECT id INTO br  FROM brands     WHERE slug = rec.brand_slug;

        img := 'https://images.unsplash.com/photo-' || CASE rec.cat_slug
            WHEN 'audio'       THEN '1505740420928-5e560c06d30e'
            WHEN 'computing'   THEN '1496181133206-80ce9b88a853'
            WHEN 'wearables'   THEN '1523275335684-37898b6baf30'
            WHEN 'cameras'     THEN '1526170375885-4d8ecf77b99f'
            WHEN 'smart-home'  THEN '1519710164239-da123dc03ef4'
            WHEN 'gaming'      THEN '1587829741301-dc798b83add3'
            WHEN 'travel'      THEN '1553531384-cc64ac80f931'
            ELSE                    '1625842268584-8f3296236761'
        END || '?auto=format&fit=crop&w=800&q=80';

        p := gen_random_uuid();
        v := gen_random_uuid();

        INSERT INTO products (id, name, slug, description, short_description, base_price, active, featured, category_id, brand_id)
        VALUES (p, rec.name, rec.slug, rec.descr, rec.descr, rec.price, TRUE, rec.featured, cat, br);

        INSERT INTO product_variants (id, product_id, sku, name, active)
        VALUES (v, p, 'SKU-' || upper(substr(rec.slug, 1, 14)) || '-' || substr(p::text, 1, 4), 'Default', TRUE);

        INSERT INTO inventory (id, variant_id, available_quantity, reserved_quantity, low_stock_threshold)
        VALUES (gen_random_uuid(), v, 60, 0, 5);

        INSERT INTO product_images (id, product_id, url, alt_text, position, main_image)
        VALUES (gen_random_uuid(), p, img, rec.name, 0, TRUE);
    END LOOP;
END $$;

SELECT 'categories' AS entity, count(*) FROM categories
UNION ALL SELECT 'brands', count(*) FROM brands
UNION ALL SELECT 'products', count(*) FROM products
UNION ALL SELECT 'variants', count(*) FROM product_variants
UNION ALL SELECT 'images', count(*) FROM product_images;
