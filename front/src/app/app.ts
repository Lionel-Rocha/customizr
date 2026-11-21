import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Theme } from './core/theme';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  // aplica o tema salvo assim que o site abre
  private readonly theme = inject(Theme);
}
