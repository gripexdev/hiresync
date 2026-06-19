import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

/**
 * Reusable, signal-based paginator.
 *
 * Stateless by design — the parent owns `page` / `pageSize` signals and reacts
 * to the `pageChange` / `pageSizeChange` outputs. Renders a "X–Y sur Z" summary,
 * a rows-per-page selector and a windowed page-number strip (with ellipses for
 * large ranges). Hides itself entirely when everything fits on a single page.
 */
@Component({
  selector: 'app-paginator',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule],
  templateUrl: './paginator.component.html',
  styleUrls: ['./paginator.component.scss'],
})
export class PaginatorComponent {
  // ── Inputs ──────────────────────────────────────────────────────────────────
  total           = input.required<number>();
  page            = input.required<number>();
  pageSize        = input.required<number>();
  pageSizeOptions = input<number[]>([10, 25, 50]);
  itemLabel       = input<string>('éléments');

  // ── Outputs ─────────────────────────────────────────────────────────────────
  pageChange     = output<number>();
  pageSizeChange = output<number>();

  // ── Derived ───────────────────────────────────────────────────────────────────
  totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  rangeStart = computed(() => (this.total() === 0 ? 0 : (this.page() - 1) * this.pageSize() + 1));
  rangeEnd   = computed(() => Math.min(this.page() * this.pageSize(), this.total()));

  /** Whether the paginator is worth showing at all. */
  multiPage = computed(() => this.totalPages() > 1);

  /** Windowed page list — `-1` marks an ellipsis gap. */
  pages = computed<number[]>(() => {
    const total = this.totalPages();
    const cur = this.page();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

    const out: number[] = [1];
    const left = Math.max(2, cur - 1);
    const right = Math.min(total - 1, cur + 1);
    if (left > 2) out.push(-1);
    for (let i = left; i <= right; i++) out.push(i);
    if (right < total - 1) out.push(-1);
    out.push(total);
    return out;
  });

  // ── Actions ─────────────────────────────────────────────────────────────────
  go(p: number): void {
    if (p >= 1 && p <= this.totalPages() && p !== this.page()) this.pageChange.emit(p);
  }

  onSize(event: Event): void {
    this.pageSizeChange.emit(+(event.target as HTMLSelectElement).value);
  }
}
