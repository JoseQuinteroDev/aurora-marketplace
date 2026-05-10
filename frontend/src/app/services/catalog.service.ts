import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Brand, Category, Product } from '../core/models/product.model';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  constructor(private readonly http: HttpClient) {}

  getProducts() {
    return this.http.get<ApiResponse<Product[]>>('/api/products').pipe(map((response) => response.data ?? []));
  }

  searchProducts(query: string) {
    return this.http
      .get<ApiResponse<Product[]>>('/api/products/search', { params: { q: query } })
      .pipe(map((response) => response.data ?? []));
  }

  getProduct(slug: string) {
    return this.http.get<ApiResponse<Product>>(`/api/products/${slug}`).pipe(map((response) => response.data));
  }

  getCategories() {
    return this.http.get<ApiResponse<Category[]>>('/api/categories').pipe(map((response) => response.data ?? []));
  }

  getBrands() {
    return this.http.get<ApiResponse<Brand[]>>('/api/brands').pipe(map((response) => response.data ?? []));
  }
}
