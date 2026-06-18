import { Component, inject, OnInit, OnDestroy, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, takeUntil } from 'rxjs';
import { CvService } from '../../../core/services/cv.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { CVOptimizationResult, OptimizationStatus } from '../../../core/models/cv.model';

/** Processing step shown in the loading screen */
interface ProcessStep {
  label: string;
  status: 'done' | 'active' | 'pending';
}

@Component({
  selector: 'app-cv-optimizer',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule],
  templateUrl: './cv-optimizer.component.html',
  styleUrls: ['./cv-optimizer.component.scss'],
})
export class CvOptimizerComponent implements OnInit, OnDestroy {
  @Input() id!: string;    // optimizationId from route param

  private cvSvc    = inject(CvService);
  private wsSvc    = inject(WebSocketService);
  private route    = inject(ActivatedRoute);
  private snack    = inject(MatSnackBar);
  private destroy$ = new Subject<void>();
  private cancel$  = new Subject<void>();

  downloading = signal(false);
  boosting    = signal(false);

  // ── State ────────────────────────────────────────────────────────────────────
  result          = signal<CVOptimizationResult | null>(null);
  status          = signal<OptimizationStatus>('queued');
  activeProvider  = signal('');
  selectedMissing = signal<Set<string>>(new Set());
  steps          = signal<ProcessStep[]>([
    { label: 'Vérification de la compatibilité profil ↔ offre', status: 'pending' },
    { label: 'Analyse de l\'offre et extraction des mots-clés ATS', status: 'pending' },
    { label: 'Réécriture des sections par l\'IA',                status: 'pending' },
    { label: 'Calcul du score ATS final',                        status: 'pending' },
  ]);

  // Query params passed from cv-manager when launching a new optimization
  jobTitle = signal('');
  jobId    = signal('');
  cvId     = signal('');

  // ── Config ───────────────────────────────────────────────────────────────────
  readonly circumference = 2 * Math.PI * 52;

  readonly changeIcons: Record<string, string> = {
    keyword_added:    'add_circle',
    section_rewritten:'edit_note',
    format_improved:  'format_align_left',
    skill_added:      'psychology',
  };
  readonly changeColors: Record<string, string> = {
    keyword_added:    '#2E86AB',
    section_rewritten:'#17A589',
    format_improved:  '#8B5CF6',
    skill_added:      '#F59E0B',
  };

  // Fallbacks for any suggestion type the model returns outside the 4 canonical ones.
  private readonly defaultIcon  = 'auto_fix_high';
  private readonly defaultColor = '#64748B';

  /** Map any LLM-returned type onto a canonical type so styling is always applied. */
  private canonicalType(type: string): string {
    const t = (type ?? '').toLowerCase();
    if (t.includes('keyword')) return 'keyword_added';
    if (t.includes('skill') || t.includes('competenc')) return 'skill_added';
    if (t.includes('rewrit') || t.includes('summary') || t.includes('section') || t.includes('content')) return 'section_rewritten';
    if (t.includes('format') || t.includes('structure') || t.includes('contact') || t.includes('layout')) return 'format_improved';
    return '';   // truly unknown → use neutral default
  }

  changeIcon(type: string): string {
    return this.changeIcons[type] ?? this.changeIcons[this.canonicalType(type)] ?? this.defaultIcon;
  }
  changeColor(type: string): string {
    return this.changeColors[type] ?? this.changeColors[this.canonicalType(type)] ?? this.defaultColor;
  }

  // ── Lifecycle ────────────────────────────────────────────────────────────────
  ngOnInit(): void {
    const q = this.route.snapshot.queryParams;
    this.jobTitle.set(q['jobTitle'] ?? '');
    this.jobId.set(q['jobId'] ?? '');
    this.cvId.set(q['cvId'] ?? '');

    // ── Key decision: is this a history view or a fresh optimization? ─────────
    // When coming from history table: no query params → fetch result directly
    // When coming from cv-manager (new job): has cvId query param → start processing
    const isNewOptimization = !!q['cvId'];

    if (isNewOptimization) {
      this._startProcessing();
    } else {
      this._loadExistingResult();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.cancel$.next();
    this.cancel$.complete();
  }

  // ── View existing result from history ────────────────────────────────────────
  private _loadExistingResult(): void {
    this.status.set('processing'); // show a brief spinner while fetching
    this.cvSvc.getOptimizationResult(this.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: r => {
          // Populate job title from the result (not from query params)
          this.jobTitle.set(r.jobTitle ?? '');
          if (r.status === 'completed') {
            this._markAllStepsDone();
            this.result.set(r);
            this.status.set('completed');
          } else if (r.status === 'rejected') {
            this.result.set(r);
            this.status.set('rejected');
          } else if (r.status === 'failed') {
            this.status.set('failed');
          } else {
            // Still processing — join the live flow
            this._startProcessing();
          }
        },
        error: () => this.status.set('failed'),
      });
  }

  // ── Processing flow (new optimization) ───────────────────────────────────────
  private _startProcessing(): void {
    this.status.set('processing');
    this._animateSteps();

    // 1. Connect WebSocket and subscribe to cv-optimization topic
    this.wsSvc.connect();
    this.wsSvc.cvOptimization$.pipe(
      takeUntil(this.destroy$),
    ).subscribe(event => {
      if (event.optimizationId !== this.id) return;   // not our job
      if (event.status === 'completed') {
        if (event.provider) this.activeProvider.set(event.provider);
        this.cancel$.next();   // stop polling
        this._loadResult();
      } else if (event.status === 'rejected') {
        this.cancel$.next();
        this._loadRejection();
      } else if (event.status === 'failed') {
        this.cancel$.next();
        this.status.set('failed');
      }
    });

    // 2. Polling fallback — fires every 3s; WebSocket cancel$ stops it when WS arrives
    this.cvSvc.pollUntilDone(this.id, this.cancel$)
      .pipe(takeUntil(this.destroy$))
      .subscribe(r => {
        if (r.status === 'completed') this._loadResult();
        else if (r.status === 'rejected') this._loadRejection();
        else if (r.status === 'failed') this.status.set('failed');
      });
  }

  /** Load the rejection verdict so the UI can explain why optimization was refused. */
  private _loadRejection(): void {
    this.cvSvc.getOptimizationResult(this.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: r => {
          this.result.set(r);
          this.status.set('rejected');
        },
        error: () => this.status.set('failed'),
      });
  }

  private _loadResult(): void {
    this.status.set('completed');
    this._markAllStepsDone();
    this.cvSvc.getOptimizationResult(this.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(r => this.result.set(r));
  }

  // ── Step animation (simulates server progress in the UI) ─────────────────────
  private _animateSteps(): void {
    const delays = [600, 1800, 4000, 7500];
    delays.forEach((delay, i) => {
      setTimeout(() => {
        this.steps.update(s => s.map((step, j) => ({
          ...step,
          status: j < i  ? 'done'
               : j === i ? 'active'
               : 'pending',
        })));
      }, delay);
    });
  }

  private _markAllStepsDone(): void {
    this.steps.update(s => s.map(step => ({ ...step, status: 'done' as const })));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────
  dashOffset(score: number): number { return this.circumference * (1 - score / 100); }

  scoreColor(score: number): string {
    if (score >= 80) return '#10B981';
    if (score >= 60) return '#F59E0B';
    return '#EF4444';
  }

  // ── Keyword boost ─────────────────────────────────────────────────────────────
  toggleKeyword(kw: string): void {
    this.selectedMissing.update(set => {
      const next = new Set(set);
      if (next.has(kw)) next.delete(kw); else next.add(kw);
      return next;
    });
  }

  isSelected(kw: string): boolean { return this.selectedMissing().has(kw); }

  applyKeywords(): void {
    const keywords = [...this.selectedMissing()];
    if (!keywords.length || this.boosting()) return;
    this.boosting.set(true);
    this.cvSvc.boostKeywords(this.id, keywords).pipe(
      takeUntil(this.destroy$),
    ).subscribe({
      next: r => {
        this.result.set(r);
        this.selectedMissing.set(new Set());
        this.boosting.set(false);
        this.snack.open(
          `${keywords.length} mot(s)-clé(s) ajouté(s) au CV — nouveau score : ${r.optimizedScore}%`,
          'OK', { duration: 4500 }
        );
      },
      error: (err: HttpErrorResponse) => {
        this.boosting.set(false);
        const msg = err.status === 401 || err.status === 403
          ? 'Session expirée — reconnectez-vous puis réessayez.'
          : err.status === 404
            ? 'Optimisation introuvable (404).'
            : `Erreur ${err.status || 'réseau'} — réessayez dans quelques secondes.`;
        this.snack.open(msg, 'OK', { duration: 5000 });
      },
    });
  }

  // ── Download ─────────────────────────────────────────────────────────────────
  /**
   * Requests the PDF from Spring Boot with the JWT Bearer token
   * (plain <a href> can't send auth headers), then triggers browser download.
   */
  download(): void {
    this.downloading.set(true);
    this.cvSvc.downloadOptimizedCv(this.id).pipe(
      takeUntil(this.destroy$),
    ).subscribe({
      next: (blob: Blob) => {
        const url  = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href      = url;
        link.download  = `CV_HireSync_Optimise_${this.id.slice(0, 8)}.pdf`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: () => {
        this.downloading.set(false);
        this.snack.open('❌ Erreur lors du téléchargement', 'OK', { duration: 3500 });
      },
    });
  }

  changeLabel(type: string): string {
    const map: Record<string, string> = {
      keyword_added:    'Mot-clé ajouté',
      section_rewritten:'Section réécrite',
      format_improved:  'Format amélioré',
      skill_added:      'Compétence ajoutée',
    };
    return map[type] ?? map[this.canonicalType(type)] ?? 'Amélioration';
  }
}
