import { Injectable } from '@angular/core';
import { HttpClient, HttpEventType, HttpRequest } from '@angular/common/http';
import { Observable, of, delay, map, filter, scan, switchMap, timer, takeUntil, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CV, CVOptimizationRequest, CVOptimizationResult,
  CVOptimizationTriggerResponse, CVOptimizationHistoryItem,
  CVOptimizationWsEvent,
} from '../models/cv.model';

// ── Mock data (replace with real HTTP once backend is ready) ──────────────────
const MOCK_CVS: CV[] = [
  {
    id: 'cv1',
    fileName: 'CV_Othmane_Sadiky_2026.pdf',
    fileSize: 184320,
    mimeType: 'application/pdf',
    uploadedAt: '2026-05-15T10:00:00Z',
    atsScore: 62,
    isActive: true,
    optimizationCount: 2,
    parsedSections: [
      { title: 'Résumé',       content: 'Ingénieur Full Stack avec 3 ans d\'expérience en Angular et Spring Boot.' },
      { title: 'Compétences',  content: 'Angular, Spring Boot, PostgreSQL, Docker, Git' },
      { title: 'Expérience',   content: 'Développeur Full Stack @ Maroc Telecom (2023–2026)' },
      { title: 'Formation',    content: 'Cycle Ingénieur GSI — École Nationale des Sciences Appliquées (2026)' },
    ],
  },
  {
    id: 'cv2',
    fileName: 'CV_Othmane_Optimise_OCP.pdf',
    fileSize: 196608,
    mimeType: 'application/pdf',
    uploadedAt: '2026-06-01T14:00:00Z',
    atsScore: 88,
    isActive: false,
    optimizationCount: 0,
  },
];

const MOCK_HISTORY: CVOptimizationHistoryItem[] = [
  { id: 'opt1', jobTitle: 'Ingénieur DevOps Senior', company: 'OCP Group',     originalScore: 62, optimizedScore: 88, createdAt: '2026-06-01T14:00:00Z', status: 'completed' },
  { id: 'opt2', jobTitle: 'Dev Full Stack Angular',  company: 'Maroc Telecom', originalScore: 62, optimizedScore: 81, createdAt: '2026-05-28T10:00:00Z', status: 'completed' },
];

const MOCK_RESULT: CVOptimizationResult = {
  id: 'opt1', status: 'completed',
  cvId: 'cv1', jobId: 'j1',
  jobTitle: 'Ingénieur DevOps Senior',
  originalScore: 62, optimizedScore: 88,
  modelUsed: 'mistralai/mistral-7b-instruct:free',
  processingTimeMs: 8400,
  suggestedChanges: [
    { type: 'keyword_added',    description: 'Ajout de "Kubernetes" et "Terraform" dans la section Compétences', before: 'Docker, Git', after: 'Docker, Kubernetes, Terraform, Git' },
    { type: 'section_rewritten', description: 'Reformulation du résumé pour cibler le poste DevOps', before: 'Ingénieur Full Stack avec 3 ans d\'expérience.', after: 'Ingénieur DevOps / Full Stack avec 3 ans d\'expérience en automatisation CI/CD, orchestration Kubernetes et infrastructure as code.' },
    { type: 'keyword_added',    description: 'Ajout "CI/CD", "Jenkins", "AWS" identifiés dans l\'offre OCP', after: 'CI/CD Jenkins, AWS EKS' },
    { type: 'format_improved',  description: 'Réorganisation des sections selon le format ATS recommandé' },
    { type: 'skill_added',      description: 'Ajout de la certification AWS mentionnée comme préférence dans l\'offre' },
  ],
  optimizedCvUrl: '/assets/mock-cv-optimized.pdf',
  createdAt: '2026-06-01T14:00:00Z',
  completedAt: '2026-06-01T14:00:08Z',
};

// ── Upload progress event emitted to the component ────────────────────────────
export interface UploadProgressEvent {
  type: 'progress' | 'done' | 'error';
  progress?: number;   // 0–100
  cv?: CV;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class CvService {
  private readonly base = `${environment.apiUrl}/cv`;

  constructor(private http: HttpClient) {}

  // ── GET /api/cv/versions ──────────────────────────────────────────────────
  getAll(): Observable<CV[]> {
    return this.http.get<CV[]>(`${this.base}/versions`);
  }

  // ── POST /api/cv/upload  (multipart, with progress events) ────────────────
  /**
   * Emits UploadProgressEvent stream:
   *   { type:'progress', progress: 0–100 }  — while uploading
   *   { type:'done', cv }                    — when server responds with parsed CV
   *   { type:'error', error }                — on failure
   *
   * Real implementation uses HttpRequest with reportProgress:true so the
   * component can drive a progress bar.
   */
  upload(file: File): Observable<UploadProgressEvent> {
    const formData = new FormData();
    formData.append('file', file);
    const req = new HttpRequest('POST', `${this.base}/upload`, formData, { reportProgress: true });
    return new Observable<UploadProgressEvent>(observer => {
      this.http.request(req).subscribe({
        next: event => {
          if (event.type === HttpEventType.UploadProgress) {
            const progress = Math.round(100 * (event.loaded / (event.total ?? event.loaded)));
            observer.next({ type: 'progress', progress });
          } else if (event.type === HttpEventType.Response) {
            observer.next({ type: 'done', cv: event.body as CV });
            observer.complete();
          }
        },
        error: err => observer.next({ type: 'error', error: err.message ?? 'Upload failed' }),
      });
    });
  }

  // ── PATCH /api/cv/{id}/activate ───────────────────────────────────────────
  setActive(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/activate`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  // ── POST /api/cv/optimize  (async — queues RabbitMQ job) ─────────────────
  /**
   * Returns immediately with { optimizationId, status:'queued' }.
   * The real result arrives via WebSocket (/user/topic/cv-optimization).
   * Use getOptimizationResult(id) as a polling fallback.
   */
  optimize(req: CVOptimizationRequest): Observable<CVOptimizationTriggerResponse> {
    return this.http.post<CVOptimizationTriggerResponse>(`${this.base}/optimize`, req);
  }

  getOptimizationResult(id: string): Observable<CVOptimizationResult> {
    return this.http.get<CVOptimizationResult>(`${this.base}/optimize/${id}`);
  }

  getOptimizationHistory(): Observable<CVOptimizationHistoryItem[]> {
    return this.http.get<CVOptimizationHistoryItem[]>(`${this.base}/optimization-history`);
  }

  // ── Polling helper ────────────────────────────────────────────────────────
  /**
   * Polls GET /api/cv/optimize/{id} every 3s until status is 'completed'|'failed'.
   * Used as fallback when WebSocket notification is not received.
   * Cancel by unsubscribing (takeUntil pattern).
   */
  pollUntilDone(id: string, cancel$: Subject<void>): Observable<CVOptimizationResult> {
    return timer(3000, 3000).pipe(
      switchMap(() => this.getOptimizationResult(id)),
      filter(r => r.status === 'completed' || r.status === 'failed'),
      takeUntil(cancel$),
    );
  }
}
