import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApplicationStatus } from '../../../core/models/application.model';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="hs-badge" [ngClass]="cssClass">{{ label }}</span>`,
})
export class StatusBadgeComponent {
  @Input() status!: ApplicationStatus;

  get cssClass(): string {
    const map: Record<ApplicationStatus, string> = {
      applied:   'hs-badge--applied',
      in_review: 'hs-badge--pending',
      interview: 'hs-badge--interview',
      offer:     'hs-badge--offer',
      rejected:  'hs-badge--rejected',
    };
    return map[this.status] ?? '';
  }

  get label(): string {
    const map: Record<ApplicationStatus, string> = {
      applied:   'Candidaté',
      in_review: 'En examen',
      interview: 'Entretien',
      offer:     'Offre reçue',
      rejected:  'Refusé',
    };
    return map[this.status] ?? this.status;
  }
}
