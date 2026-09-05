import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';
import { PlaceholderPageComponent } from './features/placeholder/placeholder-page.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app/dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'app',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      { path: 'transactions', component: PlaceholderPageComponent, data: { title: 'Transactions' } },
      { path: 'budgets', component: PlaceholderPageComponent, data: { title: 'Budgets' } },
      { path: 'analytics', component: PlaceholderPageComponent, data: { title: 'Analytics' } },
      { path: 'insights', component: PlaceholderPageComponent, data: { title: 'AI Insights' } },
      { path: 'assistant', component: PlaceholderPageComponent, data: { title: 'AI Assistant' } },
      { path: 'settings', component: PlaceholderPageComponent, data: { title: 'Settings' } },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'app/dashboard' },
];
