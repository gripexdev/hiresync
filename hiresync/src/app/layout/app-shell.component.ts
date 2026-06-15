import { Component, signal, inject } from '@angular/core';
import { Router, RouterOutlet, NavigationStart } from '@angular/router';
import { filter } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { SidebarComponent } from '../shared/components/sidebar/sidebar.component';
import { TopbarComponent } from '../shared/components/topbar/topbar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatIconModule, SidebarComponent, TopbarComponent],
  template: `
    <div class="app-layout">
      <app-sidebar
        [collapsed]="sidebarCollapsed() && !mobileNavOpen()"
        [mobileOpen]="mobileNavOpen()"
        (toggleCollapse)="sidebarCollapsed.set(!sidebarCollapsed())" />

      @if (mobileNavOpen()) {
        <div class="mobile-backdrop" (click)="mobileNavOpen.set(false)"></div>
      }

      <div class="main-content">
        <button class="mobile-menu-btn" (click)="mobileNavOpen.set(true)" aria-label="Ouvrir le menu">
          <mat-icon>menu</mat-icon>
        </button>
        <div class="page-body">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .mobile-backdrop, .mobile-menu-btn { display: none; }

    @media (max-width: 900px) {
      .mobile-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, .4);
        z-index: 1000;
      }

      .mobile-menu-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        position: fixed;
        top: 12px;
        left: 12px;
        z-index: 999;
        width: 40px;
        height: 40px;
        border: 1px solid #E2E8F0;
        border-radius: 8px;
        background: #fff;
        color: #475569;
        cursor: pointer;
        box-shadow: 0 2px 8px rgba(0,0,0,.08);
      }
    }
  `],
})
export class AppShellComponent {
  sidebarCollapsed = signal(false);
  mobileNavOpen = signal(false);

  private router = inject(Router);

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationStart))
      .subscribe(() => this.mobileNavOpen.set(false));
  }
}
