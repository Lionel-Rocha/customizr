import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, throwError } from 'rxjs';
import { Me } from './models';

const KEY = 'customizr_token';

function load(): string | null {
  try { return sessionStorage.getItem(KEY); } catch { return null; }
}

/** Login com o JWT do servidor (POST /api/auth/login devolve o token em texto). */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly token = signal<string | null>(load());
  readonly me = signal<Me | null>(null);

  async login(username: string, password: string): Promise<void> {
    const token = await firstValueFrom(this.http.post('/api/auth/login', { username, password }, { responseType: 'text' }));
    this.setToken(token);
    await this.loadMe();
  }

  async createAccount(name: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post('/user/create', { name, password }));
    await this.login(name, password);
  }

  async loadMe(): Promise<Me> {
    const me = await firstValueFrom(this.http.get<Me>('/user/me'));
    this.me.set(me);
    return me;
  }

  logout(): void {
    this.setToken(null);
    this.me.set(null);
    this.router.navigateByUrl('/entrar');
  }

  private setToken(token: string | null): void {
    this.token.set(token);
    try { token ? sessionStorage.setItem(KEY, token) : sessionStorage.removeItem(KEY); } catch { /* aba anonima */ }
  }
}

/** Poe o "Authorization: Bearer" em toda chamada pro servidor; 401 manda pro login. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.token();
  const withAuth = token && !req.url.startsWith('/api/auth') ? req.clone({ setHeaders: { Authorization: 'Bearer ' + token } }) : req;
  return next(withAuth).pipe(catchError((err: HttpErrorResponse) => {
    if (err.status === 401 && token) auth.logout();
    return throwError(() => err);
  }));
};

export const authGuard: CanActivateFn = () => inject(Auth).token() ? true : inject(Router).parseUrl('/entrar');

/** Mensagem de erro legivel pra mostrar na tela. */
export function errorText(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Servidor fora do ar.';
    if (err.status === 401) return 'Usuário ou senha inválidos.';
    if (err.status === 403) return 'Sem permissão para isso.';
    const body = err.error;
    if (typeof body === 'string' && body.length < 200) return body;
    return (body && (body.message || body.error)) || 'Erro ' + err.status;
  }
  return err instanceof Error ? err.message : String(err);
}
