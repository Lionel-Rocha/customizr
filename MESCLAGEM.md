# Mesclagem da mesa no Customizr — resumo para revisão

Este branch (`mesclagem-mesa`) traz para o Customizr o que foi feito no programa de mesa à parte (o antigo "Embertale"), reescrito no padrão deste projeto: entidades JPA em `model/`, serviços com checagem de mestre/membro, DTOs `record` (um por arquivo, `XxxRequest` / `XxxResponse`) e testes com H2. O ponto de partida é o commit `e740c23` ("Consertando import de classes").

* **Testes:** 106, todos passando (antes: 23, com 3 quebrados).
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
| `model/Token.java`, `dto/TokenResponse.java` | + condições próprias (monstro/NPC) e alcance de visão; `TokenResponse` + `conditions`, `armorClass` e `visionFt` | Combate e visão na mesa |
| `model/Scene.java`, `dto/SceneResponse.java` | + paredes/portas (`scene_walls`), `visionEnabled` e áreas de efeito (`scene_templates`) | Paredes, visão e áreas |
| `model/Token.java` (de novo) | + camada, altura, fichas do token (`token_members`) e marcadores (`token_markers`); "oculto pros jogadores" = oculto ou camada do mestre | Camadas, caravana e marcadores |
| `model/Token.java` (tamanho) | + `width_cells` / `height_cells` (tamanho livre; `sizeCells` continua, arredondado pra cima) | Miúdo, Imenso maior que 4×4, objetos retangulares |
| `model/MapObject.java` (novo, tabela `map_objects`) | Biblioteca de objetos com imagem | Importar imagens de mesa, barril etc. |
| `dto/CampaignExport.java`, `service/CampaignTransferService.java` | Formato 2: + cenas (paredes, áreas, tokens, luz), monstros customizados, objetos e ícones; imagem de outra campanha do mesmo servidor é copiada | A exportação não levava a mesa |
| `model/Scene.java`, `model/Token.java`, `model/AreaTemplate.java` (luz) | + `lighting` da cena, luz e visão no escuro do token, área de escuridão; `Scene.coverBetween` / `coverFrom` | Luz e cobertura |
| `model/CampaignIcon.java` (novo, tabela `campaign_icons`) | Pacote de ícones por campanha (condições e marcadores) | Usar pacotes de marcadores sem pôr as imagens no repositório |
| `service/CharacterSheetService.java` | `roll` virou `roll` (confere acesso) + `rollFor` (a rolagem em si) | A mesa rola a salvaguarda de quem está numa área |
| `model/Monster.java`, `dto/MonsterResponse.java` | + `campaign` (monstro customizado da campanha); resposta + `campaignId`, `custom`, `customActions` | Ficha de monstro em branco / cópia editável |
| `MonsterController` | a busca e a leitura passam a olhar quem pede (customizados só pra quem é da campanha) | Homebrew não vaza pra outras campanhas |
| `service/CharacterService.java`, `CharacterSheetService.java` | PV, condições e descanso avisam a mesa ao vivo (`LiveTableService.characterChanged`) | O token mostra o PV da ficha na hora |
| `model/NPC.java` | + ficha de monstro opcional (`statBlock`) | NPC usando um monstro do bestiário |
| `model/Campaign.java` | + listas com cascata (chat, mundos, enciclopédia, imagens, cenas, iniciativa) | Apagar a campanha leva tudo junto |
| `model/SecurityConfig.java` | + `DispatcherType.ASYNC` liberado; + `GET /mesa/**` liberado | A transmissão ao vivo (SSE) responde de forma assíncrona; a tela da mesa é um arquivo estático |
| `controllers/UserController.java` | + `GET /user/me` | A tela da mesa precisa saber quem está logado |
| `model/Membership.java` | + `color` (opcional, `#RRGGBB`) e `changeColor` | Cada jogador escolhe a cor dos próprios tokens na mesa |
| `controllers/ChatController.java`, `CharacterController.java` | + rotas de cartão no chat, lista de features e preparar magia | Clicar numa feature da ficha e ela aparecer no chat |
| Formato das respostas | `CharacterSheetResponse` (+ `combat`, `details`), `PartyResponse` (+ `maxSize`, `members`), `NpcResponse` (+ `statBlock*`), `InventoryResponse` (novo) | **O front pode precisar ler os campos novos**; nenhum campo antigo foi removido |
| `resources/data.sql` | + "Campanha de O Escriba" com O Artífice como jogador | O teste `sqlSeedCreatesCampaign...` já esperava isso e falhava |
| Testes `CharacterServiceTests`, `CharacterSheetServiceTests`, `ItemImportTests`, `SpellcastingModelTests` | Ajustes mínimos | Não compilavam ou falhavam no `e740c23` (API mudada, LAZY fora de transação, `10` vs `10.00`) |
| `dto/SourceResponse.java` | + `official` (true/false/null) | O `/source` e o detalhe da classe mostram se a fonte é oficial (vem da atualização do 5etools); nenhum campo saiu |
| Teste `ClassImportTests` (do seu `220ea25`) | `Components.VERBAL`/`SOMATIC` → `Verbal`/`Somatic` | Não compilava nem na sua `main`: o enum é `Verbal`, `Somatic`, `Material` |
| Teste `ClassSpellAssociationTests` (do seu `220ea25`) | Entra com um usuário de verdade (token do `JwtService`) e é `@Transactional` | Dava 403 (a rota exige login no `SecurityConfig`) e depois `LazyInitializationException` ao ler `availableSpells`, também na sua `main` |

## Antes de colocar no ar: o banco de produção

**INT e SAB das fichas que já existem estão trocados no banco.** A correção vale para o que for salvo daqui em diante; o que já está gravado precisa ser destrocado uma vez:

* Script: `src/main/resources/db/manual-migrations/20261117_swap_intelligence_wisdom.sql` (no mesmo esquema do `20261116_features_description_text.sql`).
* **Rodar junto com o deploy desta versão, antes de alguém salvar atributos pela versão nova.** Fichas salvas pela versão nova já estão certas e seriam trocadas de novo.
* O script anota que rodou (tabela `manual_migrations`), então rodar duas vezes não estraga nada. No fim dele há uma consulta comentada que mostra o que vai mudar.
* O teste `ManualMigrationTests` confere que o UPDATE do script destroca certo.

As tabelas e colunas novas o Hibernate cria sozinho (`ddl-auto=update`). Todas aceitam nulo ou têm valor padrão, para não quebrar as linhas que já existem.

## Testado com PostgreSQL 17 (cópia local, não o banco de produção)

Simulação do deploy, feita num Postgres 17.12 temporário no PC:

1. A versão que está no ar (`main`, `e740c23`) criou o banco e os dados: usuários, campanha com convite, personagem e grupo. O bug de INT/SAB apareceu como no banco real: INT 8 / SAB 16 digitados ficaram gravados como INT 16 / SAB 8.
2. Esta versão subiu no mesmo banco e criou sozinha as tabelas e colunas novas (59 tabelas no total), sem nenhum erro. Os dados antigos continuaram abrindo, e os campos novos vêm vazios sem quebrar nada.
3. O script `20261117_swap_intelligence_wisdom.sql` destrocou INT/SAB (a Percepção passiva foi de 9 para 13). Rodado de novo, viu que já estava aplicado e não destrocou.
4. As 36 chamadas das funções novas passaram sem erro no Postgres: combate, áreas, marcadores, tamanho, luz, monstros customizados, objetos, ícones, portas, exportar e importar.

**Pontos de segurança vistos no caminho (no código que já existia):**

* Sem a variável `SPRING_DATASOURCE_URL`, o `application.properties` conecta direto no banco de produção do Railway. Quem rodar o projeto no próprio PC sem configurar nada mexe nos dados reais. Sugestão: tirar o endereço de produção do arquivo e deixar só a variável.
* `jwt.secret` está escrito no `application.properties`, num repositório público: qualquer um consegue gerar um login válido. Sugestão: ler de uma variável de ambiente e trocar a chave.
* Dá pra criar dois usuários com o mesmo nome (`/user/create`); o login desse nome passa a falhar. O mesmo vale pra fontes com o mesmo código (`/source/create`), que quebra a importação de monstros.

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
3. **Token vencido dá erro 500:** quando o JWT expira, o filtro de login deixa a `ExpiredJwtException` escapar e o servidor responde 500 em vez de 401. Não mexi no `security/`; sugestão: pegar a exceção no filtro e seguir sem autenticar (aí vira 401/403).
4. **`management.endpoints.web.exposure.include=env`** no `application.properties`: se um dia o Actuator entrar, isso expõe segredos. Sugiro tirar.
5. **O que ainda não existe:** encomendas, bastiões, conexões entre cidades e migrações versionadas (Flyway).

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
| 13 | Monstros customizados (ficha em branco ou cópia), testes do monstro pelo token, paredes/portas e visão dinâmica |
| 14 | Áreas de efeito com salvaguarda automática, marcadores com duração, altura, camadas e token de grupo (caravana) |
| 15 | Tamanho livre do token (padrões por tamanho de criatura), biblioteca de objetos com imagem, ícones das condições |
| 16 | Pacote de ícones da campanha (condições e marcadores com imagem); imagens carregam por `onload` |
| 17 | Exportação completa da mesa; luz (cena escura, tochas, visão no escuro, escuridão mágica); cobertura no ataque e na área |
| 18 | Resultado do teste com PostgreSQL 17 e pontos de segurança |
| 19 | Botões de dano no chat; concentração automática (`service/ConcentrationService.java`) |
| 20 | "Ver como jogador" pro mestre na tela `/mesa` |
| 21 | Régua compartilhada, movimento colorido pelo deslocamento, auras (`token_auras`) e anel de alcance do ataque |
| 22 | Alfinetes no mapa com verbete da enciclopédia (`scene_pins`) e ping (o mestre pode centralizar a tela de todos) |
| 23 | Modo telão na tela `/mesa` (TV da mesa presencial: visão do grupo, cena ativa) |
| 24 | Cor de cada jogador (`Membership.color`, coluna nova opcional) e destaque de quem é a vez |
| 25 | Lojas e espólio (`shops`, `shop_entries`, `loot`, `loot_entries`; `TradeService`), aba Comércio e exportação |
| 26 | Itens com preço: o importador lê `baseitem`, aceita raridade `none` (item comum) e dá aos mágicos sem preço o valor do DMG 2024 (`MagicItemPricing`); preço do mestre por campanha pros comuns sem preço (`campaign_item_prices`, `ItemPriceService`) |
| 27 | Categorias dos itens nas suas 10 tags (`ItemCategory`): venenos, montarias e veículos, bugigangas/tesouro e maravilhosos passam a ser preenchidos; munição (`A` no 5etools) deixa de virar armadura e navio (que tem `ac`) também. O foco mágico continua `ADVENTURING_GEAR`, como no seu teste |
| 28 | Importação aberta a todas as fontes cadastradas (sai a lista fixa `IMPORTABLE_SOURCES`); `Source.official` (coluna nova opcional); atualização direto do 5etools (`FiveToolsSyncService`, oficial = autor da Wizards); itens próprios do mestre por campanha (`Item.campaign`, coluna nova opcional; `CampaignItemService`), na busca com filtro de origem e na exportação |
| 29 | Mescla do seu `220ea25` ("Spells de classes": `GET /class/{id}`, `/class-spell/add` com JSON) sem conflito; as magias que a mesa liga às classes (`/spell/class-lists/import`) aparecem no seu detalhe da classe; `SourceResponse` + `official`; dois testes seus corrigidos (ver acima) |
| 30 | Ficha: recursos de classe com usos (`ClassResources`, regras de 2014 e 2024, recarga por descanso), recursos criados à mão (`SheetResource`, tabela nova `sheet_resources`) e Inspiração Heroica (`Sheet.heroicInspiration`, coluna nova opcional; o mestre dá, gastar rola de novo). Descanso devolve e diz o que voltou. Tudo vai na exportação. Na `/mesa`: bloco "Recursos e descanso" na aba Ficha e "Rolar de novo (Inspiração)" no painel do token |
