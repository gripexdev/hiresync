import { Component, inject, OnInit, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CvService } from '../../../core/services/cv.service';
import { CVOptimizationResult, SuggestedChange } from '../../../core/models/cv.model';

@Component({
  selector: 'app-cv-optimizer',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './cv-optimizer.component.html',
  styleUrls: ['./cv-optimizer.component.scss'],
})
export class CvOptimizerComponent implements OnInit {
  @Input() id!: string;

  private svc = inject(CvService);

  result   = signal<CVOptimizationResult | null>(null);
  loading  = signal(true);
  simulating = signal(false);

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

  circumference = 2 * Math.PI * 52;
  dashOffset(score: number): number { return this.circumference * (1 - score / 100); }

  scoreColor(score: number): string {
    if (score >= 80) return '#10B981';
    if (score >= 60) return '#F59E0B';
    return '#EF4444';
  }

  ngOnInit(): void {
    // Simulate "AI processing" for 2s then show result
    this.simulating.set(true);
    setTimeout(() => {
      this.svc.getOptimizationResult(this.id).subscribe(r => {
        this.result.set(r);
        this.loading.set(false);
        this.simulating.set(false);
      });
    }, 2000);
  }

  changeLabel(type: string): string {
    const map: Record<string, string> = {
      keyword_added:    'Mot-clé ajouté',
      section_rewritten:'Section réécrite',
      format_improved:  'Format amélioré',
      skill_added:      'Compétence ajoutée',
    };
    return map[type] ?? type;
  }
}
