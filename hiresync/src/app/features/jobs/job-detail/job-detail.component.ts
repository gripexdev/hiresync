import { Component, inject, OnInit, signal, Input, computed } from '@angular/core';
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

export interface DescSection {
  title: string;
  icon:  string;
  color: string;          // accent color for the section header bar
  paragraphs: string[][]; // each inner array = list of lines (>1 → bullet list)
}

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

  /** Parses the raw description string into styled sections */
  readonly parsedDesc = computed((): DescSection[] => {
    const desc = this.job()?.description ?? '';
    if (!desc.trim()) return [];

    const PROFIL_MARKER = 'Profil recherché :';
    const idx = desc.indexOf(PROFIL_MARKER);

    if (idx === -1) {
      // No separator — render as a single generic section
      return [{
        title:      'Description du poste',
        icon:       'description',
        color:      '#2E86AB',
        paragraphs: this._parseParagraphs(desc),
      }];
    }

    const sections: DescSection[] = [];
    const posteText  = desc.slice(0, idx).trim();
    const profilText = desc.slice(idx + PROFIL_MARKER.length).trim();

    if (posteText) {
      sections.push({
        title:      'Mission & Responsabilités',
        icon:       'rocket_launch',
        color:      '#2E86AB',
        paragraphs: this._parseParagraphs(posteText),
      });
    }
    if (profilText) {
      sections.push({
        title:      'Profil recherché',
        icon:       'person_search',
        color:      '#17A589',
        paragraphs: this._parseParagraphs(profilText),
      });
    }
    return sections;
  });

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

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Splits raw text into paragraphs separated by blank lines (\n\n).
   * Within each paragraph, lines split by \n become a bullet list.
   */
  private _parseParagraphs(text: string): string[][] {
    return text
      .split(/\n\n+/)
      .map(block => block.trim())
      .filter(block => block.length > 0)
      .map(block => {
        const lines = block.split('\n').map(l => l.trim()).filter(l => l.length > 0);
        // Multiple lines → bullet list; single line → plain paragraph
        return lines.length > 1 ? lines : [block];
      });
  }
}
