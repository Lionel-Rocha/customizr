import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth, errorText } from '../../core/auth';
import { ThemePicker } from '../../shared/theme-picker';

@Component({
  selector: 'cz-login',
  imports: [FormsModule, ThemePicker],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginPage {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  protected user = '';
  protected pass = '';
  protected readonly creating = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  async submit(): Promise<void> {
    if (!this.user.trim() || !this.pass) return;
    this.busy.set(true);
    this.error.set('');
    try {
      if (this.creating()) await this.auth.createAccount(this.user.trim(), this.pass);
      else await this.auth.login(this.user.trim(), this.pass);
      await this.router.navigateByUrl('/mesas');
    } catch (err) {
      this.error.set(errorText(err));
    } finally {
      this.busy.set(false);
    }
  }
}
