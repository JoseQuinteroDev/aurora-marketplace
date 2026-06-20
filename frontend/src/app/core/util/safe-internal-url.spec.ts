import { safeInternalUrl } from './safe-internal-url';

describe('safeInternalUrl (open-redirect guard)', () => {
  it('returns the fallback for empty input', () => {
    expect(safeInternalUrl(null)).toBe('/');
    expect(safeInternalUrl(undefined)).toBe('/');
    expect(safeInternalUrl('')).toBe('/');
  });

  it('honours single-leading-slash internal paths', () => {
    expect(safeInternalUrl('/catalog')).toBe('/catalog');
    expect(safeInternalUrl('/account/orders?tab=open')).toBe('/account/orders?tab=open');
  });

  it('rejects open-redirect attempts', () => {
    expect(safeInternalUrl('//evil.com')).toBe('/');
    expect(safeInternalUrl('http://evil.com')).toBe('/');
    expect(safeInternalUrl('https://evil.com')).toBe('/');
    expect(safeInternalUrl('/\\evil.com')).toBe('/');
    expect(safeInternalUrl('evil.com')).toBe('/');
  });

  it('uses the provided fallback when rejecting', () => {
    expect(safeInternalUrl('//evil.com', '/login')).toBe('/login');
  });
});
