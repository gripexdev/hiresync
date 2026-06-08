import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { JobService } from '../../../core/services/job.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Job, ContractType, ExperienceLevel } from '../../../core/models/job.model';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-job-search',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatIconModule, MatButtonModule, MatChipsModule,
    MatProgressSpinnerModule, MatSnackBarModule],
  templateUrl: './job-search.component.html',
  styleUrls: ['./job-search.component.scss'],
})
export class JobSearchComponent implements OnInit {
  private fb    = inject(FormBuilder);
  private svc   = inject(JobService);
  private http  = inject(HttpClient);
  private snack = inject(MatSnackBar);
  auth          = inject(AuthService);

  jobs        = signal<Job[]>([]);
  total       = signal(0);
  loading     = signal(true);
  currentPage = signal(0);
  totalPages  = signal(0);

  // Admin toolbar state
  scraping  = signal(false);
  enriching = signal(false);

  readonly PAGE_SIZE = 10;

  readonly contractTypes: ContractType[]      = ['CDI', 'CDD', 'Stage', 'Freelance', 'Alternance'];
  readonly experienceLevels: ExperienceLevel[] = ['Junior', 'Mid', 'Senior', 'Manager', 'Director'];
  readonly sectors = ['Télécommunications', 'Finance & Banque', 'Industrie / Mining', 'FinTech', 'Transport / Aviation', 'IT / Conseil'];

  readonly rangeStart = computed(() => this.currentPage() * this.PAGE_SIZE + 1);
  readonly rangeEnd   = computed(() => this.currentPage() * this.PAGE_SIZE + this.jobs().length);

  readonly visiblePages = computed((): (number | null)[] => {
    const total = this.totalPages();
    const cur   = this.currentPage();

    if (total <= 7) {
      const all: (number | null)[] = [];
      for (let i = 0; i < total; i++) all.push(i);
      return all;
    }

    const pages: (number | null)[] = [0];
    if (cur > 2) pages.push(null);
    for (let i = Math.max(1, cur - 1); i <= Math.min(total - 2, cur + 1); i++) pages.push(i);
    if (cur < total - 3) pages.push(null);
    pages.push(total - 1);
    return pages;
  });

  filters = this.fb.group({
    q:               [''],
    location:        [''],
    contractType:    [null],
    sector:          [null],
    experienceLevel: [null],
  });

  ngOnInit(): void {
    this._search();
    this.filters.valueChanges.pipe(debounceTime(400), distinctUntilChanged()).subscribe(() => {
      this.currentPage.set(0);
      this._search();
    });
  }

  private _search(): void {
    this.loading.set(true);
    const v = this.filters.value as any;
    this.svc.search({ ...v, page: this.currentPage(), size: this.PAGE_SIZE }).subscribe(r => {
      this.jobs.set(r.jobs);
      this.total.set(r.total);
      this.totalPages.set(r.totalPages);
      this.loading.set(false);
    });
  }

  goToPage(page: number | null): void {
    if (page === null) return;
    if (page < 0 || page >= this.totalPages()) return;
    this.currentPage.set(page);
    this._search();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  clearFilters(): void { this.filters.reset(); }

  // ── Admin actions ─────────────────────────────────────────────────────────

  triggerScrape(): void {
    this.scraping.set(true);
    this.http.post<any>(`${environment.apiUrl}/admin/scrape/trigger`, {}).subscribe({
      next: r => {
        this.scraping.set(false);
        this.snack.open(`✅ ${r.newJobsSaved} nouvelles offres scraped (total: ${r.totalJobsInDb})`, 'OK', { duration: 4000 });
        this.currentPage.set(0);
        this._search();
      },
      error: () => {
        this.scraping.set(false);
        this.snack.open('❌ Erreur lors du scraping', 'OK', { duration: 3000 });
      },
    });
  }

  triggerEnrich(): void {
    this.enriching.set(true);
    this.http.post<any>(`${environment.apiUrl}/admin/enrich/trigger`, {}).subscribe({
      next: r => {
        this.enriching.set(false);
        const msg = r.enrichedLeft > 0
          ? `✅ ${r.enrichedThisRun} offres enrichies — encore ${r.enrichedLeft} à enrichir`
          : `✅ ${r.enrichedThisRun} offres enrichies — tout est à jour !`;
        this.snack.open(msg, 'OK', { duration: 5000 });
        this._search();  // refresh to show new descriptions
      },
      error: () => {
        this.enriching.set(false);
        this.snack.open('❌ Erreur lors de l\'enrichissement', 'OK', { duration: 3000 });
      },
    });
  }

  matchClass(score?: number): string {
    if (!score) return '';
    if (score >= 80) return 'match--high';
    if (score >= 60) return 'match--med';
    return 'match--low';
  }

  timeAgo(date: string): string {
    const d = Math.floor((Date.now() - new Date(date).getTime()) / 86400000);
    if (d === 0) return "Aujourd'hui";
    if (d === 1) return 'Hier';
    return `Il y a ${d} jours`;
  }
}
