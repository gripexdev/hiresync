import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';
import { ApplicationService } from '../../core/services/application.service';
import { NotificationService } from '../../core/services/notification.service';
import { Application, ApplicationStats } from '../../core/models/application.model';
import { Notification } from '../../core/models/notification.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule, StatusBadgeComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit {
  auth         = inject(AuthService);
  appService   = inject(ApplicationService);
  notifService = inject(NotificationService);

  stats         = signal<ApplicationStats | null>(null);
  applications  = signal<Application[]>([]);
  notifications = signal<Notification[]>([]);
  loading       = signal(true);

  readonly quickActions = [
    { icon: 'work_outline',  label: 'Rechercher des offres', route: '/jobs',         color: '#2E86AB' },
    { icon: 'auto_awesome',  label: 'Optimiser mon CV',      route: '/cv',           color: '#17A589' },
    { icon: 'upload_file',   label: 'Uploader un CV',        route: '/cv',           color: '#1B4F72' },
    { icon: 'track_changes', label: 'Mes candidatures',      route: '/applications', color: '#8B5CF6' },
  ];

  readonly notifIcons: Record<string, string> = {
    job_match:           'hub',
    cv_optimized:        'auto_awesome',
    application_update:  'track_changes',
    interview_scheduled: 'calendar_today',
    offer_received:      'celebration',
  };

  ngOnInit(): void {
    this.appService.getStats().subscribe(s => this.stats.set(s));
    this.appService.getAll().subscribe(a => { this.applications.set(a.slice(0, 5)); this.loading.set(false); });
    this.notifService.getAll().subscribe(n => this.notifications.set(n.slice(0, 5)));
  }
}
