import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApplicationService } from '../../core/services/application.service';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

interface KanbanColumn { id: ApplicationStatus; label: string; color: string; apps: Application[]; }

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, StatusBadgeComponent],
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
})
export class ApplicationsComponent implements OnInit {
  private svc = inject(ApplicationService);

  all      = signal<Application[]>([]);
  loading  = signal(true);
  view     = signal<'kanban' | 'table'>('kanban');
  selected = signal<Application | null>(null);

  columns = signal<KanbanColumn[]>([
    { id: 'applied',   label: 'Candidaté',  color: '#3B82F6', apps: [] },
    { id: 'in_review', label: 'En examen',  color: '#F59E0B', apps: [] },
    { id: 'interview', label: 'Entretien',  color: '#10B981', apps: [] },
    { id: 'offer',     label: 'Offre',      color: '#8B5CF6', apps: [] },
    { id: 'rejected',  label: 'Refusé',     color: '#EF4444', apps: [] },
  ]);

  ngOnInit(): void {
    this.svc.getAll().subscribe(apps => {
      this.all.set(apps);
      this.loading.set(false);
      this.columns.update(cols => cols.map(col => ({
        ...col, apps: apps.filter(a => a.status === col.id),
      })));
    });
  }

  matchClass(score?: number): string {
    if (!score) return '';
    return score >= 80 ? 'match--high' : score >= 60 ? 'match--med' : 'match--low';
  }
}
