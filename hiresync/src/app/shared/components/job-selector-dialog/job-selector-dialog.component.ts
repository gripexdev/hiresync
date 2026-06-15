import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { JobService } from '../../../core/services/job.service';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-selector-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatButtonModule, MatProgressSpinnerModule,
  ],
  templateUrl: './job-selector-dialog.component.html',
  styleUrls: ['./job-selector-dialog.component.scss'],
})
export class JobSelectorDialogComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<JobSelectorDialogComponent>);
  private jobSvc    = inject(JobService);

  jobs     = signal<Job[]>([]);
  loading  = signal(true);
  selected = signal<Job | null>(null);

  search = new FormControl('');

  ngOnInit(): void {
    this._loadJobs('');
    this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(q => this._loadJobs(q ?? ''));
  }

  private _loadJobs(q: string): void {
    this.loading.set(true);
    this.jobSvc.search({ q, size: 8 }).subscribe(r => {
      this.jobs.set(r.jobs);
      this.loading.set(false);
    });
  }

  select(job: Job): void     { this.selected.set(job); }
  confirm(): void            { this.dialogRef.close(this.selected()); }
  cancel(): void             { this.dialogRef.close(null); }

  matchClass(score?: number): string {
    if (!score) return '';
    return score >= 80 ? 'match--high' : score >= 60 ? 'match--med' : 'match--low';
  }

  /** Joins company/location/contractType with " · ", skipping whichever are missing (some sources don't have all three). */
  jobMeta(job: Job): string {
    return [job.company || 'Entreprise confidentielle', job.location, job.contractType]
      .filter((part): part is string => !!part)
      .join(' · ');
  }
}
