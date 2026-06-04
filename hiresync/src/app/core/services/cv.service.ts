import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CV, CVOptimizationRequest, CVOptimizationResult } from '../models/cv.model';

const MOCK_CVS: CV[] = [
  {
    id: 'cv1', fileName: 'CV_Othmane_Sadiky_2026.pdf',
    uploadedAt: '2026-05-15T10:00:00Z', atsScore: 62, isActive: true,
    parsedSections: [
      { title: 'Résumé', content: 'Ingénieur Full Stack avec 3 ans d\'expérience en Angular et Spring Boot.' },
      { title: 'Compétences', content: 'Angular, Spring Boot, PostgreSQL, Docker, Git' },
      { title: 'Expérience', content: 'Développeur Full Stack @ Maroc Telecom (2023–2026)' },
      { title: 'Formation', content: 'Cycle Ingénieur GSI — École Nationale des Sciences Appliquées (2026)' },
    ],
  },
  {
    id: 'cv2', fileName: 'CV_Othmane_Optimise_OCP.pdf',
    uploadedAt: '2026-06-01T14:00:00Z', atsScore: 88, isActive: false,
  },
];

const MOCK_OPTIMIZATION: CVOptimizationResult = {
  id: 'opt1', status: 'completed',
  cvId: 'cv1', jobId: 'j1',
  jobTitle: 'Ingénieur DevOps Senior',
  originalScore: 62, optimizedScore: 88,
  suggestedChanges: [
    { type: 'keyword_added', description: 'Ajout de "Kubernetes" et "Terraform" dans la section Compétences', before: 'Docker, Git', after: 'Docker, Kubernetes, Terraform, Git' },
    { type: 'section_rewritten', description: 'Reformulation du résumé pour cibler le poste DevOps', before: 'Ingénieur Full Stack avec 3 ans d\'expérience.', after: 'Ingénieur DevOps / Full Stack avec 3 ans d\'expérience en automatisation CI/CD, orchestration Kubernetes et infrastructure as code.' },
    { type: 'keyword_added', description: 'Ajout "CI/CD", "Jenkins", "AWS" identifiés dans l\'offre OCP', before: undefined, after: 'CI/CD Jenkins, AWS EKS' },
    { type: 'format_improved', description: 'Réorganisation des sections selon le format ATS recommandé' },
    { type: 'skill_added', description: 'Ajout de la certification AWS mentionnée comme préférence dans l\'offre' },
  ],
  optimizedCvUrl: '/assets/mock-cv-optimized.pdf',
  createdAt: '2026-06-01T14:00:00Z',
  completedAt: '2026-06-01T14:00:15Z',
};

@Injectable({ providedIn: 'root' })
export class CvService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<CV[]> {
    return of(MOCK_CVS).pipe(delay(300));
  }

  upload(file: File): Observable<CV> {
    const newCv: CV = {
      id: 'cv' + Date.now(), fileName: file.name,
      uploadedAt: new Date().toISOString(), atsScore: 0, isActive: false,
    };
    return of(newCv).pipe(delay(800));
  }

  optimize(req: CVOptimizationRequest): Observable<{ optimizationId: string }> {
    return of({ optimizationId: 'opt' + Date.now() }).pipe(delay(500));
  }

  getOptimizationResult(id: string): Observable<CVOptimizationResult> {
    return of(MOCK_OPTIMIZATION).pipe(delay(300));
  }
}
