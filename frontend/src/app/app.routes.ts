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
    path: 'onboarding',
    loadComponent: () =>
      import('./features/onboarding/onboarding-page.component').then((m) => m.OnboardingPageComponent),
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
      {
        path: 'transactions',
        loadComponent: () =>
          import('./features/transactions/transactions.component').then((m) => m.TransactionsComponent),
      },
      { path: 'budgets', component: PlaceholderPageComponent, data: { title: 'Budgets' } },
      { path: 'goals', component: PlaceholderPageComponent, data: { title: 'Goals' } },
      {
        path: 'insights',
        component: PlaceholderPageComponent,
        data: {
          title: 'Insights',
          description: 'Useful spending patterns will appear here as your transaction history grows.',
        },
      },
      {
        path: 'settings',
        component: PlaceholderPageComponent,
        data: { title: 'Settings', description: 'Manage your personal SpendSense workspace.' },
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'app/dashboard' },
];
