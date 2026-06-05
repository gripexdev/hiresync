import {
  Component, inject, OnInit, AfterViewInit, OnDestroy,
  signal, computed, Input, ViewChild, ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CvService } from '../../../core/services/cv.service';
import {
  StructuredCv, StudioOptions, TemplateId,
  FONT_CHOICES, ACCENT_PRESETS,
} from '../../../core/models/studio.model';
import { renderTemplate } from './templates/cv-templates';

@Component({
  selector: 'app-cv-studio',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatIconModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule,
  ],
  templateUrl: './cv-studio.component.html',
  styleUrls: ['./cv-studio.component.scss'],
})
export class CvStudioComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() id!: string;   // optimizationId from route

  @ViewChild('previewArea') previewArea?: ElementRef<HTMLElement>;

  private cvSvc     = inject(CvService);
  private snack     = inject(MatSnackBar);
  private sanitizer = inject(DomSanitizer);
  private resizeObs?: ResizeObserver;

  // A4 width in px at 96dpi (210mm)
  private readonly A4_PX = 794;

  // ── State ────────────────────────────────────────────────────────────────────
  cv          = signal<StructuredCv | null>(null);
  loading     = signal(true);
  downloading = signal(false);
  previewScale = signal(1);

  options = signal<StudioOptions>({
    template:    'modern',
    accentColor: '#2E86AB',
    fontFamily:  "'Inter', sans-serif",
    showPhoto:   true,
  });

  // ── Config ───────────────────────────────────────────────────────────────────
  readonly fonts   = FONT_CHOICES;
  readonly accents = ACCENT_PRESETS;
  readonly templates: { id: TemplateId; label: string; icon: string }[] = [
    { id: 'modern',  label: 'Moderne',  icon: 'view_sidebar' },
    { id: 'classic', label: 'Classique', icon: 'article' },
  ];

  // ── Derived: the full HTML doc (preview === PDF) ─────────────────────────────
  readonly html = computed(() => {
    const cv = this.cv();
    return cv ? renderTemplate(cv, this.options()) : '';
  });

  // srcdoc for the iframe (bypass sanitizer — it's our own generated template)
  readonly safeSrcdoc = computed<SafeHtml>(() =>
    this.sanitizer.bypassSecurityTrustHtml(this.html())
  );

  // ── Lifecycle ────────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.cvSvc.getStructuredCv(this.id).subscribe({
      next: cv => {
        // Ensure arrays exist so templates don't crash
        cv.experience ??= [];
        cv.education  ??= [];
        cv.skills     ??= [];
        cv.languages  ??= [];
        cv.contact    ??= {};
        this.cv.set(cv);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('❌ Impossible de charger le CV', 'OK', { duration: 3500 });
      },
    });
  }

  ngAfterViewInit(): void {
    this.resizeObs = new ResizeObserver(() => this._fitPreview());
    // Observe once the preview area exists (it's behind @if loading)
    queueMicrotask(() => this._observePreview());
  }

  ngOnDestroy(): void {
    this.resizeObs?.disconnect();
  }

  private _observePreview(): void {
    if (this.previewArea) {
      this.resizeObs?.observe(this.previewArea.nativeElement);
      this._fitPreview();
    } else {
      // preview not yet in DOM (still loading) — retry next frame
      requestAnimationFrame(() => this._observePreview());
    }
  }

  /** Scale the A4 page to fit the available preview width (max 1×). */
  private _fitPreview(): void {
    const el = this.previewArea?.nativeElement;
    if (!el) return;
    const avail = el.clientWidth - 56;     // minus padding
    const scale = Math.min(1, avail / this.A4_PX);
    this.previewScale.set(Math.max(0.25, scale));
  }

  // ── Customization handlers ────────────────────────────────────────────────────
  setTemplate(t: TemplateId): void   { this.options.update(o => ({ ...o, template: t })); }
  setAccent(c: string): void         { this.options.update(o => ({ ...o, accentColor: c })); }
  setFont(f: string): void           { this.options.update(o => ({ ...o, fontFamily: f })); }
  togglePhoto(): void                { this.options.update(o => ({ ...o, showPhoto: !o.showPhoto })); }

  onAccentInput(e: Event): void {
    this.setAccent((e.target as HTMLInputElement).value);
  }
  onFontChange(e: Event): void {
    this.setFont((e.target as HTMLSelectElement).value);
  }

  // ── Photo upload (→ base64 embedded in template) ─────────────────────────────
  onPhotoSelected(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.snack.open('❌ Choisissez une image', 'OK', { duration: 3000 });
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      this.snack.open('❌ Image trop lourde (max 3 MB)', 'OK', { duration: 3000 });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.cv.update(c => c ? { ...c, photo: reader.result as string } : c);
      this.options.update(o => ({ ...o, showPhoto: true }));
    };
    reader.readAsDataURL(file);
    (e.target as HTMLInputElement).value = '';
  }

  removePhoto(): void {
    this.cv.update(c => c ? { ...c, photo: undefined } : c);
  }

  // ── Download (HTML → backend Playwright → vector PDF) ────────────────────────
  download(): void {
    const cv = this.cv();
    if (!cv) return;
    this.downloading.set(true);

    const fileName = `CV_${cv.fullName.replace(/\s+/g, '_')}_HireSync.pdf`;
    this.cvSvc.renderPdf(this.html(), fileName).subscribe({
      next: (blob: Blob) => {
        const url  = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        this.downloading.set(false);
        this.snack.open('✅ CV téléchargé en PDF', 'OK', { duration: 3000 });
      },
      error: () => {
        this.downloading.set(false);
        this.snack.open('❌ Erreur de génération du PDF', 'OK', { duration: 3500 });
      },
    });
  }
}
