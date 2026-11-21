// Formatos que o servidor (Spring Boot) devolve. Mesmos nomes dos records Java em top.customizr.system.dto.

export interface Me { userId: string; name: string; }

export interface Campaign { id: string; name: string; code: string; master?: { userId: string; name: string }; createdAt?: string; }

export interface Token {
  id: string; sceneId: string; name: string; x: number; y: number; imageUrl?: string; hidden: boolean;
  widthCells: number; heightCells: number; hpCurrent?: number; hpMax?: number; conditions?: string[];
}

export interface Scene {
  id: string; name: string; mapUrl?: string; gridType: 'SQUARE' | 'HEX' | string; gridSizePx: number; feetPerCell: number;
  widthPx?: number; heightPx?: number; active: boolean; tokens: Token[];
}

export interface InitiativeEntry { id: string; name: string; initiative: number; bonus: number; tokenId?: string; }
export interface Initiative { round: number; turnIndex: number; currentEntryId?: string; entries: InitiativeEntry[]; }

// ---------------- Ficha (CharacterSheetResponse do servidor) ----------------
export type Ability = 'STRENGTH' | 'DEXTERITY' | 'CONSTITUTION' | 'INTELLIGENCE' | 'WISDOM' | 'CHARISMA';
export type RollMode = 'NORMAL' | 'ADVANTAGE' | 'DISADVANTAGE';

export interface CharacterSummary { characterId: string; name: string; status: string; campaignId: string; ownerId: string; }

export interface SheetSkill { skill: string; label: string; ability: Ability; bonus: number; proficient: boolean; expertise: boolean; }
export interface SheetSave { ability: Ability; bonus: number; proficient: boolean; }
export interface SheetAttack { id: string; name: string; attackBonus: number; damage: string; damageType?: string; notes?: string; }
export interface SheetResource { id: string; name: string; className?: string; max: number; spent: number; remaining: number; recharge?: string; unit?: string; }
export interface SheetSlot { pool: string; className?: string; level: number; total: number; spent: number; }
export interface SheetFeat { id: string; name: string; characterLevel: number; choices?: string; }

export interface CharacterSheet {
  characterId: string; name: string; status: string; campaignId: string; ownerId: string;
  classes: { characterClassId: string; className: string; level: number; subclass?: { name?: string } | null }[];
  attributes: Record<'strength' | 'dexterity' | 'constitution' | 'intelligence' | 'wisdom' | 'charisma', number> | null;
  combat: {
    hpMax?: number; hpCurrent?: number; hpTemp?: number; armorClass?: number; speedFt?: number; totalLevel: number;
    proficiencyBonus: number; initiative: number; passivePerception: number; suggestedHpMax?: number;
  };
  details: {
    portraitUrl?: string; species?: { name: string } | null; background?: { name: string } | null;
    experiencePoints: number; levelByExperience: number; experienceToNextLevel?: number;
    skills: SheetSkill[]; savingThrows: SheetSave[]; conditions: string[]; exhaustion: number;
    deathSaveSuccesses: number; deathSaveFailures: number; currentSpeedFt?: number;
    spellSlots: SheetSlot[]; hitDiceRemaining: number; hitDiceTotal: number; attacks: SheetAttack[];
    feats: SheetFeat[]; resources: SheetResource[]; heroicInspiration: boolean;
    carryingCapacityLb?: number; languages: string[]; toolProficiencies: string[]; weaponProficiencies: string[];
    armorProficiencies: string[]; passiveInsight: number; passiveInvestigation: number;
  };
}

export type ChatType = 'MESSAGE' | 'EMOTE' | 'ROLL' | 'WHISPER' | 'CARD' | 'SYSTEM' | string;
export interface ChatMessage {
  id: string; type: ChatType; authorName: string; speakerName?: string; content: string; recipientName?: string;
  rollTotal?: number; naturalD20?: number; createdAt?: string;
}
