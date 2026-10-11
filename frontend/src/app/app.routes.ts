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
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/onboarding/onboarding-wizard.component').then((m) => m.OnboardingWizardComponent),
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
      {
        path: 'statements',
        loadComponent: () =>
          import('./features/statements/statements.component').then((m) => m.StatementsComponent),
      },
      {
        path: 'budgets',
        loadComponent: () =>
          import('./features/budgets/budgets.component').then((m) => m.BudgetsComponent),
      },
      {
        path: 'goals',
        loadComponent: () =>
          import('./features/goals/goals.component').then((m) => m.GoalsComponent),
      },
      {
        path: 'insights',
        loadComponent: () =>
          import('./features/insights/insights.component').then((m) => m.InsightsComponent),
      },
      {
        path: 'assistant',
        loadComponent: () =>
          import('./features/assistant/assistant.component').then((m) => m.AssistantComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },
      {
        path: 'onboarding',
        loadComponent: () =>
          import('./features/onboarding/onboarding-wizard.component').then((m) => m.OnboardingWizardComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'app/dashboard' },
];
