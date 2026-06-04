import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { NotificationService } from '../../core/services/notification.service';
import { Notification } from '../../core/models/notification.model';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.scss'],
})
export class NotificationsComponent implements OnInit {
  private svc = inject(NotificationService);

  notifications = signal<Notification[]>([]);
  loading       = signal(true);

  readonly icons: Record<string, string> = {
    job_match:           'hub',
    cv_optimized:        'auto_awesome',
    application_update:  'track_changes',
    interview_scheduled: 'calendar_today',
    offer_received:      'celebration',
  };

  readonly iconColors: Record<string, string> = {
    job_match:           '#2E86AB',
    cv_optimized:        '#17A589',
    application_update:  '#F59E0B',
    interview_scheduled: '#10B981',
    offer_received:      '#8B5CF6',
  };

  readonly typeLabels: Record<string, string> = {
    job_match:           'Nouvelle offre',
    cv_optimized:        'CV optimisé',
    application_update:  'Candidature',
    interview_scheduled: 'Entretien',
    offer_received:      'Offre reçue',
  };

  ngOnInit(): void {
    this.svc.getAll().subscribe(n => { this.notifications.set(n); this.loading.set(false); });
  }

  markRead(n: Notification): void {
    if (n.read) return;
    this.svc.markRead(n.id).subscribe(() =>
      this.notifications.update(list => list.map(x => x.id === n.id ? { ...x, read: true } : x))
    );
  }

  markAllRead(): void {
    this.svc.markAllRead().subscribe(() =>
      this.notifications.update(list => list.map(n => ({ ...n, read: true })))
    );
  }

  get unreadCount(): number { return this.notifications().filter(n => !n.read).length; }

  timeAgo(date: string): string {
    const mins = Math.floor((Date.now() - new Date(date).getTime()) / 60000);
    if (mins < 60)   return `Il y a ${mins} min`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24)    return `Il y a ${hrs}h`;
    return `Il y a ${Math.floor(hrs / 24)} j`;
  }
}
