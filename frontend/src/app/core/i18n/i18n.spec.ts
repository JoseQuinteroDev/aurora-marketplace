import { LanguageService } from './language.service';
import { translations } from './translations';

/**
 * i18n integrity: the EN and ES dictionaries must stay in lockstep. A key present
 * in one language but not the other is exactly the "i18n leak" (a raw key or a
 * wrong-language string shown to the user) this guards against.
 */
describe('translations dictionary', () => {
  const esKeys = Object.keys(translations.es).sort();
  const enKeys = Object.keys(translations.en).sort();

  it('defines the same keys in ES and EN', () => {
    const missingInEn = esKeys.filter((k) => !(k in translations.en));
    const missingInEs = enKeys.filter((k) => !(k in translations.es));

    expect(missingInEn, `keys missing in EN: ${missingInEn.join(', ')}`).toEqual([]);
    expect(missingInEs, `keys missing in ES: ${missingInEs.join(', ')}`).toEqual([]);
  });

  it('has no empty values in either language', () => {
    const emptyEs = esKeys.filter((k) => !translations.es[k]?.trim());
    const emptyEn = enKeys.filter((k) => !translations.en[k]?.trim());

    expect(emptyEs, `empty ES values: ${emptyEs.join(', ')}`).toEqual([]);
    expect(emptyEn, `empty EN values: ${emptyEn.join(', ')}`).toEqual([]);
  });
});

describe('LanguageService', () => {
  beforeEach(() => localStorage.clear());

  it('defaults to Spanish when nothing is stored', () => {
    const service = new LanguageService();
    expect(service.language()).toBe('es');
    expect(service.isSpanish()).toBe(true);
  });

  it('toggle() switches language and persists the choice', () => {
    const service = new LanguageService();

    service.toggle();
    expect(service.language()).toBe('en');
    expect(localStorage.getItem('aurora_language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');

    service.toggle();
    expect(service.language()).toBe('es');
  });

  it('translate() returns the active-language value', () => {
    const service = new LanguageService();
    expect(service.translate('nav.home')).toBe(translations.es['nav.home']);

    service.setLanguage('en');
    expect(service.translate('nav.home')).toBe(translations.en['nav.home']);
  });

  it('translate() returns the key itself for an unknown key', () => {
    const service = new LanguageService();
    expect(service.translate('does.not.exist')).toBe('does.not.exist');
  });
});
