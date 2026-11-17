# Mesclagem da mesa no Customizr — resumo para revisão

Este branch (`mesclagem-mesa`) traz para o Customizr o que foi feito no programa de mesa à parte (o antigo "Embertale"), reescrito no padrão deste projeto: entidades JPA em `model/`, serviços com checagem de mestre/membro, DTOs `record` (um por arquivo, `XxxRequest` / `XxxResponse`) e testes com H2. O ponto de partida é o commit `e740c23` ("Consertando import de classes").

* **Testes:** 74, todos passando (antes: 23, com 3 quebrados).
* **Arquivos de exemplo:** o que é do 5etools nos testes são amostras pequenas do SRD 5.2 (licença CC-BY-4.0, atribuição em `src/test/resources/importer/LEIA-ME.txt`).
* **Fins de linha:** tudo em LF, igual ao resto do repositório.

## Como olhar rápido

1. `mvn test` (tudo roda com H2, sem precisar do banco).
2. Suba o servidor e abra **`/mesa`** no navegador: é uma tela de jogo simples, que usa só a API (login, cena, grade quadrada ou hexagonal, neblina, tokens, iniciativa e chat ao vivo). Ela serve para testar e de referência para o front em Angular.
3. As rotas novas estão na tabela de rotas do `README.md`; a seção "Mesclagem com a mesa" explica cada parte.

## O que mudou em código que já era seu (vale revisar com atenção)

| Onde | O que mudou | Por quê |
|---|---|---|
| `model/Attributes.java` | Ordem do construtor agora é (FOR, DES, CON, **INT, SAB**, CAR) | Corrige a troca de INT/SAB ao salvar atributos (item do roadmap) |
| `model/Time.java` | + `TenMinutes`, `OneHour`, `EightHours`, `TwelveHours`, `TwentyFourHours` | Tempos de conjuração que aparecem nas magias do 5etools; nada foi removido |
| `model/DurationUnit.java`, `model/Duration.java` | + `SPECIAL`; `UNTIL_DISPELLED` e `SPECIAL` usam quantidade 0 | Antes "até ser dissipada" exigia um número |
| `model/Spell.java` | + `metadata` (entrada original do 5etools, só nas importadas) | Mesmo padrão do `Feature`/`DndClass` |
| `model/Sheet.java`, `model/Character.java` | PV, CA, perícias, condições, espaços gastos, espécie, antecedente, XP, retrato... | Ficha completa (os campos novos aceitam nulo, então fichas antigas continuam carregando) |
| `model/Party.java`, `dto/CreatePartyRequest.java` | + `maxSize` | Roadmap P0.2 |
| `model/Token.java`, `dto/TokenResponse.java` | + condições próprias (monstro/NPC); `TokenResponse` + `conditions` e `armorClass` | Combate na mesa |
| `service/CharacterService.java`, `CharacterSheetService.java` | PV, condições e descanso avisam a mesa ao vivo (`LiveTableService.characterChanged`) | O token mostra o PV da ficha na hora |
| `model/NPC.java` | + ficha de monstro opcional (`statBlock`) | NPC usando um monstro do bestiário |
| `model/Campaign.java` | + listas com cascata (chat, mundos, enciclopédia, imagens, cenas, iniciativa) | Apagar a campanha leva tudo junto |
| `model/SecurityConfig.java` | + `DispatcherType.ASYNC` liberado; + `GET /mesa/**` liberado | A transmissão ao vivo (SSE) responde de forma assíncrona; a tela da mesa é um arquivo estático |
| `controllers/UserController.java` | + `GET /user/me` | A tela da mesa precisa saber quem está logado |
| `controllers/ChatController.java`, `CharacterController.java` | + rotas de cartão no chat, lista de features e preparar magia | Clicar numa feature da ficha e ela aparecer no chat |
| Formato das respostas | `CharacterSheetResponse` (+ `combat`, `details`), `PartyResponse` (+ `maxSize`, `members`), `NpcResponse` (+ `statBlock*`), `InventoryResponse` (novo) | **O front pode precisar ler os campos novos**; nenhum campo antigo foi removido |
| `resources/data.sql` | + "Campanha de O Escriba" com O Artífice como jogador | O teste `sqlSeedCreatesCampaign...` já esperava isso e falhava |
| Testes `CharacterServiceTests`, `CharacterSheetServiceTests`, `ItemImportTests`, `SpellcastingModelTests` | Ajustes mínimos | Não compilavam ou falhavam no `e740c23` (API mudada, LAZY fora de transação, `10` vs `10.00`) |

## Antes de colocar no ar: o banco de produção

**INT e SAB das fichas que já existem estão trocados no banco.** A correção vale para o que for salvo daqui em diante; o que já está gravado precisa ser destrocado uma vez:

* Script: `src/main/resources/db/manual-migrations/20261117_swap_intelligence_wisdom.sql` (no mesmo esquema do `20261116_features_description_text.sql`).
* **Rodar junto com o deploy desta versão, antes de alguém salvar atributos pela versão nova.** Fichas salvas pela versão nova já estão certas e seriam trocadas de novo.
* O script anota que rodou (tabela `manual_migrations`), então rodar duas vezes não estraga nada. No fim dele há uma consulta comentada que mostra o que vai mudar.
* O teste `ManualMigrationTests` confere que o UPDATE do script destroca certo.

As tabelas e colunas novas o Hibernate cria sozinho (`ddl-auto=update`). Todas aceitam nulo ou têm valor padrão, para não quebrar as linhas que já existem.

## Ajustes ao seu padrão (feitos depois de estudar o `ClassService`)

* **Catálogo novo (espécies, antecedentes, talentos e monstros)** segue o `Feature`:
  * `description` com os `entries` crus, com as tags `{@...}` intactas para o front renderizar;
  * `metadata` com a entrada original inteira (coluna JSON);
  * `sourceCode` e `page`.
* **Texto pronto:** além do cru, cada item tem `descriptionText`, a mesma descrição já convertida em texto.
* **Importações** seguem o formato do `/item/update-from-dump`: `created`, `updated` e `skipped`, cada ignorado com o motivo.
* **Regras de acesso:** as partes novas usam o `MasterGuard` do diagrama (`service/MasterGuard.java`).
* **Traços e características como `Feature`:** os traços de espécie e as características de antecedente viram `Feature` (categoria `species` / `background`, `AUTOMATIC` ou `CHOICE` pela mesma regra do seu `hasChoiceOptions`).
  * Assim, a ficha lista e manda pro chat tudo do mesmo jeito.
  * As escolhas de classe disponíveis são adicionadas pela sua rota `/character/{id}/classes/{ccId}/feats`, sem caminho paralelo.

## Decisões que ficam com você

1. **Proteção das campanhas (item 3 do roadmap):** `deleteCampaign`, `updateCampaign`, `addSource`, `readCampaign` e a listagem de personagens não conferem quem chama. Não mexi, mas deixei a sugestão no README: usar o `MasterGuard`.
2. **Tela de jogo:** `/mesa` funciona hoje, mas o lugar dela pode ser o seu Angular. Toda a lógica está na API (`/live/...`) e a página serve de exemplo de uso.
3. **`management.endpoints.web.exposure.include=env`** no `application.properties`: se um dia o Actuator entrar, isso expõe segredos. Sugiro tirar.
4. **O que ainda não existe:** paredes e visão dinâmica, lojas e encomendas, bastiões, conexões entre cidades e migrações versionadas (Flyway).

## Commits

| Commit | Conteúdo |
|---|---|
| 1 | Mescla a mesa: ficha (PV/CA), importação de personagem, dados, chat, mundo, Azgaar, enciclopédia, imagens, conteúdo 5etools, `MasterGuard` |
| 2 | Ficha completa: origem, perícias, salvaguardas, condições, testes contra a morte, espaços, descansos, ataques, XP, retrato |
| 3 | Moedas (pl/po/pe/pp/pc) e exportar/importar campanha |
| 4 | Mesa ao vivo no servidor: cenas, tokens, neblina, iniciativa e transmissão |
| 5 | Alinhamento ao seu padrão (descrição crua com tags, `metadata`; DTOs um por arquivo) |
| 6 | Script de INT/SAB; tela `/mesa`; `GET /user/me` |
| 7 | Pré-requisitos dos talentos conferidos (o mestre pode liberar) |
| 8 | Este resumo e os últimos acertos no README |
| 9 | Traços/características como `Feature`; lista da ficha e cartões no chat; preparar magia; gerador de atributos |
| 10 | Aba Ficha na tela `/mesa` (lista com Adicionar/Chat, gerador de atributos) e documentação |
| 11 | Programa de mesa de PC (`mesa-pc/`): código, ponte e scripts, sem o conteúdo dos livros nem os programas montados |
| 12 | Combate na mesa: ataque de token em token (ficha do personagem ou do monstro), dano/cura, condições, painel na tela `/mesa` |
