

Customizr, o projeto de código aberto para criação de mundos customizados para D&D 5E.

# Introdução

Resumindo, VTTs ou são pagos ou são incompletos. Não ajudam muito na criação de mundos e focam mais em combate.

Daí eu resolvi fazer uma parada que ajudasse na imersão do mundo, e que não fosse engessado demais.

Na real, eu fiz isso para minha própria campanha, mas se for útil para mais alguém, tá maneiro.

# Funcionalidades

* **Criação de mundo:** mundo, cidade, países, NPCs, enciclopédia.
* **Mapas interativos:** mapa por cidade, país, país no mundo. Possui pins de ponto de interesse.
* **Economia viva:** lojas possuem estoque, compra e venda, encomenda de itens mágicos com módulo de entrega.
* **Party e inventário:** inventário compartilhado, inventário próprio.

# Stack

Back-end: Spring Boot
Front-end: Angular.js
Banco de dados: PostgreSQL

# Execução com Docker e ngrok

O Compose deste repositório inicia o PostgreSQL, o backend, o frontend e um gateway. O ngrok publica o gateway em uma URL: páginas são encaminhadas ao Angular e as rotas da API (`/api`, `/user`, `/campaign`, `/character`, `/party`, `/source`, `/class`, `/item` e `/spell`) ao backend.

1. Copie `.env.example` para `.env` e preencha `NGROK_AUTHTOKEN` e `JWT_SECRET`. Ajuste `FRONTEND_PATH` se o frontend não estiver em `C:/Users/user/WebstormProjects/customizr-front`.
2. Na pasta deste repositório, execute `docker compose up --build`.
3. Consulte a URL pública do ngrok com `docker compose logs -f ngrok`. O gateway também estará disponível localmente em `http://localhost:8081`.

O banco de dados usa um volume Docker persistente e não publica sua porta no host.

# TO-DO / Roadmap

A ordem prioriza uma primeira versão jogável e segura; depois, os recursos avançados de criação de mundo.

## P0 — Completar e estabilizar o núcleo

1. [x] Expor as funcionalidades que já têm serviço/modelo, mas ainda não têm API completa.
   - [x] Implementar `ItemController` para criar, consultar, pesquisar, atualizar e excluir itens.
   - [x] Criar endpoints para inventários de personagens, parties e NPCs: consultar, adicionar/remover itens e atualizar dinheiro.
   - [x] Criar endpoints para NPCs: listar por campanha, consultar, atualizar e excluir.
2. [ ] Completar a experiência de party.
   - [x] Consultar party e seus membros; respeitar `maxSize` e as regras de campanha.
   - [ ] Consultar e usar o inventário compartilhado da party pela API e pela interface. *(API pronta em `/inventory`; falta a interface.)*
3. [ ] Fechar regras de acesso e testes dos fluxos principais.
   - [ ] Garantir que apenas membros da campanha acessem seus dados e que ações de mestre sejam exclusivas do mestre.
   - [ ] Cobrir com testes os fluxos de campanha, convite, personagem, party, inventário e importação, incluindo erros e permissões.
   - [ ] Padronizar respostas de erro e validação dos endpoints.
4. [ ] Completar e corrigir a ficha do personagem.
   - [x] Corrigir e testar o mapeamento de Sabedoria e Inteligência ao atualizar atributos.
   - [x] Adicionar pontos de vida, classe de armadura e demais valores derivados necessários à ficha.
   - [x] Adicionar imagem de perfil para personagem e NPC, com armazenamento de imagens em vez de depender apenas de URLs externas.

## P1 — Tornar personagens e conteúdo jogáveis

5. [ ] Importar conteúdo de jogo por JSON.
   - [ ] Importar itens, validando campos e fonte, e retornar um relatório de itens importados, ignorados e inválidos.
   - [ ] Permitir importar e atualizar conteúdo sem criar duplicatas.
   - [x] Expandir a importação para espécies, antecedentes, magias, talentos e monstros.
6. [ ] Completar a criação e progressão de personagens.
   - [x] Implementar espécies e antecedentes, incluindo características e aumentos de atributos. *(O personagem aponta para a espécie e o antecedente do catálogo; os aumentos de atributo ficam no texto e não são aplicados sozinhos.)*
   - [ ] Implementar progressão de nível, pontos de vida e benefícios recebidos por nível.
   - [ ] Implementar magias: catálogo, listas por classe, espaços, conhecidas/preparadas e seleção na ficha.
   - [x] Implementar talentos e seus pré-requisitos de atributos, nível, espécie, talento e característica. *(Pré-requisitos conferidos pela entrada original do 5etools; o mestre pode liberar.)*
7. [ ] Completar NPCs e monstros.
   - [x] Criar fichas de NPC e monstro, com estatísticas, características, imagem e inventário opcional.
   - [x] Controlar quais informações do NPC ficam visíveis para jogadores e quais são exclusivas do mestre.

## P2 — Ferramentas de criação de mundo

8. [x] Implementar a hierarquia de mundo: mundo, país, cidade e pontos de interesse, com operações de criação, consulta, atualização e exclusão.
   - [x] Adicionar mapas, coordenadas e pins para países, cidades e pontos de interesse.
   - [ ] Implementar conexões entre cidades com distâncias, meios de transporte, duração e custo.
9. [x] Implementar a enciclopédia da campanha.
   - [x] Criar, editar, consultar, pesquisar e ocultar/mostrar entradas.
   - [x] Vincular entradas a NPCs, cidades, países e pontos de interesse.
10. [ ] Implementar lojas e economia.
    - [ ] Criar lojas com estoque e permitir compra e venda de itens.
    - [ ] Criar encomendas de itens, estados do pedido e módulo de entrega.
    - [x] Suportar moedas de platina, ouro, prata e cobre, além de conversões e preços.
11. [ ] Implementar bastiões e salas.
    - [ ] Criar bastiões de personagens, com nível, limite de salas e tipos de sala.
    - [ ] Aplicar requisitos, tamanhos e efeitos de cada tipo de sala.

## P3 — Recursos adicionais e acabamento

12. [x] Implementar chat por campanha, com histórico e acesso restrito aos participantes.
13. [ ] Completar a interface web para campanhas, personagens, parties, inventários, mapas, enciclopédia e lojas.
    - [ ] Tornar a interface responsiva e acessível.
    - [ ] Adicionar buscas, filtros e paginação onde houver listas grandes.
14. [ ] Sincronizar README, diagrama de classes e tabela de rotas com o comportamento real da aplicação.
15. [ ] Preparar a aplicação para uso contínuo.
    - [ ] Adicionar métricas e logs para localizar lentidão e erros.
    - [ ] Revisar consultas para evitar N+1 e adicionar paginação/cache quando necessário.
    - [ ] Definir migrações de banco, backups e exportação/importação de campanhas. *(Exportação/importação pronta em `/campaign/{id}/export` e `/campaign/import`; faltam as migrações.)*

# Rotas atuais

Abaixo estão todos os endpoints atualmente expostos pelo backend da aplicação. A maioria das rotas autenticadas exige um token JWT no header `Authorization: Bearer <token>`.

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/login` | Autentica o usuário e retorna um token JWT. |
| `POST` | `/user/create` | Cria um usuário. |
| `GET` | `/user/{userId}` | Consulta um usuário pelo ID. |
| `POST` | `/campaign/create` | Cria uma campanha. |
| `PATCH` | `/campaign/update/{id}` | Atualiza uma campanha. |
| `DELETE` | `/campaign/{id}` | Exclui uma campanha. |
| `GET` | `/campaign/` | Lista as campanhas do usuário autenticado. |
| `GET` | `/campaign/{code}` | Consulta uma campanha pelo código. |
| `POST` | `/campaign/addSource` | Adiciona uma fonte à campanha. |
| `POST` | `/campaign/invite` | Convida um usuário para a campanha. |
| `POST` | `/campaign/accept-invite` | Aceita um convite para campanha. |
| `POST` | `/character/create` | Cria um personagem. |
| `GET` | `/character/{id}` | Consulta um personagem. |
| `PATCH` | `/character/update/{id}` | Atualiza um personagem. |
| `DELETE` | `/character/{id}` | Exclui um personagem. |
| `POST` | `/character/{id}/classes` | Adiciona uma classe ao personagem. |
| `POST` | `/character/{id}/classes/{characterClassId}/subclass` | Escolhe uma subclasse para a classe do personagem. |
| `POST` | `/character/{id}/classes/{characterClassId}/feats` | Adiciona um talento/feat à classe do personagem. |
| `PUT` | `/character/{id}/attributes` | Atualiza os atributos do personagem. |
| `GET` | `/character/{id}/sheet` | Consulta a ficha completa do personagem. |
| `GET` | `/character/{campaignId}/characters` | Lista os personagens de uma campanha. |
| `POST` | `/class/import` | Importa classes a partir de um payload JSON. |
| `POST` | `/class/subclass-features` | Adiciona uma feature a uma subclasse existente. |
| `GET` | `/class/all` | Lista as classes disponíveis. |
| `POST` | `/item/create` | Cria um item. |
| `GET` | `/item/{itemId}` | Consulta um item pelo ID. |
| `GET` | `/item/all` | Lista todos os itens. |
| `GET` | `/item/source/{sourceId}` | Lista os itens de uma fonte. |
| `GET` | `/item/search?name={name}` | Pesquisa itens pelo nome. |
| `PUT` | `/item/update/{itemId}` | Atualiza um item. |
| `DELETE` | `/item/{itemId}` | Exclui um item. |
| `POST` | `/item/import` | Importa novos itens a partir de um dump JSON. |
| `POST` | `/item/update-from-dump` | Cria ou atualiza itens existentes a partir de um dump JSON. |
| `POST` | `/spell/create` | Cria uma magia. |
| `GET` | `/spell/{id}` | Consulta uma magia pelo ID. |
| `GET` | `/spell/all` | Lista todas as magias. |
| `GET` | `/spell/search?name={name}` | Pesquisa magias pelo nome. |
| `PUT` | `/spell/update/{id}` | Atualiza uma magia. |
| `DELETE` | `/spell/{id}` | Exclui uma magia; retorna conflito se ela estiver vinculada a classes ou fichas. |
| `POST` | `/party/campaign/{campaignId}` | Cria uma party dentro da campanha. |
| `PUT` | `/party/{partyId}/characters/{characterId}` | Adiciona um personagem à party. |
| `DELETE` | `/party/{partyId}/characters/{characterId}` | Remove um personagem da party. |
| `POST` | `/source/create` | Cria uma fonte de conteúdo. |
| `GET` | `/source/{code}` | Consulta uma fonte pelo código. |
| `GET` | `/source/all` | Lista todas as fontes disponíveis. |
| `PUT` | `/character/{id}/combat` | Define PV máximo/atual/temporário, CA e deslocamento (campos nulos ficam como estão). |
| `POST` | `/character/{id}/hp` | Dano (gasta o PV temporário primeiro), cura (até o máximo) ou PV temporário. |
| `POST` | `/character/import` | Importa personagem (multipart `file` + `campaignId`): Foundry VTT dnd5e, D&D Beyond JSON ou PDF preenchível. |
| `GET` | `/party/{partyId}` | Consulta a party e os membros (membros da campanha). |
| `GET` | `/party/campaign/{campaignId}` | Lista as parties da campanha. |
| `PATCH` | `/party/{partyId}/max-size` | Muda o limite de membros (`{"maxSize": 5}`; null = sem limite). |
| `GET` | `/inventory/character/{characterId}` | Inventários do personagem e da party dele. |
| `GET` | `/inventory/npc/{npcId}?actorCharacterId=` | Inventário do NPC. |
| `GET` | `/inventory/{inventoryId}?actorCharacterId=` | Consulta um inventário. |
| `POST` | `/inventory/{inventoryId}/items` | Adiciona item (`actorCharacterId`, `itemId`, `quantity`). |
| `DELETE` | `/inventory/{inventoryId}/items/{itemId}?actorCharacterId=&quantity=` | Remove item. |
| `PATCH` | `/inventory/{inventoryId}/money` | Soma ou tira dinheiro, em po (`actorCharacterId`, `amount`). |
| `POST` | `/npc/campaign/{campaignId}` | Cria NPC (mestre). |
| `GET` | `/npc/campaign/{campaignId}` | Lista NPCs (jogadores não recebem `masterInformation`). |
| `GET` / `PUT` / `DELETE` | `/npc/{id}` | Consulta, atualiza ou exclui um NPC. |
| `POST` | `/dice/roll` | Rola uma expressão (`2d6+3`, `4d6kh3`, `2d20kl1`) sem registrar no chat. |
| `POST` | `/chat/campaign/{campaignId}` | Envia mensagem: texto com `[[1d20+5]]`, `/r 1d20+5 Ataque`, `/me ação`, `/w Nome segredo`. |
| `GET` | `/chat/campaign/{campaignId}?before=&limit=` | Histórico do chat (sussurros só para autor, destinatário e mestre). |
| `POST` | `/world/campaign/{campaignId}` | Cria um mundo (mestre). |
| `GET` | `/world/campaign/{campaignId}` | Lista os mundos com países, cidades e pontos. |
| `POST` | `/world/campaign/{campaignId}/import/azgaar` | Cria mundo, países, cidades e entradas da enciclopédia a partir do JSON "Full" do Azgaar. |
| `GET` / `PUT` / `DELETE` | `/world/{worldId}` | Consulta, atualiza ou exclui um mundo. |
| `POST` | `/world/{worldId}/countries` | Adiciona país. |
| `GET` / `PUT` / `DELETE` | `/world/countries/{countryId}` | País. |
| `POST` | `/world/countries/{countryId}/cities` | Adiciona cidade. |
| `GET` / `PUT` / `DELETE` | `/world/cities/{cityId}` | Cidade (com `generatorUrl` para o gerador de cidades do Watabou). |
| `POST` | `/world/cities/{cityId}/points` | Adiciona ponto de interesse (SHOP, INN, BASTION, TOURISTIC, OTHER). |
| `PUT` / `DELETE` | `/world/points/{pointId}` | Ponto de interesse. |
| `POST` | `/encyclopedia/campaign/{campaignId}` | Cria entrada (mestre); pode referenciar NPC, país, cidade ou ponto. |
| `GET` | `/encyclopedia/campaign/{campaignId}?q=` | Lista e pesquisa entradas (jogadores só veem as visíveis). |
| `GET` / `PUT` / `DELETE` | `/encyclopedia/{entryId}` | Entrada. |
| `PATCH` | `/encyclopedia/{entryId}/visibility` | Mostra ou esconde dos jogadores (`{"visible": true}`). |
| `POST` | `/image` | Envia imagem (multipart `file` + `campaignId`; PNG, JPEG, WebP ou GIF, até 8 MB). |
| `GET` | `/image/{id}` | Baixa a imagem (membros da campanha). |
| `POST` | `/content/species/import` | Importa espécies do 5etools (`{"race": [...]}`). |
| `POST` | `/content/backgrounds/import` | Importa antecedentes do 5etools (`{"background": [...]}`). |
| `POST` | `/content/feats/import` | Importa talentos do 5etools (`{"feat": [...]}`). |
| `GET` | `/content/species`, `/content/backgrounds`, `/content/feats` | Lista (ou pesquisa com `?name=`). |
| `POST` | `/spell/import` | Importa magias do 5etools (`{"spell": [...]}`); cria ou atualiza por nome + fonte. |
| `POST` | `/spell/class-lists/import` | Liga magias às classes já importadas (arquivo `gendata-spell-source-lookup.json` do 5etools). |
| `POST` | `/monster/import` | Importa monstros do bestiário do 5etools (`{"monster": [...]}`). |
| `GET` | `/monster?name=&minCr=&maxCr=` | Lista monstros (filtro por nome e faixa de ND; 1/4 = 0.25). |
| `GET` | `/monster/{id}` | Ficha completa do monstro (texto já legível). |
| `PUT` | `/npc/{id}/stat-block` | Faz o NPC usar uma ficha de monstro (`{"monsterId": "..."}`; null desliga). |
| `PUT` | `/character/{id}/origin` | Espécie e antecedente do catálogo (`speciesId`, `backgroundId`). |
| `PUT` | `/character/{id}/proficiencies` | Perícias, especialização e salvaguardas (`skills`, `expertise`, `savingThrows`). |
| `PUT` | `/character/{id}/conditions` | Condições (`BLINDED`, `POISONED`...) e exaustão (0 a 6). |
| `POST` | `/character/{id}/spell-slots/spend` | Gasta um espaço de magia (`pool`: STANDARD ou PACT, `level`). |
| `POST` | `/character/{id}/rest` | Descanso curto (gasta `hitDice`) ou longo. |
| `POST` / `DELETE` | `/character/{id}/attacks[/{attackId}]` | Ataques da ficha (nome, bônus, dano, tipo). |
| `POST` | `/character/{id}/attacks/{attackId}/roll` | Rola o ataque e o dano (crítico dobra os dados); `targetArmorClass` diz se acertou; `toChat` manda pro chat. |
| `POST` | `/character/{id}/roll` | Teste de perícia, atributo, salvaguarda, iniciativa ou contra a morte, com as condições e a exaustão aplicadas. |
| `POST` / `DELETE` | `/character/{id}/catalog-feats[/{id}]` | Talentos do catálogo escolhidos pelo personagem. |
| `POST` | `/character/{id}/experience` | Dá (ou tira) XP; só o mestre. |
| `PUT` | `/character/{id}/portrait` | Retrato do personagem (ex.: `/image/{id}`). |
| `GET` | `/campaign/{id}/export?images=` | Baixa a campanha inteira em JSON (só o mestre). |
| `POST` | `/campaign/import` | Cria uma campanha nova a partir do JSON exportado; quem importa vira o mestre. |
| `GET` | `/user/me` | Usuário logado (id e nome). |
| `GET` | `/character/{id}/features?available=` | Tudo que o personagem tem: features de classe/subclasse pelo nível (AUTOMATIC = já tem; CHOICE = escolha disponível ou feita), traços da espécie e do antecedente, talentos, magias preparadas e itens, com o texto pronto. |
| `POST` | `/character/{id}/features/share` | Manda uma dessas coisas pro chat como cartão, falando como o personagem (`kind`, `refId`). |
| `POST` / `DELETE` | `/character/{id}/classes/{characterClassId}/spells/{spellId}` | Prepara / tira uma magia da lista da classe. |
| `POST` | `/chat/campaign/{id}/share` | Cartão do catálogo no chat (feature, talento, espécie, antecedente, magia, item; monstro só o mestre). |
| `POST` | `/ability-scores/roll` | Rola atributos: X dados de Y faces tirando os N menores (padrão 4d6 tira 1), opção de rolar de novo os 1; `campaignId` manda pro chat. |
| `GET` | `/ability-scores/standard-array` | 15, 14, 13, 12, 10, 8. |
| `POST` | `/ability-scores/point-buy` | Confere uma compra de pontos (27, de 8 a 15): custo, sobra e problemas. |
| `GET` | `/mesa` | Tela de jogo no navegador (arquivo estático; os dados vêm da API com o token). |
| `GET` | `/live/campaign/{id}/events` | Transmissão ao vivo (Server-Sent Events) para os membros da campanha. |
| `POST` / `GET` | `/live/campaign/{id}/scenes` | Cria cena (mestre) / lista cenas (o jogador só vê a ativa). |
| `GET` / `PUT` / `DELETE` | `/live/scenes/{sceneId}` | Cena. |
| `POST` | `/live/scenes/{sceneId}/activate` | Troca a cena que os jogadores veem. |
| `PUT` | `/live/scenes/{sceneId}/fog` | Neblina: ligar/desligar, revelar ou esconder casas `[coluna, linha]`. |
| `POST` | `/live/scenes/{sceneId}/tokens` | Põe token (ligado a personagem, NPC ou monstro; pode ser oculto). |
| `PATCH` | `/live/tokens/{tokenId}/move` | Move token (o dono do personagem ou o mestre); devolve a distância em pés. |
| `PUT` / `DELETE` | `/live/tokens/{tokenId}` | Edita (nome, tamanho, oculto, PV, giro) ou tira o token. |
| `GET` | `/live/tokens/{tokenId}/attacks` | Ataques do token: os da ficha do personagem, ou as ações da ficha do monstro (lidas da entrada do 5etools: `{@hit}` vira ataque, `{@dc}` vira salvaguarda). |
| `POST` | `/live/tokens/{tokenId}/attack` | Ataca outro token (`attackId`, `targetTokenId`, `mode`, `applyDamage`): rola contra a CA do alvo, aplica vantagem/desvantagem pelas condições dos dois, dobra os dados no crítico, tira o PV do alvo (com resistência/imunidade/vulnerabilidade do monstro) e manda pro chat. |
| `POST` | `/live/tokens/{tokenId}/hp` | Dano, cura ou PV temporário no token (personagem: na ficha; monstro/NPC: no token). |
| `PUT` | `/live/tokens/{tokenId}/conditions` | Condições do token (personagem: na ficha; monstro/NPC: no token). |
| `POST` | `/live/tokens/{tokenId}/roll` | Teste pelo token (`kind` ABILITY/SAVE/SKILL/INITIATIVE, `ability`, `skill`, `mode`): personagem pela ficha; monstro pela ficha de monstro (salvaguardas e perícias com o bônus escrito na ficha; paralisado/atordoado/inconsciente/petrificado falham FOR/DES sozinhos). |
| `POST` | `/live/scenes/{sceneId}/walls` | Desenha paredes/portas (`walls`: segmentos x1,y1,x2,y2 e `type` WALL/DOOR). Só o mestre. |
| `DELETE` | `/live/scenes/{sceneId}/walls/{wallId}` · `/live/scenes/{sceneId}/walls` | Apaga uma parede / todas. |
| `PATCH` | `/live/walls/{wallId}` | Porta: `open` (jogador também, se não estiver trancada) e `locked` (só o mestre; trancar fecha). |
| `PUT` | `/live/scenes/{sceneId}/vision` | Liga/desliga a visão dinâmica da cena (`enabled`). |
| `PUT` | `/live/scenes/{sceneId}/lighting` | Luz da cena: `BRIGHT` (clara), `DIM` (penumbra) ou `DARK` (escura: só se vê o que uma luz alcança ou a visão no escuro). |
| `PUT` | `/live/tokens/{tokenId}/light` | Luz do token (`brightFt` / `dimFt`, ex.: tocha 20/20) e visão no escuro (`darkvisionFt`). O dono também muda. |
| `PUT` | `/live/tokens/{tokenId}/vision` | Alcance da visão do token em pés (`visionFt`; vazio = até onde as paredes deixam). |
| `PUT` | `/live/tokens/{tokenId}/placement` | Altura em pés (`elevationFt`; o dono também muda), tamanho (`widthCells` × `heightCells`, de 0,5 a 30 casas), camada (`layer`: SCENERY, BATTLE, OVERHEAD, GM) e fichas que viajam no token (`memberIds`, ex.: a caravana; o dono de qualquer uma move o token e vê por ele). Camada e fichas: só o mestre. |
| `GET` / `POST` / `DELETE` | `/live/campaign/{campaignId}/icons` | Pacote de ícones da campanha: `key` `condition:CODIGO` troca o ícone da condição, `marker:Nome` vira marcador pronto (`imageId` de `POST /image`). Todos da campanha leem; só o mestre põe ou tira. |
| `DELETE` | `/live/icons/{iconId}` | Tira um ícone do pacote. |
| `GET` / `POST` | `/live/campaign/{campaignId}/objects` | Biblioteca de objetos da campanha (mesa, barril...): `name`, `imageId` (de `POST /image`), `widthCells`, `heightCells`, `layer` (padrão: cenário). Só o mestre. |
| `PUT` / `DELETE` | `/live/objects/{objectId}` | Edita / tira um objeto da biblioteca. |
| `POST` | `/live/scenes/{sceneId}/objects/{objectId}` | Põe o objeto no mapa (`x`, `y`, `rotation`): vira um token da camada dele, com a imagem e o tamanho. |
| `PUT` | `/live/tokens/{tokenId}/auras` | Auras do token (até 4: `radiusFt`, `color`, `label`), medidas da borda dele; todos veem. |
| `GET` / `POST` | `/shops/campaign/{campaignId}` | Lojas da campanha (o jogador só vê as abertas) / nova loja (`name`, `description`, `open`, `buyBackPercent`). |
| `PUT` / `DELETE` | `/shops/{shopId}` · `POST` `/shops/{shopId}/items` · `PUT` / `DELETE` `/shops/{shopId}/items/{entryId}` | Edita a loja e os itens à venda (`itemId` do catálogo, `priceGp` (vazio = o do catálogo), `stock` (vazio = sem limite)). Só o mestre. |
| `POST` | `/shops/{shopId}/buy` · `/shops/{shopId}/sell` | Compra (`characterId`, `entryId`, `quantity`) ou vende (`itemId`) pelo personagem: mexe no dinheiro e no inventário dele e no estoque; vai pro chat. |
| `GET` | `/item/campaign/{campaignId}/search` | Busca de itens pra quem está na campanha: catálogo + itens do mestre desta campanha. `name`; `origin` = `OFFICIAL`, `UNOFFICIAL`, `UNKNOWN`, `CUSTOM` (pode juntar com vírgula; vazio = todos). Já traz o preço que vale na campanha. |
| `GET` / `POST` | `/item/campaign/{campaignId}/custom` | Itens próprios do mestre (todo membro vê) / novo item (`name`, `type`, `category`, `rarity`, `priceGp`, `referenceLink`). Só o mestre cria. |
| `PUT` / `DELETE` | `/item/campaign/{campaignId}/custom/{itemId}` | Edita (`clearPrice` apaga o preço) / apaga o item do mestre (sai das lojas e espólios; não apaga se estiver em inventário). |
| `POST` | `/item/campaign/{campaignId}/sync-5etools` | Atualiza o catálogo direto do 5etools (`includeUnofficial=true` traz também o não oficial). Só o mestre. |
| `GET` | `/item/prices/campaign/{campaignId}` | Preços que o mestre deu na campanha aos itens sem preço oficial (`itemId` → po). Todo membro vê. |
| `GET` | `/item/prices/campaign/{campaignId}/unpriced` | Itens sem preço oficial (`name` filtra; `includeMagic=true` mostra também os mágicos, como artefatos), com o preço da campanha. Só o mestre. |
| `PUT` | `/item/prices/campaign/{campaignId}/{itemId}` | O mestre define o preço (`priceGp`; vazio apaga) de um item sem preço oficial. Vale só nesta campanha; o catálogo não muda. |
| `GET` / `POST` | `/loot/campaign/{campaignId}` | Espólio (o jogador só vê o visível) / novo espólio (`name`, `moneyGp`, `visible`). |
| `PUT` / `DELETE` | `/loot/{lootId}` · `POST` `/loot/{lootId}/items` · `DELETE` `/loot/{lootId}/items/{entryId}` | Edita o espólio e os itens. Só o mestre. |
| `POST` | `/loot/{lootId}/take` · `/loot/{lootId}/split-money` | Pega um item pro personagem (`toParty`: pro grupo) / divide as moedas igualmente (mestre; `characterIds` vazio = todos da campanha). |
| `GET` | `/live/campaign/{campaignId}/members` | Gente da campanha com papel e cor (o mestre primeiro). |
| `PUT` | `/live/campaign/{campaignId}/my-color` | O jogador escolhe a própria cor (`#RRGGBB`; vazio = automática). |
| `POST` | `/live/scenes/{sceneId}/ping` | Ping no mapa (`x`, `y`) pra todos; `focus` (só o mestre) centraliza a tela de todo mundo no ponto. |
| `POST` | `/live/scenes/{sceneId}/pins` · `PUT` / `DELETE` `/live/pins/{pinId}` | Alfinetes no mapa (`label`, `icon`, `note`, `entryId` da enciclopédia, `visibleToPlayers`). Só o mestre põe, move e apaga; o jogador só recebe os visíveis. |
| `POST` | `/live/scenes/{sceneId}/ruler` | Régua compartilhada: repassa os pontos pra mesa (nada fica gravado); lista vazia apaga. |
| `PUT` | `/live/tokens/{tokenId}/markers` | Marcadores do token (`label`, `icon`, `roundsLeft`): a contagem desce no início do turno do token na iniciativa e o marcador some ao chegar a zero. |
| `POST` | `/live/scenes/{sceneId}/templates` | Área de efeito (`shape` CIRCLE/CONE/LINE/SQUARE, `x`, `y`, `angle`, `sizeFt`). Mestre ou jogador. |
| `DELETE` | `/live/templates/{templateId}` · `/live/scenes/{sceneId}/templates` | Tira uma área (quem pôs ou o mestre) / todas (mestre). |
| `POST` | `/live/templates/{templateId}/resolve` | Todos dentro da área (com linha livre até a origem) fazem a salvaguarda; o dano é rolado uma vez e cada um leva inteiro, metade ou nada. `attackerTokenId` + `attackId` de uma ação de salvaguarda do monstro preenchem CD, atributo e dano. |
| `GET` | `/monster?campaignId=` | Com `campaignId`, a busca traz também os monstros customizados da campanha (só pra quem é dela). |
| `POST` | `/monster/custom` | Ficha de monstro customizada (em branco ou preenchida) na campanha. Só o mestre. As ações (`actions`: ATTACK/SAVE/OTHER) já funcionam no token. |
| `PUT` / `DELETE` | `/monster/{id}` | Edita / apaga um monstro customizado (os do catálogo não se editam). |
| `POST` | `/monster/{id}/copy` | Copia um monstro (do catálogo ou da campanha) para os customizados da campanha, pra ajustar. |
| `GET` | `/live/campaign/{id}/initiative` | Ordem de iniciativa. |
| `POST` | `/live/campaign/{id}/initiative/roll` | Rola iniciativa dos tokens (ou de todos os visíveis da cena ativa). |
| `POST` / `DELETE` | `/live/campaign/{id}/initiative/entries[/{entryId}]` | Entrada manual (ex.: ação de covil). |
| `POST` | `/live/campaign/{id}/initiative/next` | Passa a vez (depois do último, nova rodada). |
| `DELETE` | `/live/campaign/{id}/initiative` | Fim do combate. |

## Atualização de itens por dump JSON

Os dois aceitam o arquivo de itens do 5etools com o array `item` e/ou `baseitem` (armas e armaduras comuns; lidas primeiro). Preços:

* **Preço oficial:** o do arquivo (`value`, em pc, ou `priceGp`). Raridade `none` = item comum (não mágico).
* **Item mágico sem preço:** a tabela "Magic Item Values" do *Dungeon Master's Guide* (2024): Common 100 po, Uncommon 400, Rare 4.000, Very Rare 40.000, Legendary 200.000; artefato fica sem preço. Consumível (poção, pergaminho, munição, explosivo) vale a metade, menos o *Spell Scroll*, que vale o dobro do custo de escrever (tabela do *Player's Handbook* 2024). Arma ou armadura mágica soma o preço do item de base (`baseItem`). Na atualização, o preço oficial do arquivo manda e a tabela só preenche quem ainda não tem preço.
* **Item comum sem preço:** fica sem; o mestre dá o preço dele na campanha (aba Comércio, "Preços do mestre").

**Fontes:** qualquer fonte cadastrada no banco entra na importação (antes só XPHB, EFA, RHW, XDMG, FRAIF e AU). A fonte homebrew de campanha (`HB-...`) fica de fora: item do mestre nasce pela tela dele. Raridade `unknown` / `unknown (magic)` entra sem preço (o mestre define); `varies` (famílias de itens) continua de fora.

**Atualizar pelo 5etools** (botão "Atualizar do 5etools" na aba Comércio, só o mestre):

1. Lê `books.json` e `adventures.json` do 5etools: cada livro/aventura vira (ou atualiza) uma fonte com o nome completo e `official` (autor da Wizards: "Wizards RPG Team" / "Wizards of the Coast").
2. Lê `items-base.json` e `items.json` e cria ou atualiza os itens (preço oficial, valor do DMG 2024, categoria). Sem "incluir não oficiais", itens de parceiros, terceiros e fontes que não estão na lista ficam de fora (e a fonte nem é cadastrada).
3. Os itens do mestre não são tocados. O conteúdo vai só pro banco.

O endereço é `fivetools.data-url` (padrão: o `data/` do repositório público `5etools-mirror-3/5etools-src` no GitHub). Sem internet, aponte pra uma pasta `data/` local: `--fivetools.data-url=file:///C:/caminho/5etools/data/`.

**Itens do mestre:** ficam na fonte homebrew da campanha, ao lado dos oficiais, só naquela campanha (a busca geral `/item/search` e as outras campanhas não veem; loja e espólio de outra campanha recusam). Vão na exportação da campanha e voltam como itens da campanha nova.

Categoria (`ItemCategory`), pelo código de tipo do 5etools:

| Categoria | Tipos do 5etools |
|---|---|
| `POISONS` | qualquer item com `poison` |
| `MOUNTS_VEHICLES` | `MNT`, `VEH`, `SHP`, `AIR`, `SPC`, `TAH` (montarias, veículos, navios, arreios) |
| `AMMUNITION_EXPLOSIVES` | `A`, `AF`, `EXP` |
| `WEAPON` | `M`, `R` ou com `dmg1` |
| `ARMOR` | `LA`, `MA`, `HA`, `S` |
| `CONSUMABLE` | `P`, `SC`, `FD` (poções, pergaminhos, comida e bebida) |
| `TOOLS` | `T`, `AT`, `INS`, `GS` (ferramentas, de artesão, instrumentos, jogos) |
| `TRINKETS` | bugigangas (`trinket`) e tesouro: `$G` gemas, `$A` objetos de arte, `TB` barras |
| `ADVENTURING_GEAR` | `G`, `SCF` (foco, mesmo mágico), `TG` mercadorias, `OTH` |
| `WONDROUS_ITEM` | `wondrous`, anéis `RG`, bastões `RD`, varinhas `WD` e mágicos sem tipo |

Para corrigir a categoria de itens já importados, mande o mesmo arquivo em `POST /item/update-from-dump`.

`POST /item/update-from-dump` recebe um objeto com um array `item`. Cada item é identificado por `name` e `source`: se já existir, é atualizado; caso contrário, é criado. Itens inválidos, fontes não habilitadas ou fontes ainda não cadastradas são retornados em `skipped`.

```json
{
  "item": [
    {
      "name": "Exemplo de item",
      "source": "XPHB",
      "rarity": "uncommon",
      "wondrous": true,
      "referenceLink": "https://example.com/item",
      "priceGp": 50
    }
  ]
}
```

`referenceLink` (ou `link`) e `priceGp` (ou `value`, em moedas de cobre) são opcionais; quando ausentes, os valores existentes são preservados. A resposta contém listas `created`, `updated` e `skipped`. Cada item criado ou atualizado inclui `id`, `name` e `source`; cada item ignorado inclui `name`, `source` e o motivo.

O endpoint é implementado por `ItemController.updateFromDump(JsonNode)`, que delega a `ItemService.updateFromDump(JsonNode)`. O serviço valida o dump e processa cada entrada; `updateOne(...)` faz o upsert por nome e fonte. O resultado é representado por `UpdateItemsFromDumpResponse`, com os registros `ProcessedItem` e `SkippedItem`.

## Magias e conjuração

`Spell` representa uma magia do catálogo. `DndClass` mantém a lista `availableSpells` permitida para a classe; cada `CharacterClass` da ficha mantém sua própria lista editável `preparedSpells`, que só aceita magias dessa lista. `prepareSpell` adiciona uma escolha e `unprepareSpell` permite removê-la.

Os espaços são calculados, não armazenados: `Sheet.getSpellSlots()` usa `SpellSlotCalculator` para combinar níveis de conjuradores completos, metade e um terço (arredondados para baixo) e calcular separadamente os espaços de Pact Magic. Cada resultado `SpellSlotCount` indica o conjunto (`STANDARD` ou `PACT`), nível da magia e quantidade total. A lista de preparadas representa as escolhas atuais do jogador; não representa espaços gastos. O limite de magias preparadas por classe ainda não é calculado automaticamente.

`Spell` tem os campos obrigatórios `description` e `duration`. `duration` é um objeto com `amount`, `unit` e `concentration`: use `amount: 0` com `unit: "INSTANTANEOUS"` para duração instantânea; unidades temporizadas são `"ROUND"`, `"MINUTE"`, `"HOUR"` e `"DAY"` com valor positivo. Para concentração sem limite temporal, use `"CONCENTRATION"` com valor `0`; para concentração limitada, use uma unidade temporizada e `concentration: true`.

```json
{
  "name": "Exemplo de magia",
  "sourceId": "00000000-0000-0000-0000-000000000001",
  "spellSchool": "Evocation",
  "level": "FIRST",
  "castingTime": "Action",
  "rangeFt": 60,
  "components": ["Visual", "Somatic"],
  "duration": {
    "amount": 1,
    "unit": "MINUTE",
    "concentration": true
  },
  "description": "Descrição da magia."
}
```

`SpellController` expõe o CRUD do catálogo por `/spell`: criação e atualização recebem `name`, `sourceId`, `spellSchool`, `level`, `castingTime`, `rangeFt`, `components`, `duration` e `description` em JSON. A pesquisa recebe `name` como parâmetro. O serviço valida a fonte e a unicidade de nome por fonte; a exclusão retorna conflito se a magia estiver permitida por uma classe ou preparada em uma ficha.

# Mesclagem com a mesa (antigo Embertale)

A parte da mesa de jogo, desenvolvida à parte em JavaFX, foi trazida para cá no padrão deste projeto: entidades JPA em `model/`, serviços com checagem de mestre/membro, DTOs `record` e testes com H2. O que entrou:

* **Ficha:** a troca de INT/SAB ao salvar atributos foi corrigida (o construtor de `Attributes` recebia SAB antes de INT). Agora a ficha tem PV (máximo, atual, temporário), CA e deslocamento, além de valores derivados (nível total, proficiência, iniciativa, percepção passiva e PV sugerido pelas classes). Dano e cura seguem as regras de 5e.
* **Importação de personagem:** aceita o ator do Foundry VTT (dnd5e), JSON do D&D Beyond e PDF preenchível (ficha oficial e "Exportar para PDF" do D&D Beyond). Quando o arquivo não traz PV e CA, eles são calculados como o Foundry faz. As classes são ligadas pelo nome às já importadas.
* **Dados e chat:** `DiceRoller` (`NdX`, `kh`/`kl`, críticos, `doubleDice`) e chat por campanha com rolagens no meio do texto feitas pelo servidor (não dá para forjar), `/r`, `/me` e sussurros.
* **Mundo:** mundo, país, cidade e ponto de interesse, como no diagrama, com coordenadas relativas (0 a 1) ao mapa. Inclui importação do Azgaar's Fantasy Map Generator e link para o gerador de cidades do Watabou.
* **Enciclopédia:** entradas com visibilidade e referência a NPC, país, cidade ou ponto.
* **Imagens:** guardadas no banco (`bytea`) sem duplicar, com o tipo conferido pelo conteúdo do arquivo.
* **Conteúdo 5etools:** espécies, antecedentes, talentos, magias e monstros, com o texto já legível (inclusive as marcações de 2024, como `{@actSave con}`, que vira "Constitution Saving Throw:"). Conferido com os arquivos completos: 391 magias do PHB 2024 e 503 monstros do MM 2024, nenhum ignorado.
* **Magias:** a importação usa o `Spell` dela. O que não cabe nos campos vai no começo da descrição: alcance que não é em pés, área, componente material, ritual, gatilho de reação. Para isso foram acrescentados valores aos enums (nenhum removido): `Time` ganhou `TenMinutes`, `OneHour`, `EightHours`, `TwelveHours` e `TwentyFourHours`; `DurationUnit` ganhou `SPECIAL`. `UNTIL_DISPELLED` e `SPECIAL` passaram a usar quantidade 0, como a instantânea.
* **Monstros:** catálogo `Monster` (ficha completa, XP e proficiência pelo ND). O NPC pode usar uma ficha de monstro (`statBlock`).
* **`MasterGuard`:** as regras de acesso do diagrama, usadas pelos serviços novos.
* **Party e inventário:** `maxSize` na party e controladores de inventário e NPC (os serviços já existiam).

**Segunda rodada:**

* **Ficha completa:**
  * espécie e antecedente do catálogo;
  * perícias, especialização e salvaguardas, com os bônus calculados (a percepção passiva usa a perícia);
  * condições e exaustão pelas regras de 2024: −2 por nível nos testes de d20 e −5 pés no deslocamento; o nível 6 mata;
  * testes contra a morte (1 natural vale duas falhas, 20 volta com 1 PV);
  * espaços de magia gastos, usando o `SpellSlotCalculator` dela;
  * descansos curto e longo, com dados de vida;
  * ataques que rolam acerto e dano, talentos do catálogo, XP e retrato.

  As rolagens aplicam as condições sozinhas: envenenado ou amedrontado dá desvantagem, e paralisado falha nas salvaguardas de FOR e DES. Também podem ir direto para o chat.
* **Moedas:** `Currency` (pl, po, pe, pp, pc) com conversão. O inventário continua guardando em po e mostra a quebra em moedas.
* **Exportar/importar campanha:** gera um JSON com mundo, enciclopédia, NPCs, personagens, parties, inventários e imagens. A importação cria sempre uma campanha nova, religa o conteúdo do catálogo por nome + fonte e avisa o que não encontrou.
* **Mesa ao vivo no servidor:**
  * cenas com mapa, grade quadrada, hexagonal ou sem grade, e neblina por casa;
  * tokens ligados a personagem, NPC ou monstro, com distância em pés ao mover;
  * iniciativa com rodadas.

  As mudanças chegam aos navegadores conectados por Server-Sent Events. Token oculto vai só para o mestre, sussurro vai só para quem pode ler, e o jogador só move o token do próprio personagem. Para o envio contínuo funcionar, o `SecurityConfig` passou a liberar o despacho assíncrono (`DispatcherType.ASYNC`); a autorização acontece na requisição original.

**Terceira rodada:**

* **Tela de jogo em `/mesa`:** login, escolha da campanha, cena com o mapa, grade quadrada ou hexagonal, neblina pintada por casa, tokens arrastáveis com régua em pés, iniciativa e chat. Tudo atualiza ao vivo pela transmissão e tudo passa pela API. O jogador só move os próprios tokens e não vê o que está oculto.
* **Script `db/manual-migrations/20261117_swap_intelligence_wisdom.sql`:** destroca INT e SAB das fichas já gravadas. Ver `MESCLAGEM.md` para saber quando rodar.
* **Pré-requisitos dos talentos** (nível, atributo, espécie, outro talento, conjuração) conferidos ao escolher. O mestre pode liberar.
* **Alinhamento ao padrão do `ClassService`:**
  * espécies, antecedentes, talentos e monstros guardam `description` crua (com as tags `{@...}`), `metadata` (entrada original), `sourceCode` e `page`, e têm `descriptionText` já legível;
  * os DTOs ficaram um por arquivo.
* **Resumo para revisão:** `MESCLAGEM.md`.

**Quarta rodada:**

* **Traços de espécie e características de antecedente viram `Feature`,** como as features de classe do `ClassService`:
  * categoria `species` / `background`;
  * `CHOICE` quando têm opções (a mesma regra do `hasChoiceOptions`);
  * descrição crua com as tags e `metadata` com a entrada original.
* **Lista da ficha:** reúne tudo o que o personagem tem e as escolhas que a classe oferece. O que estiver disponível é adicionado pela rota que já existia (`/character/{id}/classes/{ccId}/feats`). Qualquer item da lista pode ir pro chat como cartão.
* **Magias:** preparar e tirar uma magia na classe, usando o `prepareSpell` existente.
* **Gerador de atributos:** dados configuráveis (com a rolagem registrada no chat), array padrão e compra de pontos.
* **Tela `/mesa`:** ganhou a aba **Ficha**, com PV, CA, nível e atributos, o gerador com botão de gravar na ficha, e a lista com os botões "Adicionar" e "Chat".

**Quinta rodada (combate na mesa):**

* **Ataque de token em token:** o personagem usa os ataques da ficha; o monstro, as ações da ficha de monstro (bônus e dano lidos da entrada original do 5etools; dano que só vale "se..." fica de fora).
* **Regras aplicadas sozinhas:**
  * a CA do alvo decide se acerta;
  * condições de quem ataca e do alvo dão vantagem ou desvantagem (cego, caído, contido, invisível, inconsciente...);
  * o crítico dobra os dados;
  * resistência, imunidade e vulnerabilidade do monstro mudam o dano.
* **Na mesa ao vivo:** o PV sai do alvo e todos veem na hora. Mudanças de PV, condições e descanso feitas pela ficha também redesenham os tokens.
* **O que cada um vê:** condições aparecem pra todos; PV e CA de monstro, só o mestre. Jogador só age com o token do próprio personagem e não mira token oculto; monstro oculto atacando aparece no chat como "Unseen creature".
* **Tela `/mesa`:** barra de PV e marcas de condição nos tokens, e um painel de combate (dano/cura, condições, ataques com "clique no alvo").

**Sexta rodada (monstros jogáveis, paredes e visão):**

* **Monstros customizados:** o mestre cria uma ficha de monstro em branco ou copia uma do catálogo para editar.
  * Ficam na campanha (fonte própria `HB-xxxxxxxx`); só quem é da campanha vê.
  * As ações digitadas (ataque, salvaguarda ou só texto) são gravadas no mesmo formato do 5etools, então o token ataca com elas igual aos monstros importados.
* **Testes do monstro pelo token:** atributo, salvaguarda, perícia e iniciativa pela ficha de monstro, com as condições contando.
* **Paredes e portas:** o mestre desenha na tela; o jogador não atravessa parede nem porta fechada (o servidor confere); porta abre com um clique, trancada só o mestre abre.
* **Visão dinâmica:** ligada por cena. Cada jogador só vê o que os tokens dele enxergam (as paredes cortam a visão, e cada token pode ter um alcance em pés); o que já viu fica esmaecido. A linha de visão é calculada na tela, com as paredes que o servidor manda.

**Sétima rodada (áreas, marcadores, altura, camadas e caravana):**

* **Áreas de efeito:** círculo, cone (do jeito do livro: a largura é igual à distância), linha e quadrado.
  * Todos dentro fazem a salvaguarda: personagem pela ficha, monstro pela ficha de monstro. O dano é rolado uma vez e cada um leva inteiro, metade ou nada, com resistência e imunidade contando.
  * Parede ou porta fechada entre a origem e o alvo é cobertura total: a área não chega.
  * A ação de salvaguarda do monstro (ex.: sopro) vira área com um clique: forma e tamanho saem da descrição da ação, e CD, atributo e dano, da ficha.
* **Marcadores:** concentração, abençoado, em chamas etc., com ícone e duração em rodadas que desce sozinha na iniciativa. As condições ganharam ícones.
* **Altura:** cada token tem altura em pés; no ataque com altura diferente, a distância real vai na nota.
* **Camadas:** cenário (objetos debaixo de tudo), batalha, acima (telhado, copa) e mestre (só ele vê). O mestre escolhe a camada em que clica; objeto de cenário não entra na iniciativa.
* **Token de grupo:** um token pode levar várias fichas (a caravana, um barco). O dono de qualquer uma delas move o token e enxerga por ele, e o painel mostra o PV de cada ficha.

**Oitava rodada (tamanhos, objetos e ícones):**

* **Tamanho do token:** largura × altura em casas, de meia em meia.
  * Padrões do livro: Miúdo ½, Pequeno e Médio 1, Grande 2×2, Enorme 3×3, Imenso 4×4 ou maior (até 30).
  * Token de monstro já entra no tamanho da ficha.
* **Objetos:** cada campanha tem uma biblioteca de imagens de objetos (mesa, barril, árvore...).
  * A tela importa várias imagens de uma vez; o tamanho inicial sai da proporção da imagem (200×100 vira 2×1).
  * Posto no mapa, o objeto vira um token da camada de cenário, com a imagem, o tamanho e o giro.
* **Pacote de ícones da campanha:** o mestre importa um pacote de marcadores (ex.: os "D&D Light Status Markers").
  * O nome do arquivo decide o que é: Poisoned.png troca o ícone de envenenado; Blessed, On-Fire, Concentrating... viram marcadores prontos, com nome em português.
  * As imagens ficam no banco, junto da campanha. Pacote de terceiros não entra no repositório.
* **Ícones das condições:** desenho próprio em preto e branco (`static/mesa/icons/conditions`), um para cada uma das 15 condições de 2024, incluindo exaustão, que aparece no token com o nível.

**Nona rodada (exportação completa, luz e cobertura):**

* **Exportar/importar a campanha inteira** (`/campaign/{id}/export`, formato 2):
  * leva cenas (mapa, grade, neblina, visão, luz), paredes e portas, áreas e tokens com tudo (camada, tamanho, altura, condições, marcadores, luz, fichas da caravana);
  * leva também os monstros customizados, a biblioteca de objetos e o pacote de ícones;
  * exportado sem imagens e importado no mesmo servidor, as imagens são copiadas da campanha original (se quem importa tem acesso a ela);
  * a tela `/mesa` tem os botões de exportar e importar na aba Mestre.
* **Luz:** cena clara, em penumbra ou escura; tokens com luz forte e fraca (vela, tocha, lâmpada, lanterna) e visão no escuro; área de escuridão mágica, onde nada é visto. A luz também respeita as paredes.
* **Cobertura** pelas paredes, pela regra do livro do mestre (linhas de um canto de quem ataca até os cantos do alvo):
  * meia (+2 na CA) e três quartos (+5) entram no ataque;
  * cobertura total impede o ataque;
  * na área de efeito, a cobertura soma na salvaguarda de DES.

**Décima rodada (dano no chat e concentração):**

* **Botões de dano no chat:** toda rolagem de dano (o dano de um ataque, ou uma rolagem solta como `/r 8d6`) ganha os botões "−N", "½", "×2" e "+N cura", aplicados no token selecionado no mapa. Testes de d20 e ataques que erraram não ganham botão.
* **Concentração automática** (regra de 2024): quem está com o marcador de concentração e leva dano faz uma salvaguarda de CON contra CD 10 ou metade do dano (o maior, até 30), pela ficha ou pela ficha de monstro.
  * Falhou: o marcador sai sozinho.
  * Caiu a 0 PV ou ficou incapacitado: perde a concentração sem rolar.
  * Vale pra qualquer dano: ataque, área, painel ou ficha. O resultado vai pro chat e pras notas do ataque.

**Régua e alcance:**

* **Régua:** a ferramenta "Régua" marca pontos de casa em casa, mostra cada trecho e o total em pés, e todos da mesa veem quem está medindo. Duplo clique ou Esc termina.
* **Movimento colorido:** ao arrastar, o caminho fica verde até o deslocamento do token (da ficha ou da ficha de monstro), amarelo até o dobro (disparada) e vermelho além disso.
* **Auras:** cada token pode ter até 4 auras com raio, cor e nome (a do paladino a 10 pés...), visíveis pra todos; vão na exportação.
* **Alcance do ataque:** ao escolher um ataque, aparece o anel do alcance (normal e longo, lido do texto da ação) e a distância até o ponteiro, considerando a altura: verde no alcance, amarelo no longo (desvantagem), vermelho fora.

**Alfinetes e ping:**

* **Alfinetes:** a ferramenta "Alfinete" (mestre) põe um marcador no mapa com ícone, nome, nota e, se quiser, um verbete da enciclopédia ligado. Clicando, abre a nota e o texto do verbete (o jogador só vê verbete visível). Pode ficar só pro mestre; o mestre arrasta pra mover. Vai na exportação, ligado ao verbete pelo nome.
* **Ping:** segurar o clique no mapa (ou Alt+clique) solta ondas no ponto pra todos, com o nome de quem pingou. Shift+Alt+clique do mestre também centraliza a tela de todos ali.

**Modo telão (jogo presencial):** o botão "Telão" (ou `/mesa/?telao=1`) deixa só o mapa, em tela cheia, pra uma TV na mesa. Aberto pelo mestre, mostra a visão do grupo (o que os personagens de todos os jogadores enxergam juntos, com luz, paredes e neblina, sem nada que é só do mestre) e segue sozinho a cena ativa; pings e o "puxar a tela" do mestre valem. Esc ou o botão no canto sai.

**Cores e vez:**

* **Cor de cada jogador:** escolhida no seletor do cabeçalho (sem escolha, uma automática que não repete as outras). Pinta o anel dos tokens dele (e da caravana em que viaja), a régua, o ping e a faixa na lista de iniciativa.
* **De quem é a vez:** o token da vez na iniciativa ganha um anel pulsante com "▶ vez"; o dono recebe o aviso "É a sua vez!"; no telão, o mapa centraliza no token da vez.

**Lojas e espólio** (aba "Comércio" na tela `/mesa`):

* **Lojas:** o mestre monta (itens do catálogo, preço próprio ou o do catálogo, estoque opcional) e abre ou fecha. O jogador compra e vende pelo personagem: o dinheiro e o inventário mudam de verdade, sem dinheiro a compra é recusada, e a loja paga a porcentagem dela ao comprar (padrão 50%, como no livro). O item vendido volta pro estoque.
* **Espólio:** baús com moedas e itens, escondidos até o mestre mostrar. O jogador pega itens pro personagem ou pro grupo; o mestre divide as moedas igualmente (o que não divide fica no baú, em pc).
* Tudo vai pro chat e vai na exportação da campanha.
* **Preços do mestre:** caixa só do mestre que lista os itens comuns sem preço oficial (e, se marcar, os mágicos sem preço, como artefatos); o mestre escreve o preço e ele vale nas lojas e na venda só desta campanha (vai na exportação).

**Ver como jogador:** o mestre escolhe "Ver como jogador de ..." na barra da mesa e vê exatamente o que aquele jogador vê (visão dinâmica, luz, neblina, memória do que já foi visto). Tokens ocultos, camada do mestre, paredes e PV de monstro somem como somem pro jogador. As ferramentas continuam de mestre, e uma faixa no alto lembra o modo, com o botão de voltar.

Ficaram de fora por não se encaixarem numa API REST sem uma interface de jogo:

* a tela do mapa no front Angular (por enquanto existe a tela simples em `/mesa`, que usa a mesma API);
* a "ponte" para jogar sem abrir porta;
* sons;
* fichas de Tormenta20 e Ordem Paranormal (este projeto é de D&D 5e).

Isso continua no programa da mesa e pode virar WebSocket mais tarde.

**Observações sobre o código que já existia (não alterado):**

* **Campanhas sem proteção (item 3 do roadmap), deixado para a autora do projeto decidir como fazer.** Estas rotas não conferem quem está pedindo; basta estar logado e saber o ID ou o código da campanha:
  * `DELETE /campaign/{id}` (`CampaignService.deleteCampaign`): qualquer usuário apaga qualquer campanha, com personagens, chat, mundo e enciclopédia junto (cascata).
  * `PATCH /campaign/update/{id}` (`updateCampaign`) e `POST /campaign/addSource` (`addSource`): qualquer usuário edita ou liga fontes na campanha dos outros.
  * `GET /campaign/{code}` (`readCampaign`): qualquer usuário vê uma campanha que não é dele.
  * `GET /character/{campaignId}/characters` (`listCampaignCharacters`): qualquer usuário lista os personagens de uma campanha da qual não participa.

  Sugestão: receber o usuário logado (`@AuthenticationPrincipal UserDetails`) nessas rotas e usar o `MasterGuard` (`service/MasterGuard.java`), que já faz isso nas partes novas. Apagar, editar e adicionar fonte ficariam com `requireMaster(username, campaign)`; ver a campanha e listar personagens ficariam com `requireMember(username, campaign)`. Quem não puder recebe 403. Vale um teste com um usuário de fora sendo bloqueado e o mestre não.
* `application.properties` expõe `management.endpoints.web.exposure.include=env`. Se o Actuator for adicionado um dia, isso mostra `JWT_SECRET` e a senha do banco.
* Os testes `ItemImportTests`, `CharacterServiceTests`, `CharacterSheetServiceTests` e `SpellcastingModelTests` não compilavam ou falhavam e foram ajustados: o `data.sql` agora cria a "Campanha de O Escriba" que o teste esperava.

# Diagrama de classes

```mermaid
classDiagram
    %% === INTERFACES ===
    class Master {
        <<interface>>
    }
    class EncyclopediaReferenceable {
        <<interface>>
        UUID getReferenceId()
        String getDisplayName()
    }

    %% === ENUMS ===
    class Role {
        <<enumeration>>
        PLAYER
        MASTER
        SPECTATOR
    }
    class SheetType {
        <<enumeration>>
        PLAYER
        NPC
        MOB
    }
    class PointType {
        <<enumeration>>
        SHOP
        INN
        BASTION
        TOURISTIC
        OTHER
    }
    class RoomSize {
        <<enumeration>>
        CRAMPED
        ROOMY
        VAST
    }
    class Currency {
        <<enumeration>>
        PLATINUM
        GOLD
        SILVER
        COPPER
    }
    class OrderStatus {
        <<enumeration>>
        MADE
        RECEIVED
    }
    class EntryType {
        <<enumeration>>
        PERSON
        PLACE
        HISTORICAL_FACT
        OTHER
    }

    %% === CONTAS E ACESSO ===
    class User {
        UUID userId
        String name
        update()
        delete()
    }
    note for User "{selfOnly} update() e delete() exigem principal.userId == self.userId"

    class Membership {
        UUID id
    }

    class Campaign {
        UUID campaignId
        String name
        String code
        CRUD()
        addSource(campaign, source)
        invite(userId, role)
        acceptInvite(campaign)
        createWorld(campaign)
    }
    
    class Invite {
        Campaign campaign,
        User user,
        Role role
    }

    User "1" <-- "1" Invite
    User "1" -- "0..*" Membership
    Membership "0..*" -- "1" Campaign
    Membership --> Role

    %% === PERSONAGENS E FICHAS ===
    class Player {
        UUID playerId
        String name
        createCharacter()
        readCharacter(playerId)
        updateCharacter(playerId)
        deleteCharacter(playerId)
        
    }

    class Character {
        UUID characterId
        String name
        Enum status
    }

    class Sheet {
        UUID sheetId,
        Character character,
        CharacterClass[] classes
        getSpellSlots()
    }

    class Attribute {
        int str
        int dex
        int con
        int int
        int wis
        int cha
        int hp
        int ac
        getModifier(String ability)
    }

    class Party {
        UUID partyId
        String name
        int maxSize
        <<masterOnly>> createParty(name)
        enterParty(partyId)
        abandonParty(partyId)
        readParty(partyId)
    }

    Party "1" *-- "2..*" Character : contém
    Character --> CharacterStatus
    class CharacterClass {
        UUID id
        int level
        Set~Spell~ preparedSpells
        prepareSpell(Spell)
        unprepareSpell(UUID spellId)
    }
    Sheet ..> SpellSlotCalculator : consulta slots
    
    class CharacterStatus {
        <<enumeration>>
        ALIVE
        DEAD
        RETIRED
    }
    
    class DndClass {
        UUID classId
        String className
        int hitDie
        int hitDieNumber
        int subclassUnlockLevel
        Source source
        String casterProgression
        String preparedSpells
        String preparedSpellsChange
        Set~Spell~ availableSpells
        addAvailableSpell(Spell)
        removeAvailableSpell(UUID spellId)
    }
    class SubclassDefinition {
        UUID id
        String name
        Source source
    }
    class FeatureDefinition {
        UUID id
        String name
        String description
        Source source
    }
    class ClassFeatureGrant {
        UUID id
        int level
    }
    class SubclassFeatureGrant {
        UUID id
        int level
    }

    Sheet "1" *-- "0..*" CharacterClass : class levels
    CharacterClass "0..*" --> "1" DndClass : uses
    CharacterClass "0..*" --> "0..1" SubclassDefinition : chooses
    DndClass "1" o-- "0..*" SubclassDefinition : offers
    DndClass "1" *-- "0..*" ClassFeatureGrant : grants
    ClassFeatureGrant "0..*" --> "1" FeatureDefinition : references
    SubclassDefinition "1" *-- "0..*" SubclassFeatureGrant : grants
    SubclassFeatureGrant "0..*" --> "1" FeatureDefinition : references
    DndClass "0..*" -- "0..*" Spell : permite
    CharacterClass "0..*" -- "0..*" Spell : preparadas

    class Spell {
        UUID spellId
        String name
        SpellSchool school
        SpellLevel level
        Time castingTime
        int rangeFt
        Set~Components~ components
        Duration duration
        String description
        Source source
    }
    class Duration {
        int amount
        DurationUnit unit
        bool concentration
    }
    class DurationUnit {
        <<enumeration>>
        INSTANTANEOUS
        ROUND
        MINUTE
        CONCENTRATION
        HOUR
        DAY
    }
    class SpellSlotCalculator {
        calculate(List~CharacterClass~) : List~SpellSlotCount~
    }
    class SpellService {
        create(String name, String school, String level, String castingTime, int rangeFt, String[] components, Duration duration, UUID sourceId, String description)
        read(UUID spellId)
        listAll()
        search(String name)
        update(UUID spellId, String name, SpellSchool school, SpellLevel level, Time castingTime, int rangeFt, Set~Components~ components, Duration duration, UUID sourceId, String description)
        delete(UUID spellId)
    }
    class SpellController {
        createSpell(SpellCreationRequest request)
        getSpell(UUID id)
        allSpells()
        search(String name)
        updateSpell(UUID id, SpellCreationRequest request)
        deleteSpell(UUID id)
    }
    class SpellCreationRequest {
        String name
        UUID sourceId
        String spellSchool
        String level
        String castingTime
        int rangeFt
        String[] components
        SpellDurationRequest duration
        String description
    }
    class SpellDurationRequest {
        int amount
        DurationUnit unit
        bool concentration
        toModel()
    }
    class SpellRepository {
        findByNameAndSource_Id(String name, UUID sourceId)
        findByNameContainingIgnoreCase(String name)
    }
    class SpellSlotCount {
        SpellSlotPool pool
        String className
        int spellLevel
        int totalSlots
    }
    class SpellcastingProgression {
        <<enumeration>>
        NONE
        FULL
        HALF
        THIRD
        PACT
    }
    class SpellSlotPool {
        <<enumeration>>
        STANDARD
        PACT
    }
    DndClass --> SpellcastingProgression
    Spell *-- Duration
    Duration --> DurationUnit
    SpellSlotCalculator ..> CharacterClass : calcula por nível
    SpellSlotCalculator ..> SpellSlotCount : retorna
    SpellService --> SpellRepository : CRUD
    SpellController --> SpellService : delega
    SpellService ..> Spell : valida e atualiza
    SpellService ..> Source : valida fonte

%% Conexões de Personagens (PCs e NPCs na campanha, Mobs soltos)
    Campaign "1" o-- "0..*" Character : possui PCs e NPCs
    User "1" -- "0..1" Player : é jogador
    Player "1" *-- "1" Character : controla
    Character "1" *-- "1" Sheet : tem ficha
    Sheet "1" *-- "1" Attribute : tem atributos
    Sheet --> SheetType

    %% === REGRAS DE D&D (5e e 5.5) ===
    class Species {
        UUID speciesId
        String name
        Size size
        String source
        Trait[] traits
    }

    class Background {
        UUID backgroundId
        String name
        String source
    }

    class AbilityScoreIncrease {
        String ability
        int amount
    }
    
    Species "1" o-- "0..*" AbilityScoreIncrease : possui (apenas 5e)
    Background "1" o-- "0..*" AbilityScoreIncrease : possui (apenas 5.5)
    
    Character "0..*" --> "1" Species
    Character "0..*" --> "1" Background

    class FeatDefinition {
        UUID id
        String name
        String description
        String category
        Source source
    }

    class CharacterFeat {
        UUID id
        int characterLevel
        String choices
    }

    class Prerequisite {
        <<interface>>
    }
    class AbilityPrerequisite {
        String ability
        int minimumScore
    }
    class FeatPrerequisite {
        UUID requiredFeatId
    }
    class FeaturePrerequisite {
        UUID requiredFeatureId
    }
    class LevelPrerequisite {
        int minimumLevel
    }
    class SpeciesPrerequisite {
        UUID requiredSpeciesId
    }

    FeatDefinition "1" *-- "0..*" Prerequisite
    Prerequisite <|.. AbilityPrerequisite
    Prerequisite <|.. FeatPrerequisite
    Prerequisite <|.. FeaturePrerequisite
    Prerequisite <|.. LevelPrerequisite
    Prerequisite <|.. SpeciesPrerequisite

    FeatPrerequisite "0..*" --> "1" FeatDefinition : requires
    FeaturePrerequisite "0..*" --> "1" FeatureDefinition : requires
    Sheet "1" *-- "0..*" CharacterFeat : records
    CharacterFeat "0..*" --> "1" FeatDefinition : selects
    Background "0..1" --> "0..1" FeatDefinition : grants a feat (5.5)

    %% === MUNDO E GEOGRAFIA ===
    class World {
        UUID id
        String name
        String mapUrl
        String masterInfo
    }

    class Country {
        UUID countryId
        String name
        String info
        String masterInfo
        String mapUrl
        float worldMapX
        float worldMapY
    }

    class City {
        UUID cityId
        String name
        String cityMapUrl
        float countryMapX
        float countryMapY
    }

    class Point {
        UUID pointId
        String name
        float cityMapX
        float cityMapY
    }

    Campaign "1" o-- "0..*" World
    World "1" o-- "1..*" Country
    Country "1" o-- "0..*" City
    City "1" o-- "0..*" Point
    Point --> PointType

    class CityConnection {
        UUID id
        float globalDistance
        float seaDistance
        bool hasTrain
        bool hasShip
        float price
    }
    City "0..*" -- "0..*" City : connection
    CityConnection .. City : «association class»
    CityConnection --> Currency

    %% === PONTOS DE INTERESSE ===
    Point <|-- Shop
    Point <|-- Inn
    Point <|-- Bastion

    class Shop {
        NPC shopkeeper
    }
    class Inn {
        NPC innkeeper
        String type
    }
    class Bastion {
        UUID bastionId
        int level
        int maxRooms
    }

    %% === NPCs ===
    class NPC {
        UUID npcId
        String name
        String photoUrl
        String personality
        String notes
        String masterInformation
    }
    Campaign "1" o-- "0..*" NPC
    NPC "1" o-- "0..1" Sheet : pode ter ficha
    Shop --> NPC
    Inn --> NPC

    %% === LOJAS E ITENS ===
    class Stock {
        UUID stockId
    }
    class Item {
        UUID id
        String name
        String source
        String rarity
        String type
        bool custom
        String category
        String link
        float price
    }
    class ItemController {
        updateFromDump(JsonNode payload)
    }
    class ItemService {
        importFromDump(JsonNode payload)
        updateFromDump(JsonNode payload)
        updateOne(JsonNode entry, String name, String sourceCode)
        firstText(JsonNode entry, String... fields)
        parsePrice(JsonNode entry)
    }
    class UpdateItemsFromDumpResponse {
        List~ProcessedItem~ created
        List~ProcessedItem~ updated
        List~SkippedItem~ skipped
    }
    class ProcessedItem {
        UUID id
        String name
        String source
    }
    class SkippedItem {
        String name
        String source
        String reason
    }
    ItemController --> ItemService : delega
    ItemService --> UpdateItemsFromDumpResponse : retorna
    UpdateItemsFromDumpResponse *-- ProcessedItem
    UpdateItemsFromDumpResponse *-- SkippedItem
    class Order {
        UUID orderId
        int quantity
        float price
    }

    Shop "1" *-- "1" Stock
    Shop "1" o-- "0..*" Order
    Stock "1" o-- "0..*" Item : contém
    Order "0..*" --> "1" Item
    Order "0..*" --> "1" Character
    Order --> OrderStatus

    %% === INVENTÁRIO ===
    class Inventory {
        int platinum
        int gold
        int silver
        int copper
    }
    class InventoryEntry {
        Item item
        int quantity
    }
    Character "1" *-- "1" Inventory
    Inventory "1" *-- "0..*" InventoryEntry
    InventoryEntry --> Item

    %% === BASTION E SALAS ===
    Character "1" *-- "0..1" Bastion
    Bastion "1" *-- "1..*" Room
    class Room {
        UUID roomId
        String name
    }
    class RoomType {
        UUID roomTypeId
        String name
        String description
    }
    Room "0..*" --> "1" RoomType
    Room --> RoomSize
    RoomType --> RoomSize

    %% === GERENCIAMENTO DE FONTES ===
    class Source {
        UUID sourceId
        String name
        String code
        int year
    }
    class CampaignSourceConfig {
        bool enabled
    }
    Campaign "1" *-- "0..*" CampaignSourceConfig
    CampaignSourceConfig "0..*" --> "1" Source
    Item "0..*" --> "1" Source
    Spell "0..*" --> "1" Source
    RoomType "0..*" --> "1" Source

    %% === ENCICLOPÉDIA ===
    class Encyclopedia {
        UUID id
    }
    class EncyclopediaEntry {
        UUID id
        String entryName
        String information
        bool visible
    }
    Campaign "1" *-- "1" Encyclopedia
    Encyclopedia "1" *-- "0..*" EncyclopediaEntry
    EncyclopediaEntry --> EntryType
    
    %% Referências da Enciclopédia
    EncyclopediaEntry "1" --> "0..1" EncyclopediaReferenceable : references
    NPC ..|> EncyclopediaReferenceable
    City ..|> EncyclopediaReferenceable
    Country ..|> EncyclopediaReferenceable
    Point ..|> EncyclopediaReferenceable

    %% === SEGURANÇA E CAPACIDADES ===
    class MasterGuard {
        assertCanManage(User user, Campaign campaign)
        boolean canManage(User user, Campaign campaign)
    }
    MasterGuard --> Membership : verifica role
    MasterGuard --> Master : valida alvo

    Campaign ..|> Master
    Country ..|> Master
    City ..|> Master
    Shop ..|> Master
    NPC ..|> Master
    Item ..|> Master
    Player ..|> Master

`````