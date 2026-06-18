import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Application, ApplicationStats, ApplyRequest } from '../models/application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly base = `${environment.apiUrl}/applications`;

  constructor(private http: HttpClient) {}

  /** GET /api/applications — current user's applications */
  getAll(): Observable<Application[]> {
    return this.http.get<Application[]>(this.base);
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
}
