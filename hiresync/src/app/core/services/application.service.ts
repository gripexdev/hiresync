import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Application, ApplicationStats } from '../models/application.model';

const MOCK_APPLICATIONS: Application[] = [
  { id: 'a1', jobId: 'j1', jobTitle: 'Ingénieur DevOps Senior', company: 'OCP Group',         location: 'Casablanca', appliedAt: '2026-06-01', status: 'interview',  nextAction: 'Entretien technique le 10/06', matchScore: 92 },
  { id: 'a2', jobId: 'j2', jobTitle: 'Dev Full Stack Angular',   company: 'Maroc Telecom',     location: 'Rabat',       appliedAt: '2026-05-28', status: 'in_review', nextAction: 'En attente de retour RH',     matchScore: 87 },
  { id: 'a3', jobId: 'j3', jobTitle: 'Data Scientist IA/ML',     company: 'CIH Bank',          location: 'Casablanca', appliedAt: '2026-05-25', status: 'applied',   nextAction: undefined,                     matchScore: 78 },
  { id: 'a4', jobId: 'j4', jobTitle: 'Chef de Projet Digital',   company: 'Inwi',              location: 'Casablanca', appliedAt: '2026-05-20', status: 'rejected',  nextAction: undefined,                     matchScore: 65 },
  { id: 'a5', jobId: 'j5', jobTitle: 'Développeur Mobile RN',    company: 'Attijariwafa Bank', location: 'Casablanca', appliedAt: '2026-05-15', status: 'offer',     nextAction: 'Négociation salaire',         matchScore: 71 },
  { id: 'a6', jobId: 'j6', jobTitle: 'Architecte Cloud AWS',     company: 'BMCE Bank',         location: 'Casablanca', appliedAt: '2026-06-02', status: 'applied',   nextAction: undefined,                     matchScore: 83 },
  { id: 'a7', jobId: 'j7', jobTitle: 'Stage Backend Java',       company: 'HPS',               location: 'Casablanca', appliedAt: '2026-05-10', status: 'interview', nextAction: 'Entretien RH le 08/06',      matchScore: 61 },
];

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<Application[]> {
    return of(MOCK_APPLICATIONS).pipe(delay(400));
  }

  getStats(): Observable<ApplicationStats> {
    const apps = MOCK_APPLICATIONS;
    return of({
      total:      apps.length,
      pending:    apps.filter(a => a.status === 'applied' || a.status === 'in_review').length,
      interviews: apps.filter(a => a.status === 'interview').length,
      offers:     apps.filter(a => a.status === 'offer').length,
      rejected:   apps.filter(a => a.status === 'rejected').length,
    }).pipe(delay(200));
  }

  apply(jobId: string, cvId: string): Observable<Application> {
    const mock: Application = {
      id: 'a' + Date.now(), jobId, cvId,
      jobTitle: 'Nouveau poste', company: 'Entreprise', location: 'Casablanca',
      appliedAt: new Date().toISOString(), status: 'applied',
    };
    return of(mock).pipe(delay(600));
  }
}
