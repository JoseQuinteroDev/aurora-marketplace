import { instantToLocalInput, localInputToInstant } from './datetime';

describe('datetime helpers', () => {
  it('maps an empty/absent value to empty/null both ways', () => {
    expect(instantToLocalInput(null)).toBe('');
    expect(instantToLocalInput('')).toBe('');
    expect(instantToLocalInput(undefined)).toBe('');
    expect(localInputToInstant('')).toBeNull();
    expect(localInputToInstant(null)).toBeNull();
    expect(localInputToInstant(undefined)).toBeNull();
  });

  it('produces a 16-char datetime-local string from an instant', () => {
    const local = instantToLocalInput('2026-06-28T12:30:00Z');
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it('localInputToInstant returns a valid ISO-8601 UTC instant', () => {
    const iso = localInputToInstant('2026-06-28T14:30');
    expect(iso).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  });

  it('round-trips an instant back to the same instant (timezone-agnostic)', () => {
    const original = '2026-06-28T12:30:00.000Z';
    const local = instantToLocalInput(original);
    const back = localInputToInstant(local);
    expect(back).toBe(original);
  });

  it('ignores an unparseable value', () => {
    expect(instantToLocalInput('not-a-date')).toBe('');
    expect(localInputToInstant('not-a-date')).toBeNull();
  });
});
