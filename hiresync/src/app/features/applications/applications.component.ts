import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  CdkDragDrop, DragDropModule,
} from '@angular/cdk/drag-drop';
import { ApplicationService } from '../../core/services/application.service';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

interface ColumnDef { id: ApplicationStatus; label: string; color: string; }

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, MatMenuModule, MatSnackBarModule, DragDropModule, StatusBadgeComponent],
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
})
export class ApplicationsComponent implements OnInit {
  private svc   = inject(ApplicationService);
  private snack = inject(MatSnackBar);

  all      = signal<Application[]>([]);
  loading  = signal(true);
  view     = signal<'kanban' | 'table'>('kanban');
  selected = signal<Application | null>(null);
  updating = signal<string | null>(null);   // id of the application being updated

  readonly columnDefs: ColumnDef[] = [
    { id: 'applied',   label: 'Candidaté',  color: '#3B82F6' },
    { id: 'in_review', label: 'En examen',  color: '#F59E0B' },
    { id: 'interview', label: 'Entretien',  color: '#10B981' },
    { id: 'offer',     label: 'Offre',      color: '#8B5CF6' },
    { id: 'rejected',  label: 'Refusé',     color: '#EF4444' },
  ];

  /** All drop-list ids — connects every kanban column to every other for drag-and-drop. */
  readonly columnIds = this.columnDefs.map(c => 'col-' + c.id);

  /** Columns derived from the live application list — recomputes on any status change. */
  columns = computed(() =>
    this.columnDefs.map(def => ({
      ...def,
      apps: this.all().filter(a => a.status === def.id),
    })));

  /** Status options for the menu/select, excluding the current one. */
  readonly statusOptions = this.columnDefs;

  ngOnInit(): void {
    this.svc.getAll().subscribe({
      next: apps => { this.all.set(apps); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  // ── Drag & drop between columns ────────────────────────────────────────────────
  onDrop(event: CdkDragDrop<ApplicationStatus>): void {
    const app: Application = event.item.data;
    const target = event.container.data;           // the column's status id
    if (event.previousContainer === event.container || app.status === target) return;
    this.changeStatus(app, target);
  }

  // ── Manual status change (menu / modal) ────────────────────────────────────────
  changeStatus(app: Application, status: ApplicationStatus): void {
    if (app.status === status) return;
    const previous = app.status;
    this.updating.set(app.id);

    // Optimistic update
    this._patchLocal(app.id, { status });
    if (this.selected()?.id === app.id) this.selected.update(s => s ? { ...s, status } : s);

    this.svc.updateStatus(app.id, status).subscribe({
      next: updated => {
        this._patchLocal(app.id, updated);
        this.updating.set(null);
        this.snack.open(`Statut mis à jour : ${this.labelFor(status)}`, 'OK', { duration: 2500 });
      },
      error: () => {
        // Roll back on failure
        this._patchLocal(app.id, { status: previous });
        if (this.selected()?.id === app.id) this.selected.update(s => s ? { ...s, status: previous } : s);
        this.updating.set(null);
        this.snack.open('Échec de la mise à jour du statut.', 'OK', { duration: 3500 });
      },
    });
  }

  private _patchLocal(id: string, patch: Partial<Application>): void {
    this.all.update(list => list.map(a => a.id === id ? { ...a, ...patch } : a));
  }

  labelFor(status: ApplicationStatus): string {
    return this.columnDefs.find(c => c.id === status)?.label ?? status;
  }

  matchClass(score?: number): string {
    if (!score) return '';
    return score >= 80 ? 'match--high' : score >= 60 ? 'match--med' : 'match--low';
  }
}
