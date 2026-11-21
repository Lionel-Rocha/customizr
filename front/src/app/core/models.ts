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

export type ChatType = 'MESSAGE' | 'EMOTE' | 'ROLL' | 'WHISPER' | 'CARD' | 'SYSTEM' | string;
export interface ChatMessage {
  id: string; type: ChatType; authorName: string; speakerName?: string; content: string; recipientName?: string;
  rollTotal?: number; naturalD20?: number; createdAt?: string;
}
