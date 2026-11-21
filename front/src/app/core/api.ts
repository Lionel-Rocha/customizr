import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Auth } from './auth';
import { Campaign, ChatMessage, Initiative, Scene } from './models';

/** Chamadas ao servidor usadas pelo site (mesmas rotas que a tela /mesa usa). */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);
  private readonly images = new Map<string, string>();

  campaigns() { return firstValueFrom(this.http.get<Campaign[]>('/campaign/')); }
  createCampaign(name: string) { return firstValueFrom(this.http.post<string>('/campaign/create', { name })); }
  scenes(campaignId: string) { return firstValueFrom(this.http.get<Scene[]>('/live/campaign/' + campaignId + '/scenes')); }
  activateScene(sceneId: string) { return firstValueFrom(this.http.post('/live/scenes/' + sceneId + '/activate', {})); }
  initiative(campaignId: string) { return firstValueFrom(this.http.get<Initiative>('/live/campaign/' + campaignId + '/initiative')); }
  initiativeRoll(campaignId: string) { return firstValueFrom(this.http.post('/live/campaign/' + campaignId + '/initiative/roll', {})); }
  initiativeNext(campaignId: string) { return firstValueFrom(this.http.post('/live/campaign/' + campaignId + '/initiative/next', {})); }
  initiativeEnd(campaignId: string) { return firstValueFrom(this.http.delete('/live/campaign/' + campaignId + '/initiative')); }
  chat(campaignId: string) { return firstValueFrom(this.http.get<ChatMessage[]>('/chat/campaign/' + campaignId + '?limit=100')); }
  say(campaignId: string, text: string) { return firstValueFrom(this.http.post('/chat/campaign/' + campaignId, { text })); }

  /** Imagem do servidor (/image/...) precisa do token: baixa e devolve um endereco local (blob:). */
  async imageUrl(url: string | undefined): Promise<string | null> {
    if (!url) return null;
    if (!url.startsWith('/image/')) return url;
    const cached = this.images.get(url);
    if (cached) return cached;
    const res = await fetch(url, { headers: { Authorization: 'Bearer ' + this.auth.token() } });
    if (!res.ok) return null;
    const local = URL.createObjectURL(await res.blob());
    this.images.set(url, local);
    return local;
  }
}
