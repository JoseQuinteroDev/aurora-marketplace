export type CouponType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface CartCoupon {
  id: string;
  code: string;
  type: CouponType;
  value: number;
}

export interface CartItem {
  id: string;
  productId: string;
  productName: string;
  productSlug: string;
  variantId: string;
  variantSku: string;
  variantName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Cart {
  id: string;
  items: CartItem[];
  coupon: CartCoupon | null;
  subtotal: number;
  discountTotal: number;
  total: number;
}

export interface AddCartItemRequest {
  variantId: string;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

export interface ApplyCouponRequest {
  code: string;
}
