import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CvService } from '../../../core/services/cv.service';
import { CV } from '../../../core/models/cv.model';
import { JobSelectorDialogComponent } from '../../../shared/components/job-selector-dialog/job-selector-dialog.component';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-cv-manager',
  standalone: true,
  imports: [
    CommonModule, RouterModule,
    MatIconModule, MatButtonModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './cv-manager.component.html',
  styleUrls: ['./cv-manager.component.scss'],
})
export class CvManagerComponent implements OnInit {
  private svc    = inject(CvService);
  private snack  = inject(MatSnackBar);
  private dialog = inject(MatDialog);
  private router = inject(Router);

  cvs         = signal<CV[]>([]);   // accumulated across loaded pages
  historyCount = signal(0);         // total optimizations, for the bottom CTA
  loading     = signal(true);

  // Upload state
  uploadProgress = signal<number>(0);
  uploading      = signal(false);

  // Per-CV action loading states
  activating = signal<string | null>(null);
  deleting   = signal<string | null>(null);

  // Which cards have their parsed-content preview expanded
  expanded = signal<Set<string>>(new Set());

  // ── Server-side incremental load ("Afficher N de plus") ──────────────────────
  // The backend orders active-first then most-recent, so page 0 always opens on
  // the active CV. "Afficher plus" fetches the next page and appends it.
  readonly CV_PAGE = 6;
  cvTotal       = signal(0);    // total CVs on the server
  cvLoadingMore = signal(false);
  private cvPage = 0;           // index of the last page loaded (0-based)

  /** What the grid renders — the pages loaded so far. */
  visibleCvs = computed(() => this.cvs());
  hiddenCvCount = computed(() => Math.max(0, this.cvTotal() - this.cvs().length));
  allCvsShown   = computed(() => this.cvs().length >= this.cvTotal());

  /** Load the first page (resets accumulation). */
  private loadCvs(): void {
    this.cvPage = 0;
    this.loading.set(true);
    this.svc.getPage(0, this.CV_PAGE).subscribe({
      next: p => { this.cvs.set(p.content); this.cvTotal.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false),   // never stay frozen
    });
  }

  showMoreCvs(): void {
    if (this.cvLoadingMore() || this.allCvsShown()) return;
    this.cvLoadingMore.set(true);
    this.svc.getPage(this.cvPage + 1, this.CV_PAGE).subscribe({
      next: p => {
        this.cvPage += 1;
        this.cvs.update(list => [...list, ...p.content]);
        this.cvTotal.set(p.totalElements);
        this.cvLoadingMore.set(false);
      },
      error: () => this.cvLoadingMore.set(false),
    });
  }

  showLessCvs(): void { this.loadCvs(); }

  toggleExpand(id: string): void {
    this.expanded.update(set => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }
  isExpanded(id: string): boolean { return this.expanded().has(id); }

  // Derived helpers
  readonly circumference = 2 * Math.PI * 28;

  ngOnInit(): void {
    this.loadCvs();
    this.svc.getHistoryStats().subscribe({
      next: s => this.historyCount.set(s.total),
      error: () => {},
    });
  }

  // ── Upload ──────────────────────────────────────────────────────────────────
  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      this.snack.open('❌ Fichier trop volumineux (max 10 MB)', 'OK', { duration: 3500 });
      return;
    }
    this.uploading.set(true);
    this.uploadProgress.set(0);

    this.svc.upload(file).subscribe({
      next: evt => {
        if (evt.type === 'progress') {
          this.uploadProgress.set(evt.progress ?? 0);
        } else if (evt.type === 'done' && evt.cv) {
          this.uploading.set(false);
          this.uploadProgress.set(0);
          // Reload the first page so the new CV lands in correct server order and
          // the total count updates.
          this.loadCvs();
          this.snack.open('✅ CV uploadé — Score ATS initial calculé !', 'OK', { duration: 3500 });
        } else if (evt.type === 'error') {
          this.uploading.set(false);
          this.uploadProgress.set(0);
          this.snack.open(`❌ ${evt.error ?? 'Échec de l\'upload'}`, 'OK', { duration: 4000 });
        }
      },
      error: () => {
        this.uploading.set(false);
        this.uploadProgress.set(0);
        this.snack.open('❌ Échec de l\'upload. Vérifiez votre connexion.', 'OK', { duration: 4000 });
      },
    });
    // Reset input so same file can be re-selected
    (event.target as HTMLInputElement).value = '';
  }

  // ── Set active ──────────────────────────────────────────────────────────────
  setActive(cv: CV): void {
    if (cv.isActive) return;
    this.activating.set(cv.id);
    this.svc.setActive(cv.id).subscribe(() => {
      this.cvs.update(list => list.map(c => ({ ...c, isActive: c.id === cv.id })));
      this.activating.set(null);
      this.snack.open(`✅ "${cv.fileName}" défini comme CV actif`, 'OK', { duration: 3000 });
    });
  }

  // ── Delete ──────────────────────────────────────────────────────────────────
  deleteCv(cv: CV): void {
    if (!confirm(`Supprimer "${cv.fileName}" ? Cette action est irréversible.`)) return;
    this.deleting.set(cv.id);
    this.svc.delete(cv.id).subscribe(() => {
      this.cvs.update(list => list.filter(c => c.id !== cv.id));
      this.cvTotal.update(n => Math.max(0, n - 1));
      this.deleting.set(null);
      this.snack.open('🗑️ CV supprimé', 'OK', { duration: 2500 });
    });
  }

  // ── Optimize via dialog ─────────────────────────────────────────────────────
  openOptimizeDialog(cv: CV): void {
    const ref = this.dialog.open(JobSelectorDialogComponent, {
      panelClass: 'hs-dialog',
      disableClose: false,
      maxWidth: '92vw', // the dialog's own width is 520px — cap it so it never overflows a narrow phone
    });

    ref.afterClosed().subscribe((job: Job | null) => {
      if (!job) return;
      this.triggerOptimization(cv, job);
    });
  }

  private triggerOptimization(cv: CV, job: Job): void {
    // Some scraped offers have no company name — fall back to the same
    // placeholder shown in the UI rather than sending a blank string,
    // which fails the backend's @NotBlank validation with a 400.
    const company = job.company?.trim() ? job.company : 'Entreprise confidentielle';
    this.svc.optimize({
      cvId: cv.id,
      jobId: job.id,
      jobTitle: job.title,
      company,
      jobDescription: `${job.title} chez ${company} — ${job.location ?? ''}. ${job.description ?? ''} Compétences: ${job.requirements?.join(', ') ?? ''}`,
    }).subscribe({
      next: res => {
        if (res.alreadyOptimized) {
          this.snack.open('Vous avez déjà optimisé un CV pour cette offre — voici le résultat.', 'OK', { duration: 3500 });
          this.router.navigate(['/cv/optimize', res.optimizationId]);
          return;
        }
        this.snack.open(
          `🚀 Optimisation lancée via l\'IA (${res.optimizationId.slice(-6)}) — vous serez notifié`,
          'Voir', { duration: 5000 }
        );
        // Navigate to the optimizer page to show the live status
        this.router.navigate(['/cv/optimize', res.optimizationId], {
          queryParams: { cvId: cv.id, jobId: job.id, jobTitle: job.title },
        });
      },
      error: () => this.snack.open('❌ Impossible de lancer l\'optimisation', 'OK', { duration: 3500 }),
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────
  scoreColor(score: number): string {
    if (score >= 80) return '#10B981';
    if (score >= 60) return '#F59E0B';
    return '#EF4444';
  }

  scoreLabel(score: number): string {
    if (score >= 80) return 'Excellent';
    if (score >= 60) return 'Correct';
    return 'À améliorer';
  }

  dashOffset(score: number): number {
    return this.circumference * (1 - score / 100);
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    return bytes > 1024 * 1024
      ? (bytes / (1024 * 1024)).toFixed(1) + ' MB'
      : Math.round(bytes / 1024) + ' KB';
  }

}
