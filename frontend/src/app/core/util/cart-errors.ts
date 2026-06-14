import { HttpErrorResponse } from '@angular/common/http';

/**
 * Maps a failed cart request to a translation key for a user-facing message.
 * Reads the backend ErrorResponse `code` (see common.exception.ErrorResponse)
 * and falls back to a generic message for anything unrecognized.
 */
export function cartErrorKey(error: unknown): string {
  const code = error instanceof HttpErrorResponse ? (error.error?.code as string | undefined) : undefined;

  switch (code) {
    case 'INSUFFICIENT_STOCK':
      return 'cart.toast.insufficientStock';
    case 'INVENTORY_NOT_AVAILABLE':
      return 'cart.toast.unavailable';
    case 'PRODUCT_VARIANT_INACTIVE':
      return 'cart.toast.inactive';
    default:
      return 'cart.toast.error';
  }
}
