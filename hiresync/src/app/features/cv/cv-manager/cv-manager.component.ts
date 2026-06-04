import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CvService } from '../../../core/services/cv.service';
import { CV, CVOptimizationHistoryItem } from '../../../core/models/cv.model';
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

  cvs      = signal<CV[]>([]);
  history  = signal<CVOptimizationHistoryItem[]>([]);
  loading  = signal(true);

  // Upload state
  uploadProgress = signal<number>(0);
  uploading      = signal(false);

  // Per-CV action loading states
  activating = signal<string | null>(null);
  deleting   = signal<string | null>(null);

  // Derived helpers
  readonly circumference = 2 * Math.PI * 28;

  ngOnInit(): void {
    this.svc.getAll().subscribe({
      next: c => { this.cvs.set(c); this.loading.set(false); },
      error: () => this.loading.set(false),   // never stay frozen
    });
    this.svc.getOptimizationHistory().subscribe({
      next: h => this.history.set(h),
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
          this.cvs.update(list => [evt.cv!, ...list]);
          this.uploading.set(false);
          this.uploadProgress.set(0);
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
      this.deleting.set(null);
      this.snack.open('🗑️ CV supprimé', 'OK', { duration: 2500 });
    });
  }

  // ── Optimize via dialog ─────────────────────────────────────────────────────
  openOptimizeDialog(cv: CV): void {
    const ref = this.dialog.open(JobSelectorDialogComponent, {
      panelClass: 'hs-dialog',
      disableClose: false,
    });

    ref.afterClosed().subscribe((job: Job | null) => {
      if (!job) return;
      this.triggerOptimization(cv, job);
    });
  }

  private triggerOptimization(cv: CV, job: Job): void {
    this.svc.optimize({
      cvId: cv.id,
      jobId: job.id,
      jobTitle: job.title,
      company: job.company,
      jobDescription: `${job.title} chez ${job.company} — ${job.location}. ${job.description} Compétences: ${job.requirements?.join(', ')}`,
    }).subscribe({
      next: res => {
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

  historyForCv(cvId: string): CVOptimizationHistoryItem[] {
    return this.history(); // TODO: filter by cvId once backend sends it
  }

  /** Extract a readable model name from the full OpenRouter model ID */
  modelLabel(item: CVOptimizationHistoryItem): string {
    const raw = (item as any).modelUsed ?? '';
    if (raw.includes('gemma'))    return 'Gemma 4 31B';
    if (raw.includes('mistral'))  return 'Mistral 7B';
    if (raw.includes('llama'))    return 'LLaMA 3.3 70B';
    if (raw.includes('qwen'))     return 'Qwen3';
    if (raw.includes('nemotron')) return 'Nemotron';
    return raw || 'Gemma 4 31B';
  }
}
