import { Ability } from './models';

/** Nomes em portugues, iguais aos do programa de PC. */
export const ABILITIES: { key: Ability; attr: 'strength' | 'dexterity' | 'constitution' | 'intelligence' | 'wisdom' | 'charisma';
  name: string; short: string }[] = [
  { key: 'STRENGTH', attr: 'strength', name: 'Força', short: 'FOR' },
  { key: 'DEXTERITY', attr: 'dexterity', name: 'Destreza', short: 'DES' },
  { key: 'CONSTITUTION', attr: 'constitution', name: 'Constituição', short: 'CON' },
  { key: 'INTELLIGENCE', attr: 'intelligence', name: 'Inteligência', short: 'INT' },
  { key: 'WISDOM', attr: 'wisdom', name: 'Sabedoria', short: 'SAB' },
  { key: 'CHARISMA', attr: 'charisma', name: 'Carisma', short: 'CAR' },
];

export const SKILLS_PT: Record<string, string> = {
  ATHLETICS: 'Atletismo', ACROBATICS: 'Acrobacia', SLEIGHT_OF_HAND: 'Prestidigitação', STEALTH: 'Furtividade',
  ARCANA: 'Arcanismo', HISTORY: 'História', INVESTIGATION: 'Investigação', NATURE: 'Natureza', RELIGION: 'Religião',
  ANIMAL_HANDLING: 'Lidar com Animais', INSIGHT: 'Intuição', MEDICINE: 'Medicina', PERCEPTION: 'Percepção',
  SURVIVAL: 'Sobrevivência', DECEPTION: 'Enganação', INTIMIDATION: 'Intimidação', PERFORMANCE: 'Atuação', PERSUASION: 'Persuasão',
};

export const DAMAGE_PT: Record<string, string> = {
  acid: 'ácido', bludgeoning: 'contundente', cold: 'frio', fire: 'fogo', force: 'energia', lightning: 'elétrico',
  necrotic: 'necrótico', piercing: 'perfurante', poison: 'veneno', psychic: 'psíquico', radiant: 'radiante',
  slashing: 'cortante', thunder: 'trovejante',
};

export const RECHARGE_PT: Record<string, string> = { SHORT_REST: 'descanso curto', LONG_REST: 'descanso longo', DAWN: 'amanhecer' };

export function modifier(score: number): number { return Math.floor((score - 10) / 2); }
export function signed(n: number): string { return n >= 0 ? '+' + n : String(n); }
