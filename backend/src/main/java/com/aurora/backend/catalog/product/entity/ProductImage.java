package com.aurora.backend.catalog.product.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(nullable = false)
    private int position;

    @Column(name = "main_image", nullable = false)
    private boolean mainImage;

    protected ProductImage() {
    }

    public ProductImage(String url, String altText, int position, boolean mainImage) {
        this.url = url;
        this.altText = altText;
        this.position = position;
        this.mainImage = mainImage;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getUrl() {
        return url;
    }

    public String getAltText() {
        return altText;
    }

    public int getPosition() {
        return position;
    }

    public boolean isMainImage() {
        return mainImage;
    }

    void setProduct(Product product) {
        this.product = product;
    }
}
