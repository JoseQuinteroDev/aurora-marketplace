import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { StorefrontLayoutComponent } from './layout/storefront-layout/storefront-layout.component';
import { AdminShellComponent } from './layout/admin-shell/admin-shell.component';
import { HomePageComponent } from './features/home/home-page.component';

// Layout shells and the landing page load eagerly (needed for first paint);
// every other route is lazy-loaded so it ships as its own chunk.
export const routes: Routes = [
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [adminGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/admin/admin-dashboard-page.component').then((m) => m.AdminDashboardPageComponent),
        title: 'title.admin'
      },
      {
        path: 'orders',
        loadComponent: () => import('./features/admin/orders/admin-orders-page.component').then((m) => m.AdminOrdersPageComponent),
        title: 'title.adminOrders'
      },
      {
        path: 'orders/:id',
        loadComponent: () => import('./features/admin/orders/admin-order-detail-page.component').then((m) => m.AdminOrderDetailPageComponent),
        title: 'title.adminOrder'
      },
      {
        path: 'coupons',
        loadComponent: () => import('./features/admin/coupons/admin-coupons-page.component').then((m) => m.AdminCouponsPageComponent),
        title: 'title.adminCoupons'
      }
    ]
  },
  {
    path: '',
    component: StorefrontLayoutComponent,
    children: [
      { path: '', component: HomePageComponent, title: 'title.home' },
      {
        path: 'catalog',
        loadComponent: () => import('./features/catalog/catalog-page.component').then((m) => m.CatalogPageComponent),
        title: 'title.catalog'
      },
      {
        path: 'products/:slug',
        loadComponent: () => import('./features/catalog/product-detail-page.component').then((m) => m.ProductDetailPageComponent),
        title: 'title.product'
      },
      {
        path: 'login',
        loadComponent: () => import('./features/auth/login-page.component').then((m) => m.LoginPageComponent),
        title: 'title.login'
      },
      {
        path: 'register',
        loadComponent: () => import('./features/auth/register-page.component').then((m) => m.RegisterPageComponent),
        title: 'title.register'
      },
      {
        path: 'forgot-password',
        loadComponent: () => import('./features/auth/forgot-password-page.component').then((m) => m.ForgotPasswordPageComponent),
        title: 'title.forgotPassword'
      },
      {
        path: 'reset-password',
        loadComponent: () => import('./features/auth/reset-password-page.component').then((m) => m.ResetPasswordPageComponent),
        title: 'title.resetPassword'
      },
      {
        path: 'verify-email',
        loadComponent: () => import('./features/auth/verify-email-page.component').then((m) => m.VerifyEmailPageComponent),
        title: 'title.verifyEmail'
      },
      {
        path: 'cart',
        loadComponent: () => import('./features/cart/cart-page.component').then((m) => m.CartPageComponent),
        canActivate: [authGuard],
        title: 'title.cart'
      },
      {
        path: 'wishlist',
        loadComponent: () => import('./features/wishlist/wishlist-page.component').then((m) => m.WishlistPageComponent),
        canActivate: [authGuard],
        title: 'title.wishlist'
      },
      {
        path: 'checkout',
        loadComponent: () => import('./features/checkout/checkout-page.component').then((m) => m.CheckoutPageComponent),
        canActivate: [authGuard],
        title: 'title.checkout'
      },
      {
        path: 'account/orders',
        loadComponent: () => import('./features/orders/orders-page.component').then((m) => m.OrdersPageComponent),
        canActivate: [authGuard],
        title: 'title.orders'
      },
      {
        path: 'account/security',
        loadComponent: () => import('./features/account/security-page.component').then((m) => m.SecurityPageComponent),
        canActivate: [authGuard],
        title: 'title.security'
      },
      {
        path: 'account/orders/:id',
        loadComponent: () => import('./features/orders/order-detail-page.component').then((m) => m.OrderDetailPageComponent),
        canActivate: [authGuard],
        title: 'title.order'
      },
      {
        path: 'orders/:id/payment',
        loadComponent: () => import('./features/payment/payment-page.component').then((m) => m.PaymentPageComponent),
        canActivate: [authGuard],
        title: 'title.payment'
      },
      {
        path: '**',
        loadComponent: () => import('./features/not-found/not-found-page.component').then((m) => m.NotFoundPageComponent),
        title: 'title.notFound'
      }
    ]
  }
];
