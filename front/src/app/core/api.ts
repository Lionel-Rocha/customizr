import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Auth } from './auth';
import { Campaign, CharacterSheet, CharacterSummary, ChatMessage, Initiative, Invite, RollMode, Scene } from './models';

/** Chamadas ao servidor usadas pelo site (mesmas rotas que a tela /mesa usa). */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);
  private readonly images = new Map<string, string>();

  campaigns() { return firstValueFrom(this.http.get<Campaign[]>('/campaign/')); }
  createCampaign(name: string) { return firstValueFrom(this.http.post<string>('/campaign/create', { name })); }
  /** Convites que eu ainda nao aceitei. */
  invites() { return firstValueFrom(this.http.get<Invite[]>('/campaign/invites')); }
  acceptInvite(campaignId: string) {
    return firstValueFrom(this.http.post('/campaign/accept-invite', { campaignId }, { responseType: 'text' }));
  }
  declineInvite(campaignId: string) { return firstValueFrom(this.http.post('/campaign/decline-invite', { campaignId })); }
  /** Mestre convida pelo nome de usuario (devolve o texto do servidor: enviado, ja esta na mesa...). */
  invite(campaignId: string, username: string) {
    return firstValueFrom(this.http.post('/campaign/invite', { campaignId, username, role: 'PLAYER' }, { responseType: 'text' }));
  }
  scenes(campaignId: string) { return firstValueFrom(this.http.get<Scene[]>('/live/campaign/' + campaignId + '/scenes')); }
  activateScene(sceneId: string) { return firstValueFrom(this.http.post('/live/scenes/' + sceneId + '/activate', {})); }
  initiative(campaignId: string) { return firstValueFrom(this.http.get<Initiative>('/live/campaign/' + campaignId + '/initiative')); }
  initiativeRoll(campaignId: string) { return firstValueFrom(this.http.post('/live/campaign/' + campaignId + '/initiative/roll', {})); }
  initiativeNext(campaignId: string) { return firstValueFrom(this.http.post('/live/campaign/' + campaignId + '/initiative/next', {})); }
  initiativeEnd(campaignId: string) { return firstValueFrom(this.http.delete('/live/campaign/' + campaignId + '/initiative')); }
  chat(campaignId: string) { return firstValueFrom(this.http.get<ChatMessage[]>('/chat/campaign/' + campaignId + '?limit=100')); }
  say(campaignId: string, text: string) { return firstValueFrom(this.http.post('/chat/campaign/' + campaignId, { text })); }

  // ---------------- fichas ----------------
  characters(campaignId: string) { return firstValueFrom(this.http.get<CharacterSummary[]>('/character/' + campaignId + '/characters')); }
  createCharacter(campaignId: string, name: string) {
    return firstValueFrom(this.http.post('/character/create', { campaignId, name }, { responseType: 'text' }));
  }
  sheet(id: string) { return firstValueFrom(this.http.get<CharacterSheet>('/character/' + id + '/sheet')); }
  /** Teste da ficha (atributo, salvaguarda, pericia, iniciativa, contra a morte); o resultado vai pro chat. */
  sheetRoll(id: string, body: { kind: string; ability?: string; skill?: string; mode: RollMode }) {
    return firstValueFrom(this.http.post('/character/' + id + '/roll', { ...body, toChat: true }));
  }
  attack(id: string, attackId: string, mode: RollMode) {
    return firstValueFrom(this.http.post('/character/' + id + '/attacks/' + attackId + '/roll', { mode, toChat: true }));
  }
  hitPoints(id: string, type: 'DAMAGE' | 'HEALING' | 'TEMPORARY', amount: number) {
    return firstValueFrom(this.http.post<CharacterSheet>('/character/' + id + '/hp', { type, amount }));
  }
  combat(id: string, body: { hpMax?: number; armorClass?: number; speedFt?: number }) {
    return firstValueFrom(this.http.put<CharacterSheet>('/character/' + id + '/combat', body));
  }
  attributes(id: string, a: NonNullable<CharacterSheet['attributes']>) {
    return firstValueFrom(this.http.put<CharacterSheet>('/character/' + id + '/attributes', a));
  }
  inspiration(id: string, value: boolean) {
    return firstValueFrom(this.http.put<CharacterSheet>('/character/' + id + '/inspiration', { heroicInspiration: value }));
  }

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
