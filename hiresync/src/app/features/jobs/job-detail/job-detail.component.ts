import { Component, inject, OnInit, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DatePipe } from '@angular/common';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { JobService } from '../../../core/services/job.service';
import { ApplicationService } from '../../../core/services/application.service';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, MatSnackBarModule, DatePipe],
  templateUrl: './job-detail.component.html',
  styleUrls: ['./job-detail.component.scss'],
})
export class JobDetailComponent implements OnInit {
  @Input() id!: string;

  private jobSvc  = inject(JobService);
  private appSvc  = inject(ApplicationService);
  private snack   = inject(MatSnackBar);
  private router  = inject(Router);

  job         = signal<Job | null>(null);
  similar     = signal<Job[]>([]);
  loading     = signal(true);
  applying    = signal(false);
  optimizing  = signal(false);

  ngOnInit(): void {
    this.jobSvc.getById(this.id).subscribe(j => { this.job.set(j); this.loading.set(false); });
    this.jobSvc.getSimilar(this.id).subscribe(j => this.similar.set(j));
  }

  apply(): void {
    this.applying.set(true);
    this.appSvc.apply(this.id, 'cv1').subscribe(() => {
      this.applying.set(false);
      this.snack.open('✅ Candidature envoyée avec succès !', 'OK', { duration: 3500 });
    });
  }

  optimizeCV(): void {
    this.optimizing.set(true);
    setTimeout(() => {
      this.optimizing.set(false);
      this.router.navigate(['/cv/optimize/opt1']);
    }, 1200);
  }

  matchClass(score?: number): string {
    if (!score) return '';
    if (score >= 80) return 'match--high';
    if (score >= 60) return 'match--med';
    return 'match--low';
  }
}
