# Customizr — site (Angular)

O site do Customizr, com a **mesma aparência do programa de PC**: mesmas cores, os 7 temas, os mesmos ícones e a mesma organização da tela do jogo (barra de navegação | painel | mapa | chat).

Feito em Angular 22 (componentes standalone, signals, sem zone.js). Conversa com o servidor Spring Boot deste repositório pelas mesmas rotas que a tela `/mesa` usa.

## O que já funciona

- **Entrar / criar conta** (`/api/auth/login`, `/user/create`). O token fica na aba (sessionStorage).
- **Suas mesas**: lista das campanhas, quem é o mestre, criar campanha nova.
- **Tela do jogo** (`/mesa/:id`):
  - barra de navegação com as seções do PC (clicar de novo fecha o painel);
  - **Mapa**: cenas da mesa; o mestre abre uma cena pra todos;
  - **Combate**: ordem de iniciativa e turno atual; o mestre rola, passa o turno e encerra;
  - **mapa** com a imagem da cena, a grade e os tokens;
  - **chat** com rolagens (crítico/falha em verde/vermelho), sussurros e os botões de dado;
  - tudo **ao vivo** pelo canal de eventos do servidor (`/live/campaign/{id}/events`): chat, iniciativa, cenas e tokens.
- **Seletor de tema** em todas as telas (fica salvo no navegador).

Fichas, Grupo, Diário e Som ainda mostram "vai chegar no site" (existem no programa de PC e na `/mesa`).

## Rodar

Precisa do Node.js 22.22+ ou 24.15+.

```bash
npm install
npm start        # http://localhost:4200
```

O `npm start` usa o `proxy.conf.json`: as chamadas da API vão pro servidor em `http://localhost:8080`. Suba o servidor antes.

> **Cuidado com o banco:** se o `.env` da raiz tiver as credenciais do Railway, o servidor local conecta na **produção**. Pra testar com um banco de mentira, suba assim:
>
> `java -jar target/system-0.0.1-SNAPSHOT.jar "--spring.datasource.url=jdbc:h2:mem:teste;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" --spring.datasource.username=sa --spring.datasource.password=`

Outros comandos: `npm run build` (gera `dist/`), `npm test` (testes).

## Aparência compartilhada com o programa de PC

As cores, os temas e os ícones vêm de `../design/` (ver `design/LEIA-ME.md`). **Não edite** estes arquivos aqui, eles são gerados:

- `src/kit/tokens.css` e `src/kit/customizr.css` (cópias de `design/web/`)
- `src/app/design/temas.ts` e `src/app/design/icones.ts`
- `public/img/` (logo)

Pra mudar uma cor ou o kit: edite em `design/` e rode `java design/GerarTokens.java` na raiz do repositório. O programa de PC e o site mudam juntos.

Componentes novos: use as classes do kit (`panel-card`, `button-primary`, `chip`, `list-view`...) e, quando precisar de cor própria, as variáveis (`var(--c-accent)`, `var(--c-surface)`...), nunca cor fixa — assim os 7 temas funcionam sozinhos.

## Organização

```
src/app/
  core/      auth (login + token + guarda das rotas), api (chamadas), live (eventos ao vivo), theme, models
  shared/    icon (ícones do Customizr), theme-picker
  pages/     login, campaigns (suas mesas), game (tela do jogo)
  design/    (gerado) temas e ícones
src/kit/     (gerado) estilo compartilhado com o programa de PC
```
