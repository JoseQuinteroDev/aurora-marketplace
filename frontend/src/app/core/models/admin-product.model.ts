/**
 * Admin catalog models for /api/admin/products.
 *
 * The *Response* shapes mirror what the backend returns (read side); the *Request*
 * shapes are the POST/PUT bodies (write side). They are intentionally separate:
 * a request carries no server-derived fields (effectivePrice, averageRating, …) and
 * lets optional fields be `null` to clear them.
 */

export interface AdminCategoryResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

export interface AdminBrandResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

export interface AdminProductVariantResponse {
  id: string;
  sku: string;
  name: string;
  priceOverride: number | null;
  effectivePrice: number;
  attributesJson: string | null;
  active: boolean;
}

export interface AdminProductImageResponse {
  id: string;
  url: string;
  altText: string | null;
  position: number;
  mainImage: boolean;
}

export interface AdminProductResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  shortDescription: string | null;
  basePrice: number;
  active: boolean;
  featured: boolean;
  category: AdminCategoryResponse;
  brand: AdminBrandResponse;
  variants: AdminProductVariantResponse[];
  images: AdminProductImageResponse[];
  averageRating: number | null;
  reviewCount: number;
}

/**
 * Variant row for POST/PUT.
 * - `id` null/absent → create a new variant.
 * - `id` present → update the existing variant.
 * On PUT, any existing variant NOT included in the request is DEACTIVATED, so the
 * edit form must resend every variant it wants to keep (with its id).
 */
export interface AdminProductVariantRequest {
  id?: string | null;
  sku: string;
  name: string;
  priceOverride?: number | null;
  attributesJson?: string | null;
  active?: boolean;
}

/**
 * Image row for POST/PUT. On PUT, the image list is REPLACE-ALL when non-null:
 * the backend clears the existing images and re-adds exactly what's sent.
 */
export interface AdminProductImageRequest {
  url: string;
  altText?: string | null;
  position?: number | null;
  mainImage?: boolean;
}

/** Body for POST/PUT /api/admin/products. */
export interface AdminProductRequest {
  name: string;
  slug?: string;
  description?: string | null;
  shortDescription?: string | null;
  basePrice: number;
  active?: boolean;
  featured?: boolean;
  categoryId: string;
  brandId: string;
  variants?: AdminProductVariantRequest[];
  images?: AdminProductImageRequest[];
}
