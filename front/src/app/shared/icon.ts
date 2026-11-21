import { Component, computed, input } from '@angular/core';
import { ICONES } from '../design/icones';

/** Icone do Customizr (os mesmos desenhos do programa de PC). Cor = cor do texto em volta. */
@Component({
  selector: 'cz-icon',
  template: `<svg class="icon" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" aria-hidden="true">
    <path fill="currentColor" [attr.d]="path()" /></svg>`,
  styles: ':host { display: inline-flex; }',
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(16);
  protected readonly path = computed(() => ICONES[this.name()] ?? '');
}
