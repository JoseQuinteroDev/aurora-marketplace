import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import {
  AdminBrandResponse,
  AdminCategoryResponse,
  AdminProductRequest,
  AdminProductResponse
} from '../core/models/admin-product.model';

@Injectable({ providedIn: 'root' })
export class AdminProductService {
  private readonly http = inject(HttpClient);

  /** All products, including inactive (soft-deleted) ones, newest first (server-sorted). */
  listProducts() {
    return this.http
      .get<ApiResponse<AdminProductResponse[]>>('/api/admin/products')
      .pipe(map((response) => response.data ?? []));
  }

  /** A single product in any state (active or inactive); 404 if missing. */
  getProduct(id: string) {
    return this.http
      .get<ApiResponse<AdminProductResponse>>(`/api/admin/products/${id}`)
      .pipe(map((response) => response.data));
  }

  createProduct(request: AdminProductRequest) {
    return this.http
      .post<ApiResponse<AdminProductResponse>>('/api/admin/products', request)
      .pipe(map((response) => response.data));
  }

  updateProduct(id: string, request: AdminProductRequest) {
    return this.http
      .put<ApiResponse<AdminProductResponse>>(`/api/admin/products/${id}`, request)
      .pipe(map((response) => response.data));
  }

  /** Soft-deletes (deactivates the product + all its variants). Reactivate by updating with `active: true`. */
  deleteProduct(id: string) {
    return this.http
      .delete<ApiResponse<void>>(`/api/admin/products/${id}`)
      .pipe(map((response) => response.data));
  }

  /** Active categories for the form dropdown (categoryId must reference an active one). */
  listCategories() {
    return this.http
      .get<ApiResponse<AdminCategoryResponse[]>>('/api/categories')
      .pipe(map((response) => response.data ?? []));
  }

  /** Active brands for the form dropdown (brandId must reference an active one). */
  listBrands() {
    return this.http
      .get<ApiResponse<AdminBrandResponse[]>>('/api/brands')
      .pipe(map((response) => response.data ?? []));
  }
}
