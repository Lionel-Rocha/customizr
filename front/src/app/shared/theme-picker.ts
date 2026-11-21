import { Component, inject } from '@angular/core';
import { Theme } from '../core/theme';

/** Caixinha de escolher o tema (troca na hora; fica salvo neste navegador). */
@Component({
  selector: 'cz-theme-picker',
  template: `<select aria-label="Tema" [value]="theme.atual()" (change)="theme.usar($any($event.target).value)">
    @for (t of theme.temas; track t.chave) { <option [value]="t.chave" [selected]="t.chave === theme.atual()">{{ t.nome }}</option> }
  </select>`,
})
export class ThemePicker {
  protected readonly theme = inject(Theme);
}
