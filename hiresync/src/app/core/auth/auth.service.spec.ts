import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  function backendResponse(overrides: Partial<{
    token: string; userId: string; fullName: string; email: string; avatarUrl: string | null;
  }> = {}) {
    return {
      token: 'signed-jwt',
      userId: 'user-1',
      fullName: 'Othmane Sadiky',
      email: 'othmane@example.com',
      avatarUrl: null,
      ...overrides,
    };
  }

  beforeEach(() => {
    localStorage.clear();
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
      ],
    });

    // AuthService reads localStorage once, in its constructor — injecting it
    // here means every test starts from a clean, logged-out construction.
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts logged out when localStorage holds no token', () => {
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.user()).toBeNull();
  });

  it('login() persists the token/user and flips isLoggedIn to true', () => {
    service.login({ email: 'othmane@example.com', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(backendResponse());

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.token()).toBe('signed-jwt');
    expect(service.user()?.email).toBe('othmane@example.com');
    expect(localStorage.getItem('hs_token')).toBe('signed-jwt');
  });

  it('login() falls back to a generated avatar when the backend has none', () => {
    service.login({ email: 'othmane@example.com', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush(backendResponse({ avatarUrl: null }));

    expect(service.user()?.avatarUrl).toContain('ui-avatars.com');
  });

  it('login() keeps the real Google avatar when the backend provides one', () => {
    service.login({ email: 'othmane@example.com', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush(backendResponse({ avatarUrl: 'https://lh3.googleusercontent.com/a/photo.jpg' }));

    expect(service.user()?.avatarUrl).toBe('https://lh3.googleusercontent.com/a/photo.jpg');
  });

  it('register() posts to /auth/register and persists the session', () => {
    service.register({ fullName: 'Nouveau Candidat', email: 'new@example.com', password: 'secret123' })
      .subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush(backendResponse({ fullName: 'Nouveau Candidat', email: 'new@example.com' }));

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.user()?.fullName).toBe('Nouveau Candidat');
  });

  it('googleAuth() posts the ID token to /auth/google', () => {
    service.googleAuth('google-id-token').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/google`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ idToken: 'google-id-token' });
    req.flush(backendResponse());

    expect(service.isLoggedIn()).toBeTrue();
  });

  it('logout() clears the session and redirects to /login', () => {
    service.login({ email: 'othmane@example.com', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(backendResponse());
    expect(service.isLoggedIn()).toBeTrue();

    service.logout();

    expect(service.isLoggedIn()).toBeFalse();
    expect(service.user()).toBeNull();
    expect(localStorage.getItem('hs_token')).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('isTokenExpired() returns true for a token whose exp claim is in the past', () => {
    const expiredPayload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 3600 }));
    localStorage.setItem('hs_token', `header.${expiredPayload}.signature`);

    // The already-injected `service` was constructed before this token existed
    // in localStorage, so we need a fresh instance constructed *after* it.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: routerSpy }],
    });
    const fresh = TestBed.inject(AuthService);

    expect(fresh.isTokenExpired()).toBeTrue();
  });

  it('isTokenExpired() returns true for a malformed token rather than throwing', () => {
    localStorage.setItem('hs_token', 'not-a-jwt');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: routerSpy }],
    });
    const fresh = TestBed.inject(AuthService);

    expect(fresh.isTokenExpired()).toBeTrue();
  });

  it('isLoggedIn() is true at construction when a valid, unexpired token is already in localStorage', () => {
    const validPayload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    localStorage.setItem('hs_token', `header.${validPayload}.signature`);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: routerSpy }],
    });
    const fresh = TestBed.inject(AuthService);

    expect(fresh.isLoggedIn()).toBeTrue();
  });
});
