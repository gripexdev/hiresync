import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  exact?: boolean;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatTooltipModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  @Input()  collapsed = false;
  @Input()  mobileOpen = false;
  @Output() toggleCollapse = new EventEmitter<void>();

  auth    = inject(AuthService);
  notifs  = inject(NotificationService);
  router  = inject(Router);

  readonly navItems: NavItem[] = [
    { label: 'Tableau de bord', icon: 'dashboard',    route: '/dashboard', exact: true },
    { label: 'Offres d\'emploi', icon: 'work',         route: '/jobs' },
    { label: 'Mon CV',           icon: 'description',  route: '/cv' },
    { label: 'Candidatures',     icon: 'track_changes', route: '/applications' },
    { label: 'Notifications',    icon: 'notifications', route: '/notifications' },
  ];

  isActive(route: string, exact?: boolean): boolean {
    const path = this.router.url.split('?')[0];
    return exact ? path === route : path.startsWith(route);
  }

  logout(): void { this.auth.logout(); }
}
