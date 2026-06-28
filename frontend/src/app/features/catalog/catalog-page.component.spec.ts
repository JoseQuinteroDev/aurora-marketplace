import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { Brand, Category, Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { CatalogPageComponent } from './catalog-page.component';

/**
 * Catalog facet + sort logic. visibleProducts() is a pure computed over the loaded
 * product list driven by the selectedCategory / selectedBrand / sortBy signals — the
 * client-side filtering layer (the text query is the only thing that hits the backend).
 *
 * We drive those public signals directly rather than going through the URL/router, and
 * we never call detectChanges(), so ngOnInit's service subscriptions never fire — the
 * CatalogService fake exists only to satisfy injection. This keeps the test focused on
 * the computed's behaviour (the task's "prefer pure logic when a full render is heavy").
 */
describe('CatalogPageComponent.visibleProducts', () => {
  let fixture: ComponentFixture<CatalogPageComponent>;
  let component: CatalogPageComponent;

  function category(slug: string): Category {
    return { id: `cat-${slug}`, name: slug, slug, active: true };
  }

  function brand(slug: string): Brand {
    return { id: `brand-${slug}`, name: slug, slug, active: true };
  }

  function product(
    id: string,
    name: string,
    basePrice: number,
    categorySlug: string,
    brandSlug: string,
    featured = false
  ): Product {
    return {
      id,
      name,
      slug: id,
      description: null,
      shortDescription: null,
      basePrice,
      active: true,
      featured,
      category: category(categorySlug),
      brand: brand(brandSlug),
      variants: [],
      images: [],
    };
  }

  // Stable, deliberately-unsorted fixture so sort assertions are meaningful.
  const sofa = product('sofa', 'Sofa', 300, 'living', 'acme', false);
  const lamp = product('lamp', 'Lamp', 80, 'living', 'lumio', true);
  const desk = product('desk', 'Desk', 150, 'office', 'acme', false);
  const chair = product('chair', 'Chair', 220, 'office', 'lumio', true);
  const all = [sofa, lamp, desk, chair];

  function setup(): void {
    TestBed.configureTestingModule({
      imports: [CatalogPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: CatalogService,
          useValue: {
            getProducts: () => of([]),
            searchProducts: () => of([]),
            getCategories: () => of([]),
            getBrands: () => of([]),
          },
        },
        {
          // ngOnInit is never run (no detectChanges), but ActivatedRoute is a
          // constructor dependency, so it must still be injectable.
          provide: ActivatedRoute,
          useValue: { queryParamMap: EMPTY },
        },
      ],
    });

    fixture = TestBed.createComponent(CatalogPageComponent);
    component = fixture.componentInstance;
    component.products.set(all);
  }

  it('returns every product (featured-first) when no facet is active', () => {
    setup();

    const visible = component.visibleProducts();
    expect(visible.length).toBe(4);
    // Default 'featured' sort puts featured items ahead of non-featured.
    const featuredIds = visible.filter((p) => p.featured).map((p) => p.id);
    const firstTwo = visible.slice(0, 2).map((p) => p.id);
    expect(firstTwo.sort()).toEqual(featuredIds.sort());
  });

  it('applies the active category facet', () => {
    setup();
    component.selectedCategory.set('office');

    const ids = component.visibleProducts().map((p) => p.id);
    expect(ids.sort()).toEqual(['chair', 'desk']);
  });

  it('applies the active brand facet', () => {
    setup();
    component.selectedBrand.set('acme');

    const ids = component.visibleProducts().map((p) => p.id);
    expect(ids.sort()).toEqual(['desk', 'sofa']);
  });

  it('combines category AND brand facets (intersection)', () => {
    setup();
    component.selectedCategory.set('office');
    component.selectedBrand.set('lumio');

    const ids = component.visibleProducts().map((p) => p.id);
    expect(ids).toEqual(['chair']);
  });

  it('sorts by price ascending', () => {
    setup();
    component.sortBy.set('price-asc');

    expect(component.visibleProducts().map((p) => p.basePrice)).toEqual([80, 150, 220, 300]);
  });

  it('sorts by price descending', () => {
    setup();
    component.sortBy.set('price-desc');

    expect(component.visibleProducts().map((p) => p.basePrice)).toEqual([300, 220, 150, 80]);
  });

  it('sorts by name alphabetically', () => {
    setup();
    component.sortBy.set('name');

    expect(component.visibleProducts().map((p) => p.name)).toEqual(['Chair', 'Desk', 'Lamp', 'Sofa']);
  });

  it('applies the facet filter and the sort together', () => {
    setup();
    component.selectedCategory.set('living');
    component.sortBy.set('price-asc');

    // 'living' = sofa (300) + lamp (80), sorted ascending by price.
    expect(component.visibleProducts().map((p) => p.id)).toEqual(['lamp', 'sofa']);
  });

  it('returns an empty list when a facet matches nothing', () => {
    setup();
    component.selectedBrand.set('does-not-exist');

    expect(component.visibleProducts()).toEqual([]);
  });
});
