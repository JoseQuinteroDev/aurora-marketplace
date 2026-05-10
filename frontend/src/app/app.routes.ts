import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { StorefrontLayoutComponent } from './layout/storefront-layout/storefront-layout.component';
import { AdminShellComponent } from './layout/admin-shell/admin-shell.component';
import { HomePageComponent } from './features/home/home-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { RegisterPageComponent } from './features/auth/register-page.component';
import { CatalogPageComponent } from './features/catalog/catalog-page.component';
import { ProductDetailPageComponent } from './features/catalog/product-detail-page.component';
import { AdminDashboardPageComponent } from './features/admin/admin-dashboard-page.component';

export const routes: Routes = [
  {
    path: '',
    component: StorefrontLayoutComponent,
    children: [
      { path: '', component: HomePageComponent, title: 'Aurora Marketplace' },
      { path: 'catalog', component: CatalogPageComponent, title: 'Catalog | Aurora Marketplace' },
      { path: 'products/:slug', component: ProductDetailPageComponent, title: 'Product | Aurora Marketplace' },
      { path: 'login', component: LoginPageComponent, title: 'Login | Aurora Marketplace' },
      { path: 'register', component: RegisterPageComponent, title: 'Register | Aurora Marketplace' }
    ]
  },
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', component: AdminDashboardPageComponent, title: 'Admin | Aurora Marketplace' }
    ]
  },
  { path: '**', redirectTo: '' }
];
