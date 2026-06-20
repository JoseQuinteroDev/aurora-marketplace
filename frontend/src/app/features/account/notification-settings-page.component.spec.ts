import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Mock, vi } from 'vitest';
import { AuthUser } from '../../core/models/auth.model';
import { AuthService } from '../../services/auth.service';
import { NotificationSettingsPageComponent } from './notification-settings-page.component';

describe('NotificationSettingsPageComponent', () => {
  let fixture: ComponentFixture<NotificationSettingsPageComponent>;
  let component: NotificationSettingsPageComponent;
  let updateSpy: Mock;

  const user: AuthUser = {
    id: 'u1',
    email: 'ada@aurora.test',
    firstName: 'Ada',
    lastName: 'Lovelace',
    phone: null,
    notificationChannel: 'EMAIL',
    role: 'CUSTOMER'
  };

  beforeEach(() => {
    updateSpy = vi.fn().mockReturnValue(of({ ...user }));
    const authStub = {
      currentUser: signal<AuthUser | null>(user).asReadonly(),
      updateNotificationPreference: updateSpy
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub }
      ]
    });

    fixture = TestBed.createComponent(NotificationSettingsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the heading and the current email', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ada@aurora.test');
    expect(component.form.controls.channel.value).toBe('EMAIL');
  });

  it('reveals the phone field and requires it when SMS is chosen', () => {
    component.form.controls.channel.setValue('SMS');
    fixture.detectChanges();

    const phoneInput = (fixture.nativeElement as HTMLElement).querySelector('input[type="tel"]');
    expect(phoneInput).not.toBeNull();
    expect(component.form.hasError('phoneRequiredForSms')).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('saves the preference when SMS is chosen with a valid phone', () => {
    component.form.setValue({ channel: 'SMS', phone: '+34123456789' });
    fixture.detectChanges();

    component.submit();

    expect(updateSpy).toHaveBeenCalledWith({ channel: 'SMS', phone: '+34123456789' });
    expect(component.saved()).toBe(true);
  });
});
