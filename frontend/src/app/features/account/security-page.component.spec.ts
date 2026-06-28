import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ApiResponse } from '../../core/models/api-response.model';
import { MfaEnrollResponse, MfaStatusResponse } from '../../core/models/auth.model';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { SecurityPageComponent } from './security-page.component';

/**
 * Account two-factor management — exercised at the component-logic level (no full render,
 * which is heavy). A fake AuthService returns controllable observables so we can drive the
 * status/enroll/confirm/disable transitions; ToastService is a no-op spy.
 */
describe('SecurityPageComponent', () => {
  let statusResult: () => Observable<ApiResponse<MfaStatusResponse>>;
  let enrollResult: () => Observable<ApiResponse<MfaEnrollResponse>>;
  let confirmResult: () => Observable<ApiResponse<void>>;
  let disableResult: () => Observable<ApiResponse<void>>;
  let confirmCalls: string[];
  let disableCalls: string[];

  function envelope<T>(data: T): ApiResponse<T> {
    return { success: true, message: 'ok', data, timestamp: '' };
  }

  function create(): SecurityPageComponent {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            mfaStatus: () => statusResult(),
            enrollMfa: () => enrollResult(),
            confirmMfa: (code: string) => {
              confirmCalls.push(code);
              return confirmResult();
            },
            disableMfa: (code: string) => {
              disableCalls.push(code);
              return disableResult();
            },
          },
        },
        { provide: ToastService, useValue: { success: () => 0, error: () => 0 } },
      ],
    });
    // The constructor calls loadStatus(); the fakes above resolve synchronously.
    return TestBed.runInInjectionContext(() => new SecurityPageComponent());
  }

  beforeEach(() => {
    statusResult = () => of(envelope({ enabled: false }));
    enrollResult = () => of(envelope({ secret: 'BASE32SECRET', otpauthUri: 'otpauth://totp/Aurora:a@b.c?secret=BASE32SECRET' }));
    confirmResult = () => of(envelope(undefined as unknown as void));
    disableResult = () => of(envelope(undefined as unknown as void));
    confirmCalls = [];
    disableCalls = [];
  });

  it('loads the disabled status on construction', () => {
    const c = create();
    expect(c.statusLoading()).toBe(false);
    expect(c.statusError()).toBe(false);
    expect(c.enabled()).toBe(false);
    expect(c.enrollment()).toBeNull();
  });

  it('reflects an enabled status', () => {
    statusResult = () => of(envelope({ enabled: true }));
    const c = create();
    expect(c.enabled()).toBe(true);
  });

  it('surfaces a status load failure with a retry path', () => {
    statusResult = () => throwError(() => ({ status: 500 }));
    const c = create();
    expect(c.statusError()).toBe(true);
    expect(c.statusLoading()).toBe(false);
  });

  it('enroll exposes the secret and otpauth URI for manual entry', () => {
    const c = create();
    c.enroll();
    expect(c.enrollment()?.secret).toBe('BASE32SECRET');
    expect(c.enrollment()?.otpauthUri).toContain('otpauth://');
    expect(c.busy()).toBe(false);
  });

  it('confirm with a valid code enables MFA and clears the enrollment', () => {
    const c = create();
    c.enroll();
    c.confirmForm.controls.code.setValue('123456');
    c.confirm();

    expect(confirmCalls).toEqual(['123456']);
    expect(c.enabled()).toBe(true);
    expect(c.enrollment()).toBeNull();
    expect(c.error()).toBeNull();
  });

  it('confirm does not call the API for an invalid code', () => {
    const c = create();
    c.enroll();
    c.confirmForm.controls.code.setValue('12'); // too short
    c.confirm();

    expect(confirmCalls).toEqual([]);
    expect(c.enabled()).toBe(false);
  });

  it('a wrong confirm code surfaces an error and keeps the user enrolling', () => {
    confirmResult = () => throwError(() => ({ status: 400, error: { code: 'MFA_INVALID_CODE' } }));
    const c = create();
    c.enroll();
    c.confirmForm.controls.code.setValue('000000');
    c.confirm();

    expect(c.enabled()).toBe(false);
    expect(c.enrollment()).not.toBeNull(); // still on the confirm step
    expect(c.error()).not.toBeNull();
    expect(c.busy()).toBe(false);
  });

  it('disable requires a 6-digit code before calling the API', () => {
    statusResult = () => of(envelope({ enabled: true }));
    const c = create();
    c.disableForm.controls.code.setValue('99'); // invalid
    c.disable();
    expect(disableCalls).toEqual([]);
    expect(c.enabled()).toBe(true);
  });

  it('disable with a valid code turns MFA off', () => {
    statusResult = () => of(envelope({ enabled: true }));
    const c = create();
    c.disableForm.controls.code.setValue('654321');
    c.disable();

    expect(disableCalls).toEqual(['654321']);
    expect(c.enabled()).toBe(false);
    expect(c.error()).toBeNull();
  });

  it('cancelEnroll drops the enrollment without enabling MFA', () => {
    const c = create();
    c.enroll();
    expect(c.enrollment()).not.toBeNull();
    c.cancelEnroll();
    expect(c.enrollment()).toBeNull();
    expect(c.enabled()).toBe(false);
  });
});
