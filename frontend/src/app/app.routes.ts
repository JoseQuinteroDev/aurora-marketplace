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
import { NotFoundPageComponent } from './features/not-found/not-found-page.component';

export const routes: Routes = [
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', component: AdminDashboardPageComponent, title: 'title.admin' }
    ]
  },
  {
    path: '',
    component: StorefrontLayoutComponent,
    children: [
      { path: '', component: HomePageComponent, title: 'title.home' },
      { path: 'catalog', component: CatalogPageComponent, title: 'title.catalog' },
      { path: 'products/:slug', component: ProductDetailPageComponent, title: 'title.product' },
      { path: 'login', component: LoginPageComponent, title: 'title.login' },
      { path: 'register', component: RegisterPageComponent, title: 'title.register' },
      { path: 'cart', component: CartPageComponent, canActivate: [authGuard], title: 'title.cart' },
      { path: 'wishlist', component: WishlistPageComponent, canActivate: [authGuard], title: 'title.wishlist' },
      { path: 'checkout', component: CheckoutPageComponent, canActivate: [authGuard], title: 'title.checkout' },
      { path: 'account/orders', component: OrdersPageComponent, canActivate: [authGuard], title: 'title.orders' },
      { path: 'account/orders/:id', component: OrderDetailPageComponent, canActivate: [authGuard], title: 'title.order' },
      { path: 'orders/:id/payment', component: PaymentPageComponent, canActivate: [authGuard], title: 'title.payment' },
      { path: '**', component: NotFoundPageComponent, title: 'title.notFound' }
    ]
  }
];
