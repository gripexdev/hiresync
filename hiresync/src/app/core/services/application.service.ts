import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Application, ApplicationStats, ApplyRequest, ApplicationStatus } from '../models/application.model';
import { Page } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly base = `${environment.apiUrl}/applications`;

  constructor(private http: HttpClient) {}

  /**
   * GET /api/applications — server-side paginated applications, newest first.
   * Pass a `status` to fetch a single kanban column; omit it for the full table list.
   */
  getPage(opts: { status?: ApplicationStatus | null; page: number; size: number }): Observable<Page<Application>> {
    let params = new HttpParams()
      .set('page', opts.page)
      .set('size', opts.size);
    if (opts.status) params = params.set('status', opts.status);
    return this.http.get<Page<Application>>(this.base, { params });
  }

  /** GET /api/applications/stats */
  getStats(): Observable<ApplicationStats> {
    return this.http.get<ApplicationStats>(`${this.base}/stats`);
  }

  /** POST /api/applications/{jobId} — apply with a chosen CV */
  apply(jobId: string, body: ApplyRequest): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${jobId}`, body);
  }

  /** GET /api/applications/check/{jobId} — has the user already applied? */
  checkApplied(jobId: string): Observable<{ applied: boolean }> {
    return this.http.get<{ applied: boolean }>(`${this.base}/check/${jobId}`);
  }

  /** POST /api/applications/{jobId}/mark-applied — record the "Postuler" click (idempotent) */
  markApplied(jobId: string, cvId: string): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${jobId}/mark-applied`, { cvId });
  }

  /** PATCH /api/applications/{id}/status — change an application's status */
  updateStatus(id: string, status: ApplicationStatus): Observable<Application> {
    return this.http.patch<Application>(`${this.base}/${id}/status`, { status });
  }
}
