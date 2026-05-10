CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    type VARCHAR(50) NOT NULL,
    value NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    max_uses INTEGER,
    max_uses_per_user INTEGER,
    minimum_order_amount NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT chk_coupons_value_non_negative CHECK (value >= 0),
    CONSTRAINT chk_coupons_max_uses_positive CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_coupons_max_uses_per_user_positive CHECK (max_uses_per_user IS NULL OR max_uses_per_user > 0),
    CONSTRAINT chk_coupons_minimum_order_non_negative CHECK (minimum_order_amount IS NULL OR minimum_order_amount >= 0)
);

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    coupon_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_carts_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cart_items_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0)
);

CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_wishlist_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_items_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    title VARCHAR(160),
    comment TEXT,
    verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_reviews_rating_range CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE coupon_usages (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL,
    user_id UUID NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_coupon_usages_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_usages_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_carts_user_id ON carts (user_id);
CREATE INDEX idx_cart_items_variant_id ON cart_items (variant_id);
CREATE INDEX idx_wishlist_items_user_id ON wishlist_items (user_id);
CREATE INDEX idx_wishlist_items_product_id ON wishlist_items (product_id);
CREATE INDEX idx_reviews_product_id ON reviews (product_id);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_active ON reviews (active);
CREATE INDEX idx_coupons_active ON coupons (active);
CREATE INDEX idx_coupon_usages_coupon_id ON coupon_usages (coupon_id);
CREATE INDEX idx_coupon_usages_user_id ON coupon_usages (user_id);
