export type CouponType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface AdminCoupon {
  id: string;
  code: string;
  type: CouponType;
  value: number;
  active: boolean;
  startsAt: string | null;
  endsAt: string | null;
  maxUses: number | null;
  maxUsesPerUser: number | null;
  minimumOrderAmount: number | null;
}

/** Body for POST/PUT /api/admin/coupons. Optional fields may be null to clear them. */
export interface AdminCouponRequest {
  code: string;
  type: CouponType;
  value: number;
  active?: boolean;
  startsAt?: string | null;
  endsAt?: string | null;
  maxUses?: number | null;
  maxUsesPerUser?: number | null;
  minimumOrderAmount?: number | null;
}
