export interface Category {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

export interface Brand {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

export interface ProductVariant {
  id: string;
  sku: string;
  name: string;
  priceOverride: number | null;
  effectivePrice: number;
  attributesJson: string | null;
  active: boolean;
}

export interface ProductImage {
  id: string;
  url: string;
  altText: string | null;
  position: number;
  mainImage: boolean;
}

export interface Product {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  shortDescription: string | null;
  basePrice: number;
  active: boolean;
  featured: boolean;
  category: Category;
  brand: Brand;
  variants: ProductVariant[];
  images: ProductImage[];
  /** Real review aggregates from the backend; reviewCount is 0 when there are none. */
  averageRating?: number;
  reviewCount?: number;
}
