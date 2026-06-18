import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HttpErrorResponse } from '@angular/common/http';
import { CvService } from '../../../core/services/cv.service';
import { ApplicationService } from '../../../core/services/application.service';
import { CV } from '../../../core/models/cv.model';
import { Application } from '../../../core/models/application.model';

export interface ApplyDialogData {
  jobId: string;
  jobTitle: string;
  company: string | null;
}

@Component({
  selector: 'app-apply-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatDialogModule, MatIconModule,
    MatButtonModule, MatProgressSpinnerModule,
  ],
  templateUrl: './apply-dialog.component.html',
  styleUrls: ['./apply-dialog.component.scss'],
})
export class ApplyDialogComponent implements OnInit {
  data = inject<ApplyDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ApplyDialogComponent, Application>);
  private cvSvc     = inject(CvService);
  private appSvc    = inject(ApplicationService);

  cvs        = signal<CV[]>([]);
  loading    = signal(true);
  selectedId = signal<string | null>(null);
  coverNote  = signal('');
  submitting = signal(false);
  error      = signal<string | null>(null);

  readonly maxNote = 600;

  ngOnInit(): void {
    this.cvSvc.getAll().subscribe({
      next: cvs => {
        this.cvs.set(cvs);
        // Preselect the active CV, else the most recent one
        const active = cvs.find(c => c.isActive) ?? cvs[0];
        if (active) this.selectedId.set(active.id);
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); },
    });
  }

  select(cv: CV): void { this.selectedId.set(cv.id); }
  isSelected(cv: CV): boolean { return this.selectedId() === cv.id; }

  onNote(value: string): void {
    this.coverNote.set(value.slice(0, this.maxNote));
  }

  scoreColor(score: number): string {
    if (score >= 80) return '#10B981';
    if (score >= 60) return '#F59E0B';
    return '#EF4444';
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    return bytes > 1024 * 1024
      ? (bytes / (1024 * 1024)).toFixed(1) + ' MB'
      : Math.round(bytes / 1024) + ' KB';
  }

  submit(): void {
    const cvId = this.selectedId();
    if (!cvId || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    this.appSvc.apply(this.data.jobId, {
      cvId,
      coverNote: this.coverNote().trim() || undefined,
    }).subscribe({
      next: app => { this.submitting.set(false); this.dialogRef.close(app); },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(
          err.status === 409 ? (err.error?.message ?? 'Vous avez déjà postulé à cette offre.')
          : err.status === 401 || err.status === 403 ? 'Session expirée — reconnectez-vous.'
          : 'Une erreur est survenue. Réessayez.'
        );
      },
    });
  }

  cancel(): void { this.dialogRef.close(); }
}
