import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Review, ReviewRequest } from '../core/models/review.model';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private readonly http: HttpClient) {}

  list(productId: string) {
    return this.http
      .get<ApiResponse<Review[]>>(`/api/products/${productId}/reviews`)
      .pipe(map((response) => response.data ?? []));
  }

  create(productId: string, request: ReviewRequest) {
    return this.http
      .post<ApiResponse<Review>>(`/api/products/${productId}/reviews`, request)
      .pipe(map((response) => response.data));
  }
}
