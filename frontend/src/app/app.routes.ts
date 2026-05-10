import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { StorefrontLayoutComponent } from './layout/storefront-layout/storefront-layout.component';
import { AdminShellComponent } from './layout/admin-shell/admin-shell.component';
import { HomePageComponent } from './features/home/home-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { RegisterPageComponent } from './features/auth/register-page.component';
import { CatalogPageComponent } from './features/catalog/catalog-page.component';
import { ProductDetailPageComponent } from './features/catalog/product-detail-page.component';
import { AdminDashboardPageComponent } from './features/admin/admin-dashboard-page.component';
import { CartPageComponent } from './features/cart/cart-page.component';
import { CheckoutPageComponent } from './features/checkout/checkout-page.component';
import { WishlistPageComponent } from './features/wishlist/wishlist-page.component';
import { PaymentPageComponent } from './features/payment/payment-page.component';
import { OrdersPageComponent } from './features/orders/orders-page.component';
import { OrderDetailPageComponent } from './features/orders/order-detail-page.component';

export const routes: Routes = [
  {
    path: '',
    component: StorefrontLayoutComponent,
    children: [
      { path: '', component: HomePageComponent, title: 'Aurora Marketplace' },
      { path: 'catalog', component: CatalogPageComponent, title: 'Catalog | Aurora Marketplace' },
      { path: 'products/:slug', component: ProductDetailPageComponent, title: 'Product | Aurora Marketplace' },
      { path: 'login', component: LoginPageComponent, title: 'Login | Aurora Marketplace' },
      { path: 'register', component: RegisterPageComponent, title: 'Register | Aurora Marketplace' },
      { path: 'cart', component: CartPageComponent, canActivate: [authGuard], title: 'Cart | Aurora Marketplace' },
      { path: 'wishlist', component: WishlistPageComponent, canActivate: [authGuard], title: 'Wishlist | Aurora Marketplace' },
      { path: 'checkout', component: CheckoutPageComponent, canActivate: [authGuard], title: 'Checkout | Aurora Marketplace' },
      { path: 'account/orders', component: OrdersPageComponent, canActivate: [authGuard], title: 'Orders | Aurora Marketplace' },
      { path: 'account/orders/:id', component: OrderDetailPageComponent, canActivate: [authGuard], title: 'Order | Aurora Marketplace' },
      { path: 'orders/:id/payment', component: PaymentPageComponent, canActivate: [authGuard], title: 'Payment | Aurora Marketplace' }
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
