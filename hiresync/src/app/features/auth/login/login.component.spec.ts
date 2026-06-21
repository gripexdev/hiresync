import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['login', 'googleAuth']);

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  it('the form starts pre-filled with valid demo credentials', () => {
    expect(component.form.valid).toBeTrue();
  });

  it('submit() does nothing when the form is invalid', () => {
    component.form.patchValue({ email: 'not-an-email' });

    component.submit();

    expect(auth.login).not.toHaveBeenCalled();
  });

  it('submit() success navigates to the dashboard', () => {
    auth.login.and.returnValue(of({ token: 'jwt', user: {} as any }));

    component.submit();

    expect(auth.login).toHaveBeenCalledWith({ email: 'othmane@hiresync.ma', password: 'password123' });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.loading()).toBeFalse();
  });

  it('submit() failure shows the error message and stops loading', () => {
    auth.login.and.returnValue(throwError(() => new Error('Identifiants invalides')));

    component.submit();

    expect(component.error()).toBe('Identifiants invalides');
    expect(component.loading()).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('submit() failure without a message falls back to a generic French error', () => {
    auth.login.and.returnValue(throwError(() => ({})));

    component.submit();

    expect(component.error()).toBe('Erreur de connexion');
  });

  it('onGoogleCredential() success navigates to the dashboard', () => {
    auth.googleAuth.and.returnValue(of({ token: 'jwt', user: {} as any }));

    component.onGoogleCredential('google-id-token');

    expect(auth.googleAuth).toHaveBeenCalledWith('google-id-token');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('onGoogleCredential() failure shows a Google-specific error message', () => {
    auth.googleAuth.and.returnValue(throwError(() => new Error('Jeton invalide')));

    component.onGoogleCredential('bad-token');

    expect(component.error()).toBe('Jeton invalide');
    expect(component.loading()).toBeFalse();
  });
});
