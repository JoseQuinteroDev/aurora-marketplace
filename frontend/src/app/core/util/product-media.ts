import { Product } from '../models/product.model';

/**
 * Branded, inline SVG placeholder used when a product has no image. Replaces the
 * previous random Unsplash fallbacks so missing media never shows an unrelated
 * stock photo. Encoded at module load so it can be used directly as an <img src>.
 */
const PLACEHOLDER_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300" role="img" aria-label="Aurora">
  <defs>
    <linearGradient id="auroraPlaceholder" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1F1D17"/>
      <stop offset="1" stop-color="#100F0C"/>
    </linearGradient>
  </defs>
  <rect width="400" height="300" fill="url(#auroraPlaceholder)"/>
  <path d="M200 96 L236 196 M200 96 L164 196 M178 168 H222" stroke="#F4F1EA" stroke-width="2.2" stroke-linecap="round" fill="none" stroke-opacity="0.16"/>
  <circle cx="200" cy="96" r="4.5" fill="#3E7C63" fill-opacity="0.8"/>
  <text x="200" y="236" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-size="13" letter-spacing="6" fill="#F4F1EA" fill-opacity="0.4">AURORA</text>
</svg>`;

export const PRODUCT_IMAGE_PLACEHOLDER = `data:image/svg+xml,${encodeURIComponent(PLACEHOLDER_SVG)}`;

/** Main product image, falling back to the first image, then the branded placeholder. */
export function productImage(product: Product): string {
  return product.images.find((image) => image.mainImage)?.url || product.images[0]?.url || PRODUCT_IMAGE_PLACEHOLDER;
}

/** Up to four gallery images from the product itself — no fabricated stock photos. */
export function productGallery(product: Product): string[] {
  // De-duplicate so the gallery's `track image` keys stay unique (avoids NG0955).
  const urls = [...new Set(product.images.map((image) => image.url).filter(Boolean))];
  return urls.length > 0 ? urls.slice(0, 4) : [productImage(product)];
}

/** Accessible alt text for a product image, preferring the image's own altText. */
export function productImageAlt(product: Product): string {
  return product.images.find((image) => image.mainImage)?.altText || product.images[0]?.altText || product.name;
}

/** The variant a buyer acts on by default: first active variant, else the first one. */
export function firstActiveVariantId(product: Product): string | null {
  return product.variants.find((variant) => variant.active)?.id ?? product.variants[0]?.id ?? null;
}
