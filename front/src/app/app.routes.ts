import { Routes } from '@angular/router';
import { authGuard } from './core/auth';

export const routes: Routes = [
  { path: 'entrar', loadComponent: () => import('./pages/login/login').then(m => m.LoginPage) },
  { path: 'mesas', canActivate: [authGuard], loadComponent: () => import('./pages/campaigns/campaigns').then(m => m.CampaignsPage) },
  { path: 'mesa/:id', canActivate: [authGuard], loadComponent: () => import('./pages/game/game').then(m => m.GamePage) },
  { path: '', pathMatch: 'full', redirectTo: 'mesas' },
  { path: '**', redirectTo: 'mesas' },
];
