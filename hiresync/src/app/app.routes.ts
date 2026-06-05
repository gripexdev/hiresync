import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // Public routes
  {
    path: '',
    loadComponent: () => import('./features/landing/landing.component').then(m => m.LandingComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },

  // Protected routes — all inside AppShell (sidebar layout)
  {
    path: '',
    loadComponent: () => import('./layout/app-shell.component').then(m => m.AppShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'jobs',
        loadComponent: () => import('./features/jobs/job-search/job-search.component').then(m => m.JobSearchComponent),
      },
      {
        path: 'jobs/:id',
        loadComponent: () => import('./features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent),
      },
      {
        path: 'cv',
        loadComponent: () => import('./features/cv/cv-manager/cv-manager.component').then(m => m.CvManagerComponent),
      },
      {
        path: 'cv/optimize/:id',
        loadComponent: () => import('./features/cv/cv-optimizer/cv-optimizer.component').then(m => m.CvOptimizerComponent),
      },
      {
        path: 'cv/studio/:id',
        loadComponent: () => import('./features/cv/cv-studio/cv-studio.component').then(m => m.CvStudioComponent),
      },
      {
        path: 'applications',
        loadComponent: () => import('./features/applications/applications.component').then(m => m.ApplicationsComponent),
      },
      {
        path: 'notifications',
        loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent),
      },
    ],
  },

  { path: '**', redirectTo: '' },
];
