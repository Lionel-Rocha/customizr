import { inject, Injectable, NgZone, signal } from '@angular/core';
import { Auth } from './auth';

export interface LiveEvent { type: string; data: any; }

/**
 * Canal ao vivo da campanha (GET /live/campaign/{id}/events, Server-Sent Events). Lido com fetch porque o EventSource
 * do navegador nao manda o header Authorization. Reconecta sozinho.
 */
@Injectable({ providedIn: 'root' })
export class Live {
  private readonly auth = inject(Auth);
  private readonly zone = inject(NgZone);
  private controller: AbortController | null = null;
  readonly online = signal(false);

  connect(campaignId: string, onEvent: (e: LiveEvent) => void): void {
    this.disconnect();
    const controller = new AbortController();
    this.controller = controller;
    const run = async () => {
      try {
        const res = await fetch('/live/campaign/' + campaignId + '/events', {
          headers: { Authorization: 'Bearer ' + this.auth.token() }, signal: controller.signal,
        });
        if (!res.ok || !res.body) throw new Error('HTTP ' + res.status);
        this.online.set(true);
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let cut: number;
          while ((cut = buffer.indexOf('\n\n')) >= 0) {
            const block = buffer.slice(0, cut);
            buffer = buffer.slice(cut + 2);
            let type = 'message';
            const data: string[] = [];
            for (const line of block.split('\n')) {
              if (line.startsWith('event:')) type = line.slice(6).trim();
              else if (line.startsWith('data:')) data.push(line.slice(5));
            }
            if (data.length) {
              try {
                const parsed = JSON.parse(data.join('\n'));
                this.zone.run(() => onEvent({ type, data: parsed }));
              } catch { /* evento estranho: ignora */ }
            }
          }
        }
      } catch { /* caiu: tenta de novo */ }
      this.online.set(false);
      if (this.controller === controller && !controller.signal.aborted) setTimeout(run, 2000);
    };
    run();
  }

  disconnect(): void {
    this.controller?.abort();
    this.controller = null;
    this.online.set(false);
  }
}
