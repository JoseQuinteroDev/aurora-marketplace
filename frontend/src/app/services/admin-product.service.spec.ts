import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  AdminBrandResponse,
  AdminCategoryResponse,
  AdminProductRequest,
  AdminProductResponse
} from '../core/models/admin-product.model';
import { AdminProductService } from './admin-product.service';

function product(overrides: Partial<AdminProductResponse> = {}): AdminProductResponse {
  return {
    id: 'p1',
    name: 'Aurora Headphones',
    slug: 'aurora-headphones',
    description: null,
    shortDescription: null,
    basePrice: 199.99,
    active: true,
    featured: false,
    category: { id: 'cat1', name: 'Audio', slug: 'audio', active: true },
    brand: { id: 'br1', name: 'Aurora', slug: 'aurora', active: true },
    variants: [],
    images: [],
    averageRating: null,
    reviewCount: 0,
    ...overrides
  };
}

const request: AdminProductRequest = {
  name: 'Aurora Headphones',
  slug: 'aurora-headphones',
  description: null,
  shortDescription: null,
  basePrice: 199.99,
  active: true,
  featured: false,
  categoryId: 'cat1',
  brandId: 'br1',
  variants: [{ id: null, sku: 'AH-BLK', name: 'Black', priceOverride: null, attributesJson: null, active: true }],
  images: [{ url: 'https://cdn/x.jpg', altText: null, position: 0, mainImage: true }]
};

describe('AdminProductService', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    return {
      service: TestBed.inject(AdminProductService),
      http: TestBed.inject(HttpTestingController)
    };
  }

  it('listProducts GETs /api/admin/products and unwraps the data array', () => {
    const { service, http } = setup();

    let result: AdminProductResponse[] | undefined;
    service.listProducts().subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/products');
    expect(req.request.method).toBe('GET');
    req.flush({ data: [product(), product({ id: 'p2', active: false })] });

    expect(result?.length).toBe(2);
    expect(result?.[1].active).toBe(false);
    http.verify();
  });

  it('listProducts returns [] when data is null', () => {
    const { service, http } = setup();

    let result: AdminProductResponse[] | undefined;
    service.listProducts().subscribe((r) => (result = r));
    http.expectOne('/api/admin/products').flush({ data: null });

    expect(result).toEqual([]);
    http.verify();
  });

  it('getProduct GETs /api/admin/products/{id} and unwraps the data', () => {
    const { service, http } = setup();

    let result: AdminProductResponse | undefined;
    service.getProduct('p9').subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/products/p9');
    expect(req.request.method).toBe('GET');
    req.flush({ data: product({ id: 'p9' }) });

    expect(result?.id).toBe('p9');
    http.verify();
  });

  it('createProduct POSTs /api/admin/products with the request body', () => {
    const { service, http } = setup();

    let result: AdminProductResponse | undefined;
    service.createProduct(request).subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/products');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: product() });

    expect(result?.name).toBe('Aurora Headphones');
    http.verify();
  });

  it('updateProduct PUTs /api/admin/products/{id} with the request body', () => {
    const { service, http } = setup();

    service.updateProduct('p1', request).subscribe();
    const req = http.expectOne('/api/admin/products/p1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ data: product() });

    http.verify();
  });

  it('updateProduct can reactivate by sending active:true', () => {
    const { service, http } = setup();

    service.updateProduct('p1', { ...request, active: true }).subscribe();
    const req = http.expectOne('/api/admin/products/p1');
    expect(req.request.method).toBe('PUT');
    expect((req.request.body as AdminProductRequest).active).toBe(true);
    req.flush({ data: product({ active: true }) });

    http.verify();
  });

  it('deleteProduct DELETEs /api/admin/products/{id}', () => {
    const { service, http } = setup();

    service.deleteProduct('p1').subscribe();
    const req = http.expectOne('/api/admin/products/p1');
    expect(req.request.method).toBe('DELETE');
    req.flush({ data: null });

    http.verify();
  });

  it('listCategories GETs /api/categories and unwraps (defaults to [])', () => {
    const { service, http } = setup();

    let result: AdminCategoryResponse[] | undefined;
    service.listCategories().subscribe((r) => (result = r));
    const req = http.expectOne('/api/categories');
    expect(req.request.method).toBe('GET');
    req.flush({ data: [{ id: 'cat1', name: 'Audio', slug: 'audio', active: true }] });

    expect(result?.[0].name).toBe('Audio');
    http.verify();
  });

  it('listBrands GETs /api/brands and unwraps (defaults to [])', () => {
    const { service, http } = setup();

    let result: AdminBrandResponse[] | undefined;
    service.listBrands().subscribe((r) => (result = r));
    const req = http.expectOne('/api/brands');
    expect(req.request.method).toBe('GET');
    req.flush({ data: null });

    expect(result).toEqual([]);
    http.verify();
  });
});
