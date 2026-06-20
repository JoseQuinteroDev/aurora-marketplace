import { HttpErrorResponse } from '@angular/common/http';
import { cartErrorKey } from './cart-errors';

function httpError(code?: string): HttpErrorResponse {
  return new HttpErrorResponse({ error: code ? { code } : null, status: 400 });
}

describe('cartErrorKey', () => {
  it('maps known backend error codes to translation keys', () => {
    expect(cartErrorKey(httpError('INSUFFICIENT_STOCK'))).toBe('cart.toast.insufficientStock');
    expect(cartErrorKey(httpError('INVENTORY_NOT_AVAILABLE'))).toBe('cart.toast.unavailable');
    expect(cartErrorKey(httpError('PRODUCT_VARIANT_INACTIVE'))).toBe('cart.toast.inactive');
  });

  it('falls back to a generic key for unknown codes and non-HTTP errors', () => {
    expect(cartErrorKey(httpError('WAT'))).toBe('cart.toast.error');
    expect(cartErrorKey(httpError())).toBe('cart.toast.error');
    expect(cartErrorKey(new Error('boom'))).toBe('cart.toast.error');
    expect(cartErrorKey(null)).toBe('cart.toast.error');
  });
});
