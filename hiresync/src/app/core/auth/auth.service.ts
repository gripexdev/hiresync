import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, throwError, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/user.model';

const TOKEN_KEY = 'hs_token';
const USER_KEY  = 'hs_user';

// ── Mock data (remove once backend is ready) ──────────────────────────────────
const MOCK_USER: User = {
  id: 'u1',
  fullName: 'Othmane Sadiky',
  email: 'othmane@hiresync.ma',
  avatarUrl: 'https://ui-avatars.com/api/?name=Othmane+Sadiky&background=2E86AB&color=fff&size=128',
  createdAt: '2025-09-01T10:00:00Z',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _user = signal<User | null>(this._loadUser());
  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly user     = this._user.asReadonly();
  readonly token    = this._token.asReadonly();
  readonly isLoggedIn = computed(() => !!this._token());

  constructor(private http: HttpClient, private router: Router) {}

  login(req: LoginRequest): Observable<AuthResponse> {
    // TODO: replace with real API call: return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, req)
    const mock: AuthResponse = { token: 'mock-jwt-token', user: MOCK_USER };
    this._persist(mock);
    return of(mock);
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    // TODO: replace with real API call: return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/register`, req)
    const newUser: User = { ...MOCK_USER, id: 'u2', fullName: req.fullName, email: req.email };
    const mock: AuthResponse = { token: 'mock-jwt-token', user: newUser };
    this._persist(mock);
    return of(mock);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this._token.set(null);
    this.router.navigate(['/login']);
  }

  private _persist(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._token.set(res.token);
    this._user.set(res.user);
  }

  private _loadUser(): User | null {
    try { return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null'); }
    catch { return null; }
  }
}
