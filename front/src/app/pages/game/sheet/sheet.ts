import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../../core/api';
import { errorText } from '../../../core/auth';
import { CharacterSheet, RollMode } from '../../../core/models';
import { ABILITIES, DAMAGE_PT, modifier, RECHARGE_PT, signed, SKILLS_PT } from '../../../core/rules-pt';

/**
 * Ficha de personagem no site, com a mesma organizacao do programa de PC: cabecalho, aba Principal (atributos com
 * salvaguardas e pericias clicaveis, PV, CA, iniciativa, dados de vida, morte, ataques), Tracos e magias, Personagem.
 * Toda rolagem vai pro chat da mesa (o servidor rola: ninguem forja o resultado).
 */
@Component({
  selector: 'cz-sheet',
  imports: [FormsModule],
  templateUrl: './sheet.html',
  styleUrl: './sheet.css',
})
export class SheetWindow implements OnInit {
  readonly characterId = input.required<string>();
  readonly closed = output<void>();
  private readonly api = inject(Api);

  protected readonly sheet = signal<CharacterSheet | null>(null);
  protected readonly error = signal('');
  protected readonly tab = signal<'principal' | 'tracos' | 'personagem'>('principal');
  protected readonly mode = signal<RollMode>('NORMAL');
  protected readonly abilities = ABILITIES;
  protected readonly signed = signed;
  protected readonly modifier = modifier;
  protected hpAmount: number | null = null;

  protected readonly classLine = computed(() => {
    const s = this.sheet();
    if (!s) return '';
    const cls = s.classes.map(c => c.className + ' ' + c.level + (c.subclass?.name ? ' (' + c.subclass.name + ')' : '')).join(' / ');
    return cls || 'Sem classe';
  });

  async ngOnInit(): Promise<void> { await this.reload(); }

  async reload(): Promise<void> {
    try { this.sheet.set(await this.api.sheet(this.characterId())); } catch (err) { this.error.set(errorText(err)); }
  }

  // ---------------- leitura ----------------
  protected score(attr: string): number { return (this.sheet()?.attributes as any)?.[attr] ?? 10; }
  protected save(ability: string) { return this.sheet()?.details.savingThrows.find(s => s.ability === ability); }
  protected skills(ability: string) { return (this.sheet()?.details.skills ?? []).filter(s => s.ability === ability); }
  protected skillName(key: string, label: string): string { return SKILLS_PT[key] ?? label; }
  protected damageType(t?: string): string { return t ? (DAMAGE_PT[t.toLowerCase()] ?? t) : ''; }
  protected recharge(r?: string): string { return r ? (RECHARGE_PT[r] ?? r.toLowerCase()) : ''; }
  protected mark(proficient: boolean, expertise = false): string { return expertise ? '◉' : proficient ? '●' : '○'; }
  protected dots(n: number, total = 3): boolean[] { return Array.from({ length: total }, (_, i) => i < n); }

  // ---------------- rolagens (vao pro chat) ----------------
  async roll(kind: string, extra: { ability?: string; skill?: string } = {}): Promise<void> {
    await this.run(() => this.api.sheetRoll(this.characterId(), { kind, mode: this.mode(), ...extra }), true);
  }
  async attack(attackId: string): Promise<void> {
    await this.run(() => this.api.attack(this.characterId(), attackId, this.mode()), false);
  }

  // ---------------- PV ----------------
  async hp(type: 'DAMAGE' | 'HEALING' | 'TEMPORARY'): Promise<void> {
    const n = Math.floor(Number(this.hpAmount));
    if (!n || n <= 0) return;
    await this.run(async () => { this.sheet.set(await this.api.hitPoints(this.characterId(), type, n)); this.hpAmount = null; }, false);
  }

  async setCombat(field: 'hpMax' | 'armorClass' | 'speedFt', value: string): Promise<void> {
    const n = Math.floor(Number(value));
    if (!Number.isFinite(n) || n < 0) return;
    await this.run(async () => this.sheet.set(await this.api.combat(this.characterId(), { [field]: n })), false);
  }

  async setScore(attr: string, value: string): Promise<void> {
    const s = this.sheet();
    const n = Math.floor(Number(value));
    if (!s?.attributes || !Number.isFinite(n) || n < 1 || n > 30) return;
    await this.run(async () => this.sheet.set(await this.api.attributes(this.characterId(), { ...s.attributes!, [attr]: n })), false);
  }

  async toggleInspiration(): Promise<void> {
    const s = this.sheet();
    if (!s) return;
    await this.run(async () => this.sheet.set(await this.api.inspiration(this.characterId(), !s.details.heroicInspiration)), false);
  }

  private async run(action: () => Promise<unknown>, reloadAfter: boolean): Promise<void> {
    this.error.set('');
    try {
      await action();
      if (reloadAfter) await this.reload();
    } catch (err) {
      this.error.set(errorText(err));
    }
  }
}
