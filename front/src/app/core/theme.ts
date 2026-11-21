import { Injectable, signal } from '@angular/core';
import { TEMA_PADRAO, TEMAS } from '../design/temas';

/** Tema do site: o mesmo do programa de PC (cores em design/web/tokens.css), trocado pelo data-theme do <html>. */
@Injectable({ providedIn: 'root' })
export class Theme {
  readonly temas = TEMAS;
  readonly atual = signal(TEMA_PADRAO);

  constructor() {
    let salvo: string | null = null;
    try { salvo = localStorage.getItem('tema'); } catch { /* sem armazenamento */ }
    this.usar(salvo && TEMAS.some(t => t.chave === salvo) ? salvo : TEMA_PADRAO);
  }

  usar(chave: string): void {
    this.atual.set(chave);
    document.documentElement.dataset['theme'] = chave;
    try { localStorage.setItem('tema', chave); } catch { /* sem armazenamento */ }
  }
}
