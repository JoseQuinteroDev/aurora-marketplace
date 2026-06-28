import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import {
  AdminBrandResponse,
  AdminCategoryResponse,
  AdminProductRequest,
  AdminProductResponse,
  AdminProductVariantRequest
} from '../../../core/models/admin-product.model';
import { AdminProductService } from '../../../services/admin-product.service';
import { ToastService } from '../../../services/toast.service';
import { AdminProductFormPageComponent } from './admin-product-form-page.component';

/**
 * Product create/edit form — exercised at the component-logic level (no full render).
 * A fake AdminProductService feeds the lookups + the product on edit and captures the
 * create/update request bodies. ToastService and Router are no-ops. The constructor
 * calls load(), resolved synchronously by the fakes (of(...)).
 */
describe('AdminProductFormPageComponent', () => {
  let createCalls: AdminProductRequest[];
  let updateCalls: { id: string; body: AdminProductRequest }[];
  let productToLoad: AdminProductResponse | null;
  let paramId: string | null;

  const categories: AdminCategoryResponse[] = [
    { id: 'cat1', name: 'Audio', slug: 'audio', active: true },
    { id: 'cat2', name: 'Home', slug: 'home', active: true }
  ];
  const brands: AdminBrandResponse[] = [
    { id: 'br1', name: 'Aurora', slug: 'aurora', active: true },
    { id: 'br2', name: 'Lumio', slug: 'lumio', active: true }
  ];

  function product(overrides: Partial<AdminProductResponse> = {}): AdminProductResponse {
    return {
      id: 'p1',
      name: 'Aurora Headphones',
      slug: 'aurora-headphones',
      description: 'Full description',
      shortDescription: 'Short',
      basePrice: 199.99,
      active: true,
      featured: true,
      category: categories[0],
      brand: brands[0],
      variants: [
        { id: 'v1', sku: 'AH-BLK', name: 'Black', priceOverride: null, effectivePrice: 199.99, attributesJson: '{"c":"black"}', active: true },
        { id: 'v2', sku: 'AH-WHT', name: 'White', priceOverride: 209.99, effectivePrice: 209.99, attributesJson: null, active: true }
      ],
      images: [
        { id: 'i1', url: 'https://cdn/a.jpg', altText: 'A', position: 0, mainImage: true },
        { id: 'i2', url: 'https://cdn/b.jpg', altText: null, position: 1, mainImage: false }
      ],
      averageRating: null,
      reviewCount: 0,
      ...overrides
    };
  }

  function build(): AdminProductFormPageComponent {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AdminProductService,
          useValue: {
            listCategories: (): Observable<AdminCategoryResponse[]> => of(categories),
            listBrands: (): Observable<AdminBrandResponse[]> => of(brands),
            getProduct: (): Observable<AdminProductResponse> => of(productToLoad ?? product()),
            createProduct: (body: AdminProductRequest) => {
              createCalls.push(body);
              return of(product());
            },
            updateProduct: (id: string, body: AdminProductRequest) => {
              updateCalls.push({ id, body });
              return of(product());
            }
          }
        },
        { provide: ToastService, useValue: { success: () => 0, error: () => 0 } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => paramId } } }
        }
      ]
    });
    return TestBed.runInInjectionContext(() => new AdminProductFormPageComponent());
  }

  beforeEach(() => {
    createCalls = [];
    updateCalls = [];
    productToLoad = null;
    paramId = null;
  });

  it('create mode: loads lookups and starts with an empty, invalid form', () => {
    const c = build();
    expect(c.loading()).toBe(false);
    expect(c.editingId()).toBeNull();
    expect(c.categories().length).toBe(2);
    expect(c.brands().length).toBe(2);
    expect(c.variants.length).toBe(0);
    expect(c.images.length).toBe(0);
    expect(c.form.invalid).toBe(true); // name/basePrice/category/brand required
  });

  it('does not call the API when the form is invalid', () => {
    const c = build();
    c.save();
    expect(createCalls).toEqual([]);
    expect(updateCalls).toEqual([]);
  });

  it('requires name, basePrice, categoryId and brandId', () => {
    const c = build();
    expect(c.form.controls.name.valid).toBe(false);
    expect(c.form.controls.basePrice.valid).toBe(false);
    expect(c.form.controls.categoryId.valid).toBe(false);
    expect(c.form.controls.brandId.valid).toBe(false);

    c.form.controls.name.setValue('Speaker');
    c.form.controls.basePrice.setValue(49.99);
    c.form.controls.categoryId.setValue('cat1');
    c.form.controls.brandId.setValue('br1');
    expect(c.form.controls.name.valid).toBe(true);
    expect(c.form.controls.basePrice.valid).toBe(true);
    expect(c.form.controls.categoryId.valid).toBe(true);
    expect(c.form.controls.brandId.valid).toBe(true);
  });

  it('rejects a basePrice with more than 2 decimals or 10 integer digits', () => {
    const c = build();
    c.form.controls.basePrice.setValue(1.234);
    expect(c.form.controls.basePrice.valid).toBe(false);
    c.form.controls.basePrice.setValue(12345678901);
    expect(c.form.controls.basePrice.valid).toBe(false);
    c.form.controls.basePrice.setValue(199.99);
    expect(c.form.controls.basePrice.valid).toBe(true);
  });

  it('a valid create sends a normalized request with a new variant (id null)', () => {
    const c = build();
    c.form.controls.name.setValue('  Speaker  ');
    c.form.controls.basePrice.setValue(49.99);
    c.form.controls.categoryId.setValue('cat1');
    c.form.controls.brandId.setValue('br1');
    c.addVariant();
    c.variants.at(0).get('sku')!.setValue('SPK-1');
    c.variants.at(0).get('name')!.setValue('Default');
    c.addImage();
    c.images.at(0).get('url')!.setValue('https://cdn/spk.jpg');

    c.save();

    expect(createCalls.length).toBe(1);
    const body = createCalls[0];
    expect(body.name).toBe('Speaker'); // trimmed
    expect(body.slug).toBeUndefined(); // blank slug omitted so the server derives it
    expect(body.basePrice).toBe(49.99);
    expect(body.variants?.length).toBe(1);
    expect(body.variants?.[0].id).toBeNull(); // new variant
    expect(body.variants?.[0].sku).toBe('SPK-1');
    expect(body.images?.length).toBe(1);
    expect(body.images?.[0].url).toBe('https://cdn/spk.jpg');
  });

  it('blank short/description fields are sent as null, not empty strings', () => {
    const c = build();
    c.form.controls.name.setValue('Speaker');
    c.form.controls.basePrice.setValue(49.99);
    c.form.controls.categoryId.setValue('cat1');
    c.form.controls.brandId.setValue('br1');
    c.save();

    const body = createCalls[0];
    expect(body.shortDescription).toBeNull();
    expect(body.description).toBeNull();
  });

  it('edit mode: prefills scalars, every variant id and all images', () => {
    paramId = 'p1';
    productToLoad = product();
    const c = build();

    expect(c.editingId()).toBe('p1');
    expect(c.form.controls.name.value).toBe('Aurora Headphones');
    expect(c.form.controls.categoryId.value).toBe('cat1');
    expect(c.form.controls.brandId.value).toBe('br1');
    expect(c.variants.length).toBe(2);
    expect(c.variants.at(0).get('id')!.value).toBe('v1');
    expect(c.variants.at(1).get('id')!.value).toBe('v2');
    expect(c.images.length).toBe(2);
    expect(c.images.at(0).get('mainImage')!.value).toBe(true);
  });

  it('edit save resends existing variants WITH ids plus a new variant with id null', () => {
    paramId = 'p1';
    productToLoad = product();
    const c = build();

    // Add a brand-new variant alongside the two prefilled ones.
    c.addVariant();
    c.variants.at(2).get('sku')!.setValue('AH-RED');
    c.variants.at(2).get('name')!.setValue('Red');

    c.save();

    expect(updateCalls.length).toBe(1);
    expect(updateCalls[0].id).toBe('p1');
    const variants = updateCalls[0].body.variants as AdminProductVariantRequest[];
    expect(variants.length).toBe(3);
    expect(variants[0].id).toBe('v1'); // existing kept
    expect(variants[1].id).toBe('v2'); // existing kept
    expect(variants[2].id).toBeNull(); // new
    expect(variants[2].sku).toBe('AH-RED');
    // The full image set is resent (replace-all semantics).
    expect(updateCalls[0].body.images?.length).toBe(2);
  });

  it('removing a variant before save drops it from the request (deactivates it server-side)', () => {
    paramId = 'p1';
    productToLoad = product();
    const c = build();

    c.removeVariant(1); // drop v2
    c.save();

    const variants = updateCalls[0].body.variants as AdminProductVariantRequest[];
    expect(variants.length).toBe(1);
    expect(variants[0].id).toBe('v1');
    expect(variants.some((v) => v.id === 'v2')).toBe(false);
  });

  it('validates image url against the absolute http(s) pattern', () => {
    const c = build();
    c.addImage();
    const url = c.images.at(0).get('url')!;

    url.setValue('not-a-url');
    expect(url.valid).toBe(false);
    url.setValue('ftp://cdn/x.jpg');
    expect(url.valid).toBe(false);
    url.setValue('https://cdn/x.jpg');
    expect(url.valid).toBe(true);
    url.setValue('http://cdn/x.jpg');
    expect(url.valid).toBe(true);
  });

  it('variant sku and name are required', () => {
    const c = build();
    c.addVariant();
    expect(c.variants.at(0).get('sku')!.valid).toBe(false);
    expect(c.variants.at(0).get('name')!.valid).toBe(false);

    c.variants.at(0).get('sku')!.setValue('SKU-1');
    c.variants.at(0).get('name')!.setValue('Default');
    expect(c.variants.at(0).get('sku')!.valid).toBe(true);
    expect(c.variants.at(0).get('name')!.valid).toBe(true);
  });

  it('setMainImage keeps at most one main image checked', () => {
    const c = build();
    c.addImage();
    c.addImage();
    // First add auto-marks index 0 as main; second add does not flip it.
    expect(c.images.at(0).get('mainImage')!.value).toBe(true);
    expect(c.images.at(1).get('mainImage')!.value).toBe(false);

    // Check the second one -> the first must clear.
    c.setMainImage(1, { target: { checked: true } } as unknown as Event);
    expect(c.images.at(0).get('mainImage')!.value).toBe(false);
    expect(c.images.at(1).get('mainImage')!.value).toBe(true);
  });

  it('a new variant created via addVariant carries id null in the built request', () => {
    const c = build();
    c.form.controls.name.setValue('Speaker');
    c.form.controls.basePrice.setValue(49.99);
    c.form.controls.categoryId.setValue('cat1');
    c.form.controls.brandId.setValue('br1');
    c.addVariant();
    c.variants.at(0).get('sku')!.setValue('SPK-1');
    c.variants.at(0).get('name')!.setValue('Default');

    const request = c.buildRequest();
    expect(request.variants?.[0].id).toBeNull();
    expect(request.variants?.[0].attributesJson).toBeNull(); // blank attributes -> null
  });
});
