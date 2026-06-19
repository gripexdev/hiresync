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
import { forkJoin } from 'rxjs';
import { ApplicationService } from '../../core/services/application.service';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';

interface ColumnDef { id: ApplicationStatus; label: string; color: string; }

/** Per-column kanban state — each status is paginated independently on the server. */
interface ColData { items: Application[]; page: number; total: number; loading: boolean; }

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, MatMenuModule, MatSnackBarModule, DragDropModule,
    StatusBadgeComponent, PaginatorComponent],
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
})
export class ApplicationsComponent implements OnInit {
  private svc   = inject(ApplicationService);
  private snack = inject(MatSnackBar);

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
  readonly statusOptions = this.columnDefs;

  /** How many cards a column fetches per "page" / "Voir plus" click. */
  private readonly KANBAN_PAGE = 6;

  // ── Kanban: one independently-paginated column per status ────────────────────
  kanban = signal<Record<ApplicationStatus, ColData>>(this.emptyKanban());

  private emptyKanban(): Record<ApplicationStatus, ColData> {
    const rec = {} as Record<ApplicationStatus, ColData>;
    for (const d of this.columnDefs) rec[d.id] = { items: [], page: 0, total: 0, loading: false };
    return rec;
  }

  /** View model for the template — columnDefs joined with their live server state. */
  columns = computed(() =>
    this.columnDefs.map(def => {
      const c = this.kanban()[def.id];
      return {
        ...def,
        items: c.items,
        total: c.total,
        loading: c.loading,
        hiddenCount: Math.max(0, c.total - c.items.length),   // more available on the server
        expandable: c.items.length > this.KANBAN_PAGE,        // can collapse back down
      };
    }));

  /** Grand total across all columns — drives the header and empty state. */
  totalCount = computed(() =>
    this.columnDefs.reduce((sum, d) => sum + this.kanban()[d.id].total, 0));

  // ── Table: classic server-side pagination (all statuses) ─────────────────────
  tableRows     = signal<Application[]>([]);
  tablePage     = signal(1);
  tablePageSize = signal(8);
  tableTotal    = signal(0);
  private tableLoaded = false;

  ngOnInit(): void {
    this.loading.set(true);
    // Open all columns' first page in parallel so the board paints in one go.
    forkJoin(this.columnDefs.map(d =>
      this.svc.getPage({ status: d.id, page: 0, size: this.KANBAN_PAGE }),
    )).subscribe({
      next: pages => {
        this.kanban.update(k => {
          const next = { ...k };
          this.columnDefs.forEach((d, i) => {
            next[d.id] = { items: pages[i].content, page: 0, total: pages[i].totalElements, loading: false };
          });
          return next;
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // ── View switching (table loads lazily on first open) ────────────────────────
  switchView(v: 'kanban' | 'table'): void {
    this.view.set(v);
    if (v === 'table' && !this.tableLoaded) this.loadTable();
  }

  private loadTable(): void {
    this.svc.getPage({ page: this.tablePage() - 1, size: this.tablePageSize() }).subscribe({
      next: p => { this.tableRows.set(p.content); this.tableTotal.set(p.totalElements); this.tableLoaded = true; },
    });
  }

  goToTablePage(p: number): void { this.tablePage.set(p); this.loadTable(); }
  changeTablePageSize(n: number): void { this.tablePageSize.set(n); this.tablePage.set(1); this.loadTable(); }

  // ── Kanban column lazy-load ──────────────────────────────────────────────────
  showMore(status: ApplicationStatus): void { this.loadColumn(status, true); }
  showLess(status: ApplicationStatus): void { this.loadColumn(status, false); }

  private loadColumn(status: ApplicationStatus, append: boolean): void {
    const cur = this.kanban()[status];
    if (cur.loading) return;
    const nextPage = append ? cur.page + 1 : 0;
    this.patchCol(status, { loading: true });
    this.svc.getPage({ status, page: nextPage, size: this.KANBAN_PAGE }).subscribe({
      next: p => this.patchCol(status, {
        items: append ? [...this.kanban()[status].items, ...p.content] : p.content,
        page: nextPage,
        total: p.totalElements,
        loading: false,
      }),
      error: () => this.patchCol(status, { loading: false }),
    });
  }

  private patchCol(status: ApplicationStatus, patch: Partial<ColData>): void {
    this.kanban.update(k => ({ ...k, [status]: { ...k[status], ...patch } }));
  }

  // ── Drag & drop between columns ──────────────────────────────────────────────
  onDrop(event: CdkDragDrop<ApplicationStatus>): void {
    const app: Application = event.item.data;
    const target = event.container.data;           // the column's status id
    if (event.previousContainer === event.container || app.status === target) return;
    this.changeStatus(app, target);
  }

  // ── Manual status change (menu / modal / drag) ───────────────────────────────
  changeStatus(app: Application, status: ApplicationStatus): void {
    if (app.status === status) return;
    const previous = app.status;
    this.updating.set(app.id);

    // Optimistic: move the card across columns and patch the table row.
    this.moveCard(app, previous, status);
    this.patchTableRow(app.id, { status });
    if (this.selected()?.id === app.id) this.selected.update(s => s ? { ...s, status } : s);

    this.svc.updateStatus(app.id, status).subscribe({
      next: updated => {
        this.patchTableRow(app.id, updated);
        this.updating.set(null);
        this.snack.open(`Statut mis à jour : ${this.labelFor(status)}`, 'OK', { duration: 2500 });
      },
      error: () => {
        // Roll back the optimistic move.
        this.moveCard({ ...app, status }, status, previous);
        this.patchTableRow(app.id, { status: previous });
        if (this.selected()?.id === app.id) this.selected.update(s => s ? { ...s, status: previous } : s);
        this.updating.set(null);
        this.snack.open('Échec de la mise à jour du statut.', 'OK', { duration: 3500 });
      },
    });
  }

  /** Move a card between two columns' loaded items, adjusting both totals. */
  private moveCard(app: Application, from: ApplicationStatus, to: ApplicationStatus): void {
    this.kanban.update(k => {
      const src = k[from];
      const tgt = k[to];
      const moved = { ...app, status: to };
      return {
        ...k,
        [from]: { ...src, items: src.items.filter(a => a.id !== app.id), total: Math.max(0, src.total - 1) },
        [to]:   { ...tgt, items: [moved, ...tgt.items.filter(a => a.id !== app.id)], total: tgt.total + 1 },
      };
    });
  }

  private patchTableRow(id: string, patch: Partial<Application>): void {
    this.tableRows.update(list => list.map(a => a.id === id ? { ...a, ...patch } : a));
  }

  labelFor(status: ApplicationStatus): string {
    return this.columnDefs.find(c => c.id === status)?.label ?? status;
  }

  matchClass(score?: number): string {
    if (!score) return '';
    return score >= 80 ? 'match--high' : score >= 60 ? 'match--med' : 'match--low';
  }
}
