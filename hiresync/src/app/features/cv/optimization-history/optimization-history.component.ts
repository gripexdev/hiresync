import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { CvService, HistoryStats } from '../../../core/services/cv.service';
import { CVOptimizationHistoryItem } from '../../../core/models/cv.model';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';

type FilterKey = 'all' | 'completed' | 'rejected' | 'failed';

@Component({
  selector: 'app-optimization-history',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule, PaginatorComponent],
  templateUrl: './optimization-history.component.html',
  styleUrls: ['./optimization-history.component.scss'],
})
export class OptimizationHistoryComponent implements OnInit {
  private svc = inject(CvService);

  // ── State (current page only — the rest lives on the server) ─────────────────
  history = signal<CVOptimizationHistoryItem[]>([]);
  loading = signal(true);
  errored = signal(false);

  filter = signal<FilterKey>('all');
  search = signal('');

  // ── Pagination (server-driven) ───────────────────────────────────────────────
  page     = signal(1);          // 1-based for the paginator UI
  pageSize = signal(10);
  total    = signal(0);          // total elements matching the current filter/search

  // ── Whole-dataset stats (cards + filter-tab counts) ──────────────────────────
  stats = signal<HistoryStats | null>(null);

  /** True once we know the user has zero optimizations at all (unfiltered). */
  get isEmpty(): boolean { return this.stats() !== null && this.stats()!.total === 0; }

  // Debounced search pipeline — avoids a request per keystroke.
  private search$ = new Subject<string>();

  // ── Filter tabs with live counts (from server stats) ─────────────────────────
  filters() {
    const s = this.stats();
    return [
      { key: 'all'       as FilterKey, label: 'Toutes',        icon: 'apps',         count: s?.total     ?? 0 },
      { key: 'completed' as FilterKey, label: 'Optimisées',    icon: 'check_circle', count: s?.completed ?? 0 },
      { key: 'rejected'  as FilterKey, label: 'Incompatibles', icon: 'block',        count: s?.rejected  ?? 0 },
      { key: 'failed'    as FilterKey, label: 'Échecs',        icon: 'error',        count: s?.failed    ?? 0 },
    ];
  }

  // ── Lifecycle ───────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.search$.pipe(debounceTime(350), distinctUntilChanged()).subscribe(() => {
      this.page.set(1);
      this.load();
    });
    this.loadStats();
    this.load();
  }

  private loadStats(): void {
    this.svc.getHistoryStats().subscribe({
      next: s => this.stats.set(s),
      error: () => {},
    });
  }

  private load(): void {
    // Note: we don't flip `loading` here — it only gates the first-paint spinner.
    // Filter/search/page changes update the table in place without blanking the UI.
    this.errored.set(false);
    this.svc.getHistoryPage({
      status: this.filter(),
      q: this.search(),
      page: this.page() - 1,
      size: this.pageSize(),
    }).subscribe({
      next: p => {
        this.history.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => { this.errored.set(true); this.loading.set(false); },
    });
  }

  setFilter(key: FilterKey): void {
    if (this.filter() === key) return;
    this.filter.set(key);
    this.page.set(1);
    this.load();
  }

  onSearch(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.search.set(v);
    this.search$.next(v);
  }

  clearSearch(): void {
    if (!this.search()) return;
    this.search.set('');
    this.page.set(1);
    this.load();
  }

  // ── Pagination handlers ─────────────────────────────────────────────────────
  goToPage(p: number): void { this.page.set(p); this.load(); }
  changePageSize(n: number): void { this.pageSize.set(n); this.page.set(1); this.load(); }

  // ── Display helpers ─────────────────────────────────────────────────────────
  scoreColor(score: number): string {
    if (score >= 80) return '#10B981';
    if (score >= 60) return '#F59E0B';
    return '#EF4444';
  }

  /** Friendly model name from a raw provider/model id. */
  modelLabel(item: CVOptimizationHistoryItem): string {
    const raw = (item.modelUsed ?? '').toLowerCase();
    if (!raw) return '—';
    if (raw.includes('gemini'))   return 'Gemini 2.0 Flash';
    if (raw.includes('groq') || raw.includes('llama')) return 'Groq Llama 3.3';
    if (raw.includes('gemma'))    return 'Gemma';
    if (raw.includes('mistral'))  return 'Mistral';
    if (raw.includes('qwen'))     return 'Qwen3';
    if (raw.includes('nemotron')) return 'Nemotron';
    return item.modelUsed!;
  }

  trackById(_: number, item: CVOptimizationHistoryItem): string { return item.id; }
}
