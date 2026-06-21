import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], { token: () => 'stub-jwt' });
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token to outgoing requests when one is present', () => {
    http.get('/api/jobs').subscribe();

    const req = httpMock.expectOne('/api/jobs');
    expect(req.request.headers.get('Authorization')).toBe('Bearer stub-jwt');
    req.flush({});
  });

  it('sends the request unmodified when there is no token', () => {
    Object.defineProperty(authServiceSpy, 'token', { value: () => null });

    http.get('/api/jobs').subscribe();

    const req = httpMock.expectOne('/api/jobs');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('logs out and redirects to /login on a 401 response', () => {
    http.get('/api/cv/versions').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/cv/versions');
    req.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logs out and redirects to /login on a 403 response', () => {
    http.get('/api/cv/versions').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/cv/versions');
    req.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('does not log out on an unrelated error such as a 500', () => {
    http.get('/api/cv/versions').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/cv/versions');
    req.flush({ message: 'oops' }, { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });
});
