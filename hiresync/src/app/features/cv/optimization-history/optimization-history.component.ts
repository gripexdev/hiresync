import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CvService } from '../../../core/services/cv.service';
import { CVOptimizationHistoryItem, OptimizationStatus } from '../../../core/models/cv.model';

type FilterKey = 'all' | 'completed' | 'rejected' | 'failed';

@Component({
  selector: 'app-optimization-history',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './optimization-history.component.html',
  styleUrls: ['./optimization-history.component.scss'],
})
export class OptimizationHistoryComponent implements OnInit {
  private svc = inject(CvService);

  // ── State ──────────────────────────────────────────────────────────────────
  history = signal<CVOptimizationHistoryItem[]>([]);
  loading = signal(true);
  errored = signal(false);

  filter = signal<FilterKey>('all');
  search = signal('');

  // ── Derived stats (on the full, unfiltered set) ─────────────────────────────
  completed = computed(() => this.history().filter(h => h.status === 'completed'));

  totalCount     = computed(() => this.history().length);
  completedCount = computed(() => this.completed().length);

  avgGain = computed(() => {
    const done = this.completed();
    if (!done.length) return 0;
    const sum = done.reduce((acc, h) => acc + (h.optimizedScore - h.originalScore), 0);
    return Math.round(sum / done.length);
  });

  bestScore = computed(() => {
    const done = this.completed();
    return done.length ? Math.max(...done.map(h => h.optimizedScore)) : 0;
  });

  // ── Filter tabs with live counts ────────────────────────────────────────────
  filters = computed(() => {
    const h = this.history();
    return [
      { key: 'all'       as FilterKey, label: 'Toutes',        icon: 'apps',         count: h.length },
      { key: 'completed' as FilterKey, label: 'Optimisées',    icon: 'check_circle', count: h.filter(x => x.status === 'completed').length },
      { key: 'rejected'  as FilterKey, label: 'Incompatibles', icon: 'block',        count: h.filter(x => x.status === 'rejected').length },
      { key: 'failed'    as FilterKey, label: 'Échecs',        icon: 'error',        count: h.filter(x => x.status === 'failed').length },
    ];
  });

  // ── The visible rows (filter + search applied) ──────────────────────────────
  visible = computed(() => {
    const f = this.filter();
    const q = this.search().trim().toLowerCase();
    return this.history().filter(item => {
      const matchesFilter = f === 'all' ? true : item.status === f;
      const matchesSearch = !q
        || item.jobTitle?.toLowerCase().includes(q)
        || item.company?.toLowerCase().includes(q);
      return matchesFilter && matchesSearch;
    });
  });

  // ── Lifecycle ───────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.svc.getOptimizationHistory().subscribe({
      next: h => { this.history.set(h); this.loading.set(false); },
      error: () => { this.errored.set(true); this.loading.set(false); },
    });
  }

  setFilter(key: FilterKey): void { this.filter.set(key); }

  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  clearSearch(): void { this.search.set(''); }

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
