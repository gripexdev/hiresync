import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, delay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Job, JobSearchParams, JobSearchResult } from '../models/job.model';

// ── Moroccan job market mock data ─────────────────────────────────────────────
const MOCK_JOBS: Job[] = [
  {
    id: 'j1', title: 'Ingénieur DevOps Senior',
    company: 'OCP Group', location: 'Casablanca',
    contractType: 'CDI', sector: 'Industrie / Mining', experienceLevel: 'Senior',
    salary: '25 000 – 35 000 MAD/mois',
    description: `OCP Group recherche un Ingénieur DevOps Senior pour renforcer son équipe d'infrastructure cloud. Vous serez responsable de la mise en place et de la maintenance des pipelines CI/CD, de la gestion des environnements Kubernetes et de l'optimisation des coûts cloud.`,
    requirements: ['Kubernetes', 'Docker', 'Terraform', 'Jenkins', 'AWS ou Azure', 'Python', '5+ ans d\'expérience'],
    postedAt: '2026-06-01T09:00:00Z', matchScore: 92,
    source: 'recrute.ma',
    logoUrl: 'https://ui-avatars.com/api/?name=OCP&background=1B4F72&color=fff',
  },
  {
    id: 'j2', title: 'Développeur Full Stack Angular / Spring Boot',
    company: 'Maroc Telecom', location: 'Rabat',
    contractType: 'CDI', sector: 'Télécommunications', experienceLevel: 'Mid',
    salary: '15 000 – 22 000 MAD/mois',
    description: `Maroc Telecom recherche un développeur Full Stack passionné pour rejoindre l'équipe Digital Services. Vous développerez des applications web modernes pour des millions d'abonnés.`,
    requirements: ['Angular 17+', 'Spring Boot 3.x', 'PostgreSQL', 'REST APIs', 'Git', '3+ ans d\'expérience'],
    postedAt: '2026-05-30T10:00:00Z', matchScore: 87,
    source: 'LinkedIn',
    logoUrl: 'https://ui-avatars.com/api/?name=MT&background=2E86AB&color=fff',
  },
  {
    id: 'j3', title: 'Data Scientist — IA & Machine Learning',
    company: 'CIH Bank', location: 'Casablanca',
    contractType: 'CDI', sector: 'Finance & Banque', experienceLevel: 'Mid',
    salary: '20 000 – 30 000 MAD/mois',
    description: `CIH Bank intègre l'IA dans ses processus décisionnels. Le Data Scientist rejoindra une équipe innovation pour développer des modèles de scoring crédit et de détection de fraude.`,
    requirements: ['Python', 'TensorFlow / PyTorch', 'SQL', 'Scikit-learn', 'PySpark', 'Statistiques avancées'],
    postedAt: '2026-05-28T14:00:00Z', matchScore: 78,
    source: 'rekrut.ma',
    logoUrl: 'https://ui-avatars.com/api/?name=CIH&background=17A589&color=fff',
  },
  {
    id: 'j4', title: 'Chef de Projet Digital',
    company: 'Inwi', location: 'Casablanca',
    contractType: 'CDI', sector: 'Télécommunications', experienceLevel: 'Senior',
    salary: '22 000 – 32 000 MAD/mois',
    description: `Inwi recrute un Chef de Projet Digital expérimenté pour piloter la transformation numérique de ses offres B2C et B2B. Vous serez l'interface entre les équipes métier et IT.`,
    requirements: ['Gestion de projet', 'Agile / Scrum', 'JIRA', 'Analyse fonctionnelle', '5+ ans d\'expérience'],
    postedAt: '2026-05-27T09:00:00Z', matchScore: 65,
    source: 'LinkedIn',
    logoUrl: 'https://ui-avatars.com/api/?name=Inwi&background=EF4444&color=fff',
  },
  {
    id: 'j5', title: 'Développeur Mobile React Native',
    company: 'Attijariwafa Bank', location: 'Casablanca',
    contractType: 'CDI', sector: 'Finance & Banque', experienceLevel: 'Mid',
    salary: '16 000 – 24 000 MAD/mois',
    description: `Attijariwafa Bank recherche un développeur Mobile React Native pour son équipe fintech. Vous développerez les nouvelles fonctionnalités de l'application mobile AWB avec 2M+ d'utilisateurs actifs.`,
    requirements: ['React Native', 'TypeScript', 'Redux', 'REST APIs', 'iOS & Android', '3+ ans d\'expérience'],
    postedAt: '2026-05-25T11:00:00Z', matchScore: 71,
    source: 'recrute.ma',
    logoUrl: 'https://ui-avatars.com/api/?name=AWB&background=F59E0B&color=fff',
  },
  {
    id: 'j6', title: 'Architecte Cloud AWS / Azure',
    company: 'BMCE Bank', location: 'Casablanca',
    contractType: 'CDI', sector: 'Finance & Banque', experienceLevel: 'Senior',
    salary: '30 000 – 45 000 MAD/mois',
    description: `BMCE Bank of Africa recherche un Architecte Cloud pour accompagner sa migration vers le cloud hybride. Certification AWS Solutions Architect ou Azure Architect requise.`,
    requirements: ['AWS / Azure', 'Architecture microservices', 'Terraform', 'Kubernetes', 'Sécurité cloud', '7+ ans'],
    postedAt: '2026-05-22T10:00:00Z', matchScore: 83,
    source: 'LinkedIn',
    logoUrl: 'https://ui-avatars.com/api/?name=BMCE&background=3B82F6&color=fff',
  },
  {
    id: 'j7', title: 'Stage — Développeur Backend Java',
    company: 'HPS (HighTech Payment Systems)', location: 'Casablanca',
    contractType: 'Stage', sector: 'FinTech', experienceLevel: 'Junior',
    salary: '4 000 – 5 000 MAD/mois',
    description: `HPS, leader africain du paiement électronique, recrute des stagiaires backend Java pour renforcer ses équipes produit. Stage de 6 mois avec possibilité d'embauche.`,
    requirements: ['Java 17+', 'Spring Boot', 'SQL', 'Git', 'Bac+4 ou Bac+5'],
    postedAt: '2026-06-02T09:00:00Z', matchScore: 61,
    source: 'rekrut.ma',
    logoUrl: 'https://ui-avatars.com/api/?name=HPS&background=8B5CF6&color=fff',
  },
  {
    id: 'j8', title: 'Ingénieur Cybersécurité',
    company: 'Royal Air Maroc', location: 'Casablanca',
    contractType: 'CDI', sector: 'Transport / Aviation', experienceLevel: 'Mid',
    salary: '18 000 – 26 000 MAD/mois',
    description: `RAM renforce son SOC et recrute un Ingénieur Cybersécurité pour la protection de ses systèmes critiques. Vous réaliserez des audits de sécurité et répondrez aux incidents.`,
    requirements: ['SIEM', 'Pentest', 'ISO 27001', 'Networking', 'OSCP est un plus'],
    postedAt: '2026-05-29T14:00:00Z', matchScore: 55,
    source: 'recrute.ma',
    logoUrl: 'https://ui-avatars.com/api/?name=RAM&background=EF4444&color=fff',
  },
];

@Injectable({ providedIn: 'root' })
export class JobService {
  private readonly apiUrl = `${environment.apiUrl}/jobs`;

  constructor(private http: HttpClient) {}

  search(params: JobSearchParams): Observable<JobSearchResult> {
    // TODO: replace with HTTP call
    const filtered = MOCK_JOBS.filter(j => {
      const q = (params.q ?? '').toLowerCase();
      const loc = (params.location ?? '').toLowerCase();
      return (!q || j.title.toLowerCase().includes(q) || j.company.toLowerCase().includes(q))
          && (!loc || j.location.toLowerCase().includes(loc))
          && (!params.contractType || j.contractType === params.contractType)
          && (!params.sector || j.sector === params.sector)
          && (!params.experienceLevel || j.experienceLevel === params.experienceLevel);
    });
    return of({ jobs: filtered, total: filtered.length, page: params.page ?? 0, size: params.size ?? 10 }).pipe(delay(400));
  }

  getById(id: string): Observable<Job> {
    const job = MOCK_JOBS.find(j => j.id === id);
    if (!job) return new Observable(s => s.error('Job not found'));
    return of(job).pipe(delay(300));
  }

  getSimilar(jobId: string): Observable<Job[]> {
    return of(MOCK_JOBS.filter(j => j.id !== jobId).slice(0, 3)).pipe(delay(300));
  }
}
