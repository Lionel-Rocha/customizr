import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from '../../core/api';
import { Auth, errorText } from '../../core/auth';
import { Campaign } from '../../core/models';
import { Icon } from '../../shared/icon';
import { ThemePicker } from '../../shared/theme-picker';

@Component({
  selector: 'cz-campaigns',
  imports: [FormsModule, Icon, ThemePicker],
  templateUrl: './campaigns.html',
  styleUrl: './campaigns.css',
})
export class CampaignsPage implements OnInit {
  protected readonly auth = inject(Auth);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly list = signal<Campaign[] | null>(null);
  protected readonly error = signal('');
  protected newName = '';

  async ngOnInit(): Promise<void> {
    try {
      if (!this.auth.me()) await this.auth.loadMe();
      this.list.set(await this.api.campaigns());
    } catch (err) {
      this.error.set(errorText(err));
    }
  }

  open(c: Campaign): void { this.router.navigate(['/mesa', c.id]); }

  isMaster(c: Campaign): boolean { return !!c.master && c.master.name === this.auth.me()?.name; }

  async create(): Promise<void> {
    const name = this.newName.trim();
    if (!name) return;
    try {
      await this.api.createCampaign(name);
      this.newName = '';
      this.list.set(await this.api.campaigns());
    } catch (err) {
      this.error.set(errorText(err));
    }
  }
}
