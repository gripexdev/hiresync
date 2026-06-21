import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['register', 'googleAuth']);

    TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  function fillValidForm(): void {
    component.form.setValue({
      fullName: 'Othmane Sadiky',
      email: 'othmane@example.com',
      password: 'password123',
      confirmPassword: 'password123',
    });
  }

  it('the form starts empty and invalid', () => {
    expect(component.form.valid).toBeFalse();
  });

  it('mismatched passwords mark the form invalid via the cross-field validator', () => {
    component.form.setValue({
      fullName: 'Othmane Sadiky',
      email: 'othmane@example.com',
      password: 'password123',
      confirmPassword: 'different',
    });

    expect(component.form.errors).toEqual({ mismatch: true });
  });

  it('a password shorter than 8 characters is invalid', () => {
    component.form.patchValue({ password: 'short' });
    expect(component.form.get('password')?.invalid).toBeTrue();
  });

  it('submit() does nothing and marks all fields touched when the form is invalid', () => {
    component.submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(component.form.get('fullName')?.touched).toBeTrue();
  });

  it('submit() success navigates to the dashboard', () => {
    fillValidForm();
    auth.register.and.returnValue(of({ token: 'jwt', user: {} as any }));

    component.submit();

    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Othmane Sadiky', email: 'othmane@example.com', password: 'password123',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('submit() failure (e.g. duplicate email) shows the server error', () => {
    fillValidForm();
    auth.register.and.returnValue(throwError(() => new Error('Email already in use')));

    component.submit();

    expect(component.error()).toBe('Email already in use');
    expect(component.loading()).toBeFalse();
  });

  it('onGoogleCredential() success navigates to the dashboard', () => {
    auth.googleAuth.and.returnValue(of({ token: 'jwt', user: {} as any }));

    component.onGoogleCredential('google-id-token');

    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('onGoogleCredential() failure shows a Google-specific error', () => {
    auth.googleAuth.and.returnValue(throwError(() => ({})));

    component.onGoogleCredential('bad-token');

    expect(component.error()).toBe('Erreur de connexion avec Google');
  });
});
