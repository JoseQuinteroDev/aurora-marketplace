import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { LoginRequest } from '../../core/models/auth.model';
import { AuthService } from '../../services/auth.service';
import { LoginPageComponent } from './login-page.component';

/**
 * Login flow (the auth entry point). Covers the security-relevant behaviours:
 * client-side validation gating the call, the open-redirect defense on returnUrl
 * (safeInternalUrl), and the failed-login error surface. AuthService is a fake so
 * no real HTTP happens; the Router is real-but-inert (provideRouter([])) with
 * navigateByUrl spied so we can assert the destination without an actual navigation.
 */
describe('LoginPageComponent', () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let component: LoginPageComponent;

  let loginCalls: LoginRequest[];
  let loginResult: () => Observable<unknown>;
  let mfaVerifyCalls: { mfaToken: string; code: string }[];
  let mfaVerifyResult: () => Observable<unknown>;
  let navigations: string[];
  let returnUrl: string | null;

  function setup(): void {
    loginCalls = [];
    loginResult = () => of({ data: {} });
    mfaVerifyCalls = [];
    mfaVerifyResult = () => of({ data: {} });
    navigations = [];
    returnUrl = null;

    TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            login: (request: LoginRequest) => {
              loginCalls.push(request);
              return loginResult();
            },
            mfaVerify: (mfaToken: string, code: string) => {
              mfaVerifyCalls.push({ mfaToken, code });
              return mfaVerifyResult();
            },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: { get: (key: string) => (key === 'returnUrl' ? returnUrl : null) } },
          },
        },
      ],
    });

    const router = TestBed.inject(Router);
    // Intercept navigateByUrl so we record the target without performing a real navigation.
    router.navigateByUrl = ((url: string) => {
      navigations.push(url);
      return Promise.resolve(true);
    }) as Router['navigateByUrl'];

    fixture = TestBed.createComponent(LoginPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('does not call AuthService.login and shows the email error for an invalid email', () => {
    setup();

    // Valid password, invalid email -> the form is invalid, so submit() must early-return.
    component.form.controls.email.setValue('not-an-email');
    component.form.controls.password.setValue('secret12');
    component.submit();
    fixture.detectChanges();

    expect(loginCalls.length).toBe(0); // login never attempted
    expect(navigations).toEqual([]);
    expect(component.emailInvalid()).toBe(true); // markAllAsTouched surfaced the error

    const errorText = (fixture.nativeElement as HTMLElement).querySelector('#login-email-error');
    expect(errorText).not.toBeNull();
  });

  it('on a successful login navigates to a sanitized internal returnUrl', () => {
    setup();
    returnUrl = '/account/orders';

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();

    expect(loginCalls).toEqual([{ email: 'a@b.c', password: 'secret12' }]);
    expect(navigations).toEqual(['/account/orders']);
    expect(component.error()).toBeNull();
  });

  it('does NOT honor an external/dangerous returnUrl (open-redirect defense)', () => {
    setup();
    returnUrl = 'https://evil.com/phish';

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();

    // safeInternalUrl rejects the absolute URL and falls back to '/'.
    expect(loginCalls.length).toBe(1);
    expect(navigations).toEqual(['/']);
  });

  it('also rejects protocol-relative (//host) returnUrl redirects', () => {
    setup();
    returnUrl = '//evil.com';

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();

    expect(navigations).toEqual(['/']);
  });

  it('surfaces the error state and stops loading when login fails', () => {
    setup();
    loginResult = () => throwError(() => new Error('bad credentials'));

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    fixture.detectChanges();

    expect(navigations).toEqual([]); // no navigation on failure
    expect(component.error()).not.toBeNull();
    expect(component.loading()).toBe(false); // re-enabled for a retry

    const panel = (fixture.nativeElement as HTMLElement).querySelector('app-state-panel');
    expect(panel).not.toBeNull(); // error panel rendered
  });

  it('an MFA_REQUIRED login shows the code step and does NOT navigate or persist a session', () => {
    setup();
    returnUrl = '/account/orders';
    loginResult = () => of({ data: { status: 'MFA_REQUIRED', mfaToken: 'mfa-tok' } });

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    fixture.detectChanges();

    expect(loginCalls.length).toBe(1);
    expect(component.mfaRequired()).toBe(true); // switched to the code step
    expect(navigations).toEqual([]); // no navigation yet — not authenticated
    expect(component.loading()).toBe(false);

    // The code input is now rendered.
    const codeInput = (fixture.nativeElement as HTMLElement).querySelector('#mfa-code-error, input[formControlName="code"]');
    expect(codeInput).not.toBeNull();
  });

  it('entering a code calls mfaVerify and on success navigates to the sanitized returnUrl', () => {
    setup();
    returnUrl = '/account/orders';
    loginResult = () => of({ data: { status: 'MFA_REQUIRED', mfaToken: 'mfa-tok' } });

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    fixture.detectChanges();

    component.mfaForm.controls.code.setValue('123456');
    component.submitMfa();

    expect(mfaVerifyCalls).toEqual([{ mfaToken: 'mfa-tok', code: '123456' }]);
    expect(navigations).toEqual(['/account/orders']);
    expect(component.error()).toBeNull();
  });

  it('a dangerous returnUrl is still rejected after the MFA step (open-redirect defense)', () => {
    setup();
    returnUrl = 'https://evil.com/phish';
    loginResult = () => of({ data: { status: 'MFA_REQUIRED', mfaToken: 'mfa-tok' } });

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    component.mfaForm.controls.code.setValue('123456');
    component.submitMfa();

    expect(navigations).toEqual(['/']);
  });

  it('a failed MFA verification surfaces an error, stays on the step and allows a retry', () => {
    setup();
    loginResult = () => of({ data: { status: 'MFA_REQUIRED', mfaToken: 'mfa-tok' } });
    mfaVerifyResult = () => throwError(() => ({ status: 400 }));

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    fixture.detectChanges();

    component.mfaForm.controls.code.setValue('000000');
    component.submitMfa();
    fixture.detectChanges();

    expect(navigations).toEqual([]); // no navigation
    expect(component.mfaRequired()).toBe(true); // still on the code step
    expect(component.error()).not.toBeNull();
    expect(component.loading()).toBe(false); // re-enabled for a retry
  });

  it('backToLogin returns to the credentials step and clears the challenge', () => {
    setup();
    loginResult = () => of({ data: { status: 'MFA_REQUIRED', mfaToken: 'mfa-tok' } });

    component.form.controls.email.setValue('a@b.c');
    component.form.controls.password.setValue('secret12');
    component.submit();
    expect(component.mfaRequired()).toBe(true);

    component.backToLogin();
    expect(component.mfaRequired()).toBe(false);
    expect(component.error()).toBeNull();
  });
});
