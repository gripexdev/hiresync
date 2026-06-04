import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CvService } from '../../../core/services/cv.service';
import { CV } from '../../../core/models/cv.model';

@Component({
  selector: 'app-cv-manager',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, MatSnackBarModule],
  templateUrl: './cv-manager.component.html',
  styleUrls: ['./cv-manager.component.scss'],
})
export class CvManagerComponent implements OnInit {
  private svc   = inject(CvService);
  private snack = inject(MatSnackBar);

  cvs      = signal<CV[]>([]);
  loading  = signal(true);
  uploading = signal(false);

  ngOnInit(): void {
    this.svc.getAll().subscribe(c => { this.cvs.set(c); this.loading.set(false); });
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.svc.upload(file).subscribe(cv => {
      this.cvs.update(list => [cv, ...list]);
      this.uploading.set(false);
      this.snack.open('✅ CV uploadé avec succès !', 'OK', { duration: 3000 });
    });
  }

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

  circumference = 2 * Math.PI * 28;
  dashOffset(score: number): number {
    return this.circumference * (1 - score / 100);
  }
}
