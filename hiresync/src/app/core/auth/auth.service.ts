import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/user.model';

const TOKEN_KEY = 'hs_token';
const USER_KEY  = 'hs_user';

/** Shape the Spring Boot backend actually returns */
interface BackendAuthResponse {
  token:    string;
  userId:   string;
  fullName: string;
  email:    string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _user    = signal<User | null>(this._loadUser());
  private readonly _token   = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly user       = this._user.asReadonly();
  readonly token      = this._token.asReadonly();
  readonly isLoggedIn = computed(() => !!this._token());

  constructor(private http: HttpClient, private router: Router) {
    // Clear any stale expired token immediately so the app never appears
    // "logged in" with a token the backend will reject.
    if (this._token() && this.isTokenExpired()) {
      this._clearSession();
    }
  }

  /** Returns true if the stored JWT has passed its exp claim. */
  isTokenExpired(): boolean {
    const token = this._token();
    if (!token) return true;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }

  private _clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this._token.set(null);
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http.post<BackendAuthResponse>(`${environment.apiUrl}/auth/login`, req).pipe(
      map(r => this._toAuthResponse(r)),
      tap(r => this._persist(r)),
    );
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<BackendAuthResponse>(`${environment.apiUrl}/auth/register`, req).pipe(
      map(r => this._toAuthResponse(r)),
      tap(r => this._persist(r)),
    );
  }

  logout(): void {
    this._clearSession();
    this.router.navigate(['/login']);
  }

  private _toAuthResponse(r: BackendAuthResponse): AuthResponse {
    const user: User = {
      id:       r.userId,
      fullName: r.fullName,
      email:    r.email,
      avatarUrl: `https://ui-avatars.com/api/?name=${encodeURIComponent(r.fullName)}&background=2E86AB&color=fff&size=128`,
      createdAt: new Date().toISOString(),
    };
    return { token: r.token, user };
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
