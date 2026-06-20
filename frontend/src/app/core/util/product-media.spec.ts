import { Product, ProductImage, ProductVariant } from '../models/product.model';
import {
  PRODUCT_IMAGE_PLACEHOLDER,
  firstActiveVariantId,
  productGallery,
  productImage,
  productImageAlt
} from './product-media';

function image(over: Partial<ProductImage>): ProductImage {
  return { id: 'i', url: 'x', altText: null, position: 0, mainImage: false, ...over };
}

function variant(over: Partial<ProductVariant>): ProductVariant {
  return { id: 'v', sku: 'SKU', name: 'V', priceOverride: null, effectivePrice: 10, attributesJson: null, active: true, ...over };
}

function product(over: Partial<Product> = {}): Product {
  return {
    id: 'p1', name: 'Test Product', slug: 'test', description: null, shortDescription: null,
    basePrice: 10, active: true, featured: false,
    category: { id: 'c', name: 'Cat', slug: 'cat', active: true },
    brand: { id: 'b', name: 'Brand', slug: 'brand', active: true },
    variants: [], images: [],
    ...over
  };
}

describe('product-media', () => {
  it('PRODUCT_IMAGE_PLACEHOLDER is an inline SVG data URI', () => {
    expect(PRODUCT_IMAGE_PLACEHOLDER.startsWith('data:image/svg+xml,')).toBe(true);
  });

  describe('productImage', () => {
    it('prefers the main image, then the first, then the placeholder', () => {
      expect(productImage(product({ images: [image({ url: 'a' }), image({ url: 'b', mainImage: true })] }))).toBe('b');
      expect(productImage(product({ images: [image({ url: 'a' }), image({ url: 'b' })] }))).toBe('a');
      expect(productImage(product({ images: [] }))).toBe(PRODUCT_IMAGE_PLACEHOLDER);
    });
  });

  describe('productGallery', () => {
    it('de-duplicates urls and caps at four', () => {
      const p = product({ images: [image({ url: 'a' }), image({ url: 'a' }), image({ url: 'b' }), image({ url: 'c' }), image({ url: 'd' }), image({ url: 'e' })] });
      expect(productGallery(p)).toEqual(['a', 'b', 'c', 'd']);
    });

    it('falls back to a single placeholder when there are no images', () => {
      expect(productGallery(product({ images: [] }))).toEqual([PRODUCT_IMAGE_PLACEHOLDER]);
    });
  });

  describe('firstActiveVariantId', () => {
    it('returns the first active variant, else the first, else null', () => {
      expect(firstActiveVariantId(product({ variants: [variant({ id: 'x', active: false }), variant({ id: 'y', active: true })] }))).toBe('y');
      expect(firstActiveVariantId(product({ variants: [variant({ id: 'x', active: false })] }))).toBe('x');
      expect(firstActiveVariantId(product({ variants: [] }))).toBeNull();
    });
  });

  describe('productImageAlt', () => {
    it('prefers the image altText and falls back to the product name', () => {
      expect(productImageAlt(product({ name: 'Phone', images: [image({ mainImage: true, altText: 'Black phone' })] }))).toBe('Black phone');
      expect(productImageAlt(product({ name: 'Phone', images: [image({ altText: null })] }))).toBe('Phone');
      expect(productImageAlt(product({ name: 'Phone', images: [] }))).toBe('Phone');
    });
  });
});
