import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { Job, JobSearchParams, JobSearchResult } from '../models/job.model';

interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class JobService {
  private readonly apiUrl = `${environment.apiUrl}/jobs`;

  constructor(private http: HttpClient) {}

  search(params: JobSearchParams): Observable<JobSearchResult> {
    let p = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20));
    if (params.q) p = p.set('q', params.q);

    return this.http.get<SpringPage<Job>>(this.apiUrl, { params: p }).pipe(
      map(page => ({
        jobs: page.content.map(j => ({ ...j, postedAt: j.postedAt ?? j.scrapedAt })),
        total: page.totalElements,
        totalPages: page.totalPages,
        page: page.number,
        size: page.size,
      }))
    );
  }

  getById(id: string): Observable<Job> {
    return this.http.get<Job>(`${this.apiUrl}/${id}`).pipe(
      map(j => ({ ...j, postedAt: j.postedAt ?? j.scrapedAt }))
    );
  }

  getSimilar(jobId: string): Observable<Job[]> {
    return this.http.get<SpringPage<Job>>(this.apiUrl, {
      params: new HttpParams().set('page', '0').set('size', '4'),
    }).pipe(
      map(page => page.content.filter(j => j.id !== jobId).slice(0, 3))
    );
  }
}
