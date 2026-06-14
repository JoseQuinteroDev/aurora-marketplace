/**
 * Resolves a safe in-app redirect target from an untrusted `returnUrl`.
 *
 * Guards against open-redirect attacks: only same-origin, single-leading-slash
 * paths are honored. Rejects protocol-relative URLs (`//evil.com`), absolute URLs
 * (`http://…`), backslash tricks (`/\evil.com`) and anything that doesn't start
 * with a single `/`, falling back to `fallback`.
 */
export function safeInternalUrl(returnUrl: string | null | undefined, fallback = '/'): string {
  if (!returnUrl) {
    return fallback;
  }

  const isInternalPath =
    returnUrl.startsWith('/') && !returnUrl.startsWith('//') && !returnUrl.startsWith('/\\');

  return isInternalPath ? returnUrl : fallback;
}
