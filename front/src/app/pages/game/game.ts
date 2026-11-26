import { Component, computed, ElementRef, inject, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Api } from '../../core/api';
import { Auth, errorText } from '../../core/auth';
import { Live, LiveEvent } from '../../core/live';
import { Campaign, CharacterSummary, ChatMessage, Initiative, Scene } from '../../core/models';
import { Icon } from '../../shared/icon';
import { ThemePicker } from '../../shared/theme-picker';
import { SheetWindow } from './sheet/sheet';

interface Section { key: string; label: string; icon: string; }

/**
 * Tela do jogo: a mesma organizacao do programa de PC (barra de navegacao | painel | mapa | chat), com as mesmas
 * classes do kit (design/web/customizr.css).
 */
@Component({
  selector: 'cz-game',
  imports: [FormsModule, Icon, ThemePicker, SheetWindow],
  templateUrl: './game.html',
  styleUrl: './game.css',
})
export class GamePage implements OnInit, OnDestroy {
  protected readonly auth = inject(Auth);
  protected readonly live = inject(Live);
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sections: Section[] = [
    { key: 'mapa', label: 'Mapa', icon: 'map' }, { key: 'fichas', label: 'Fichas', icon: 'sheet' },
    { key: 'grupo', label: 'Grupo', icon: 'party' }, { key: 'combate', label: 'Combate', icon: 'combat' },
    { key: 'diario', label: 'Diário', icon: 'journal' }, { key: 'som', label: 'Som', icon: 'sound' },
  ];
  protected readonly open = signal<string | null>('mapa');
  protected readonly openLabel = computed(() => this.sections.find(s => s.key === this.open())?.label ?? '');

  protected readonly campaign = signal<Campaign | null>(null);
  protected readonly master = computed(() => {
    const c = this.campaign();
    return !!c?.master && c.master.name === this.auth.me()?.name;
  });
  protected readonly scenes = signal<Scene[]>([]);
  protected readonly scene = computed(() => this.scenes().find(s => s.active) ?? null);
  protected readonly mapImage = signal<string | null>(null);
  protected readonly initiative = signal<Initiative>({ round: 1, turnIndex: 0, entries: [] });
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly toast = signal('');
  protected readonly characters = signal<CharacterSummary[]>([]);
  protected readonly openSheet = signal<string | null>(null);
  protected newCharacter = '';
  protected text = '';
  protected readonly dice = ['d4', 'd6', 'd8', 'd10', 'd12', 'd20', 'd100'];
  private readonly chatList = viewChild<ElementRef<HTMLElement>>('chatList');

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id')!;
    try {
      if (!this.auth.me()) await this.auth.loadMe();
      const campaign = (await this.api.campaigns()).find(c => c.id === id);
      if (!campaign) { this.router.navigateByUrl('/mesas'); return; }
      this.campaign.set(campaign);
      const [scenes, initiative, messages] = await Promise.all([this.api.scenes(id), this.api.initiative(id), this.api.chat(id)]);
      this.scenes.set(scenes);
      this.initiative.set(initiative);
      this.messages.set(messages);
      this.loadMap();
      this.scrollChat();
      this.loadCharacters();
      this.live.connect(id, e => this.onEvent(e));
    } catch (err) {
      this.say(errorText(err));
    }
  }

  ngOnDestroy(): void { this.live.disconnect(); }

  // ---------------- navegacao (igual ao PC: clicar de novo fecha o painel) ----------------
  toggle(key: string): void { this.open.set(this.open() === key ? null : key); }

  // ---------------- eventos ao vivo ----------------
  private onEvent(e: LiveEvent): void {
    const d = e.data;
    switch (e.type) {
      case 'chat': this.messages.update(list => [...list, d]); this.scrollChat(); break;
      case 'initiative': this.initiative.set(d); break;
      case 'initiative_ended': this.initiative.set({ round: 1, turnIndex: 0, entries: [] }); break;
      case 'scene': {
        this.scenes.update(list => {
          const next = list.filter(s => s.id !== d.id).map(s => d.active ? { ...s, active: false } : s);
          return [...next, d];
        });
        this.loadMap();
        break;
      }
      case 'scene_deleted': this.scenes.update(list => list.filter(s => s.id !== d.sceneId)); this.loadMap(); break;
      case 'token_added': case 'token_updated':
        this.patchScene(d.sceneId, s => ({ ...s, tokens: [...s.tokens.filter(t => t.id !== d.id), d] })); break;
      case 'token_moved':
        this.patchScene(d.sceneId, s => ({ ...s, tokens: s.tokens.map(t => t.id === d.tokenId ? { ...t, x: d.x, y: d.y } : t) })); break;
      case 'token_removed':
        this.patchScene(d.sceneId, s => ({ ...s, tokens: s.tokens.filter(t => t.id !== d.tokenId) })); break;
    }
  }

  private patchScene(sceneId: string, change: (s: Scene) => Scene): void {
    this.scenes.update(list => list.map(s => s.id === sceneId ? change(s) : s));
  }

  private async loadMap(): Promise<void> {
    this.mapImage.set(await this.api.imageUrl(this.scene()?.mapUrl));
  }

  // ---------------- cenas (mestre) ----------------
  async activate(s: Scene): Promise<void> {
    try { await this.api.activateScene(s.id); } catch (err) { this.say(errorText(err)); }
  }

  // ---------------- fichas ----------------
  protected mine(c: CharacterSummary): boolean { return c.ownerId === this.auth.me()?.userId; }
  async loadCharacters(): Promise<void> {
    try { this.characters.set(await this.api.characters(this.campaign()!.id)); } catch (err) { this.say(errorText(err)); }
  }
  async createCharacter(): Promise<void> {
    const name = this.newCharacter.trim();
    if (!name) return;
    await this.run(async () => {
      await this.api.createCharacter(this.campaign()!.id, name);
      this.newCharacter = '';
      await this.loadCharacters();
    });
  }
  /** Ficha alheia so o mestre abre (o servidor confere de novo). */
  canOpen(c: CharacterSummary): boolean { return this.master() || this.mine(c); }

  // ---------------- combate ----------------
  protected current(i: number): boolean { return this.initiative().entries.length > 0 && i === this.initiative().turnIndex; }
  protected inviteName = '';

  async invitePlayer(): Promise<void> {
    const name = this.inviteName.trim();
    if (!name) return;
    try {
      this.say(String(await this.api.invite(this.campaign()!.id, name)));
      this.inviteName = '';
    } catch (err) {
      this.say(errorText(err));
    }
  }

  async roll(): Promise<void> { await this.run(() => this.api.initiativeRoll(this.campaign()!.id)); }
  async next(): Promise<void> { await this.run(() => this.api.initiativeNext(this.campaign()!.id)); }
  async end(): Promise<void> { await this.run(() => this.api.initiativeEnd(this.campaign()!.id)); }

  // ---------------- chat ----------------
  async send(): Promise<void> {
    const text = this.text.trim();
    if (!text) return;
    this.text = '';
    await this.run(() => this.api.say(this.campaign()!.id, text));
  }
  async rollDie(d: string): Promise<void> { await this.run(() => this.api.say(this.campaign()!.id, '/r 1' + d)); }

  protected who(m: ChatMessage): string {
    const name = m.speakerName || m.authorName;
    return m.type === 'WHISPER' ? name + ' • sussurro para ' + m.recipientName : name;
  }
  protected crit(m: ChatMessage): string { return m.naturalD20 === 20 ? 'crit' : m.naturalD20 === 1 ? 'fumble' : ''; }

  private scrollChat(): void {
    setTimeout(() => { const el = this.chatList()?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    try { await action(); } catch (err) { this.say(errorText(err)); }
  }

  private say(text: string): void {
    this.toast.set(text);
    setTimeout(() => { if (this.toast() === text) this.toast.set(''); }, 5000);
  }
}
