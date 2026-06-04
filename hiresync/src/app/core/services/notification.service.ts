import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay } from 'rxjs';
import { Notification } from '../models/notification.model';

const MOCK_NOTIFICATIONS: Notification[] = [
  { id: 'n1', type: 'interview_scheduled', title: 'Entretien confirmé', message: 'OCP Group a confirmé votre entretien technique pour le 10/06 à 10h.', read: false, createdAt: '2026-06-03T09:00:00Z', link: '/applications' },
  { id: 'n2', type: 'cv_optimized',        title: 'CV optimisé avec succès', message: 'Votre CV pour "Ingénieur DevOps" a été optimisé. Score ATS : 62 → 88%.', read: false, createdAt: '2026-06-01T14:15:00Z', link: '/cv/optimize/opt1' },
  { id: 'n3', type: 'job_match',           title: '3 nouvelles offres correspondent', message: 'Des offres chez OCP, RAM et BMCE correspondent à votre profil (>80%).', read: false, createdAt: '2026-05-31T08:00:00Z', link: '/jobs' },
  { id: 'n4', type: 'application_update',  title: 'Candidature en examen', message: 'Maroc Telecom a examiné votre candidature pour "Dev Full Stack".', read: true,  createdAt: '2026-05-29T16:00:00Z', link: '/applications' },
  { id: 'n5', type: 'offer_received',      title: 'Offre reçue !', message: 'Attijariwafa Bank vous a envoyé une offre pour "Développeur Mobile React Native".', read: true, createdAt: '2026-05-28T11:00:00Z', link: '/applications' },
];

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly unreadCount = signal(MOCK_NOTIFICATIONS.filter(n => !n.read).length);

  constructor(private http: HttpClient) {}

  getAll(): Observable<Notification[]> {
    return of(MOCK_NOTIFICATIONS).pipe(delay(300));
  }

  markRead(id: string): Observable<void> {
    const n = MOCK_NOTIFICATIONS.find(x => x.id === id);
    if (n) { n.read = true; this.unreadCount.set(MOCK_NOTIFICATIONS.filter(x => !x.read).length); }
    return of(void 0);
  }

  markAllRead(): Observable<void> {
    MOCK_NOTIFICATIONS.forEach(n => n.read = true);
    this.unreadCount.set(0);
    return of(void 0);
  }
}
