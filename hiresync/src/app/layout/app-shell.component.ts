import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../shared/components/sidebar/sidebar.component';
import { TopbarComponent } from '../shared/components/topbar/topbar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  template: `
    <div class="app-layout">
      <app-sidebar [collapsed]="sidebarCollapsed()" (toggleCollapse)="sidebarCollapsed.set(!sidebarCollapsed())" />
      <div class="main-content">
        <div class="page-body">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
})
export class AppShellComponent {
  sidebarCollapsed = signal(false);
}
