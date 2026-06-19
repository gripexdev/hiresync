import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification } from '../models/notification.model';
import { Page } from '../models/page.model';

/**
 * Real, server-backed notifications. They are raised by the backend on actual
 * product events (CV optimization completed/refused/failed, application status
 * changes) and served server-side paginated.
 *
 * {@link unreadCount} is a shared signal that drives the topbar bell badge. It is
 * refreshed on construction (the service is only created inside the authenticated
 * shell) and kept in sync as the user reads notifications.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly base = `${environment.apiUrl}/notifications`;

  readonly unreadCount = signal(0);

  constructor(private http: HttpClient) {
    this.refreshUnread();
  }

  /** First page of notifications (newest first) — enough for the bell list and dashboard preview. */
  getAll(): Observable<Notification[]> {
    return this.getPage(0, 50).pipe(
      tap(() => this.refreshUnread()),
      map(p => p.content),
    );
  }

  /** Server-side paginated notifications for the dedicated notifications page. */
  getPage(page: number, size: number): Observable<Page<Notification>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Notification>>(this.base, { params });
  }

  /** Re-fetch the unread count from the server and update the badge. */
  refreshUnread(): void {
    this.http.get<{ count: number }>(`${this.base}/unread-count`).subscribe({
      next: r => this.unreadCount.set(r.count),
      error: () => {},
    });
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/read`, {}).pipe(
      tap(() => this.unreadCount.update(n => Math.max(0, n - 1))),
    );
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/read-all`, {}).pipe(
      tap(() => this.unreadCount.set(0)),
    );
  }
}
