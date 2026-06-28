/**
 * Helpers to bridge an HTML `<input type="datetime-local">` (a local wall-clock
 * string with no zone, e.g. `2026-06-28T14:30`) and an ISO-8601 UTC instant
 * (e.g. `2026-06-28T12:30:00Z`) as the backend expects.
 */

/** ISO instant (or null) → `datetime-local` value in the browser's local zone (or '' when absent). */
export function instantToLocalInput(instant: string | null | undefined): string {
  if (!instant) {
    return '';
  }
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  // Shift by the local offset so toISOString's local-component slice reads as wall-clock time.
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/** `datetime-local` value (local wall-clock, or '') → ISO UTC instant, or null when empty/invalid. */
export function localInputToInstant(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date.toISOString();
}
