export interface Review {
  id: string;
  productId: string;
  authorName: string;
  rating: number;
  title: string | null;
  comment: string | null;
  verifiedPurchase: boolean;
  createdAt: string;
}

export interface ReviewRequest {
  rating: number;
  title: string | null;
  comment: string | null;
}
