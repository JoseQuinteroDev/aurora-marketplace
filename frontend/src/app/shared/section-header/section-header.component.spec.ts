import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SectionHeaderComponent } from './section-header.component';

describe('SectionHeaderComponent', () => {
  let fixture: ComponentFixture<SectionHeaderComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(SectionHeaderComponent);
    fixture.componentRef.setInput('eyebrow', 'Eyebrow text');
    fixture.componentRef.setInput('title', 'The Title');
    fixture.componentRef.setInput('description', 'A description');
    fixture.detectChanges();
  });

  it('renders the eyebrow, title and description from its inputs', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Eyebrow text');
    expect(text).toContain('The Title');
    expect(text).toContain('A description');
  });

  it('renders the title inside an h2 (display heading)', () => {
    const h2 = (fixture.nativeElement as HTMLElement).querySelector('h2');
    expect(h2?.textContent?.trim()).toBe('The Title');
  });
});
