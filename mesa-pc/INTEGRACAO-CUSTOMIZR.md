# Customizr x nossa parte (Embertale) — o que vai pra onde

Estudo do repositório https://github.com/Lionel-Rocha/customizr (commit `e740c23`, 16/11/2026).
Nada foi enviado pra lá; isto é só o mapa pra decidir juntos.

## 1. O que cada um é hoje

| | **Customizr** (repositório dela) | **Embertale** (nossa parte, esta pasta) |
|---|---|---|
| Ideia | Criação de mundo + campanha: mundo, países, cidades, NPCs, enciclopédia, economia, party | Mesa de jogo: mapa, tokens, neblina, visão, dados, chat, fichas em uso |
| Forma | Servidor web (API REST) + front Angular (repositório do front não é público) | Programa de PC (JavaFX) + jogadores pelo navegador |
| Tecnologia | Java 27, Spring Boot 4.1, JPA, PostgreSQL, login com JWT | Java 25+, JavaFX 27, Gson, PDFBox; dados em arquivos JSON por mesa |
| Contas | Usuários com senha, papéis (jogador/mestre/espectador), convite por código | Sem conta: nome + senha da mesa, código da mesa na ponte |
| Pronto | Campanhas (CRUD + convite), personagens, classes/subclasses (import 5etools), itens (import/dump com preço), magias (CRUD + cálculo de espaços multiclasse), party, fontes por campanha | Praticamente tudo de mesa (ver README), importadores de ficha e mapa, compêndio 5etools, T20/Ordem |
| Planejado (roadmap dela) | Mundo/país/cidade/ponto com mapas e pins, enciclopédia, lojas/encomendas, bastiões, chat, NPC com ficha, espécies/antecedentes/talentos/monstros | — |

**Nome:** o projeto se chama **Customizr** (README e `spring.application.name: Customizr API`).
"Embertale" era o nome que eu tinha posto; a renomeação da nossa parte está na seção 5.

## 2. Encaixe proposto

Customizr é o **projeto inteiro**. Ele tem duas metades que conversam:

- **Customizr (servidor, dela)** — a "biblioteca da campanha": contas, campanhas, catálogo
  (classes, itens, magias, fontes), mundo, enciclopédia, economia, bastiões. Fica online.
- **Customizr Mesa (nossa parte)** — onde a sessão acontece: mapa, tokens, neblina, dados, chat,
  fichas em jogo, jogadores pelo navegador. Puxa da API dela o que precisa e devolve o que mudou
  (PV, inventário, XP).

Assim nada se joga fora: o roadmap dela fica no servidor, e o que é tempo real fica na mesa.

## 3. O que da nossa parte já cobre itens do roadmap dela

| Item do roadmap dela | O que já temos | Como alocar |
|---|---|---|
| **P2.8** mapas e pins de país/cidade/ponto | Import do **Azgaar** (reinos, cidades, capitais com coordenadas), **pins** que abrem outros mapas, links do **Watabou** pra gerar mapa de cidade/vila | O import do Azgaar pode **criar World/Country/City** no servidor dela com `worldMapX/Y` e `countryMapX/Y` já preenchidos. Os pins da mesa viram `Point` |
| **P2.9** enciclopédia com entradas ocultas | **Diário** da mesa + pins com texto, ocultos dos jogadores | O Diário passa a ler/escrever `EncyclopediaEntry` (campo `visible` = oculto ou não) |
| **P3.12** chat por campanha | Chat completo: rolagens no texto, `/me`, cartões de dano, "Rolar dano" | Fica na mesa (é tempo real). Se ela quiser histórico no servidor, a mesa manda o log no fim da sessão |
| **P0.4** PV, CA e valores derivados | Ficha calcula tudo; o import do Foundry calcula PV máx/CA/ataque | As regras de cálculo (`CharacterSheet`, `FoundrySheetReader.computeHpMax/computeAc`) podem ir pro `Sheet` dela |
| **P0.4** imagem de perfil armazenada | `ImageAssetStore` (deduplica por hash, limite de tamanho) | Mesma ideia num endpoint de upload dela |
| **P1.5** importar espécies, antecedentes, magias, talentos, monstros | Compêndio já importa tudo isso do formato **5etools** (`compendium/*Importer`) | Os dois lados usam o **mesmo formato 5etools**. Reaproveitar nossos analisadores no `ClassService`/`ItemService` dela evita escrever de novo |
| **P1.6** magias e espaços na ficha | Ficha com espaços, magias preparadas, conjurar gastando espaço | Ela já tem o `SpellSlotCalculator` multiclasse (o nosso é mais simples): **trazer o dela pra mesa** |
| **P1.7** fichas de NPC/monstro + visibilidade | `MobSheet`, cálculo de ND, monstros do compêndio, tokens ocultos | `NPC.masterInformation` = só o mestre vê; a ficha de monstro da mesa vira a `Sheet` do NPC |
| **P0.2** party e inventário compartilhado | Party + inventário com moedas na mesa | Modelos quase iguais (`Inventory` com PP/PO/PE/PC). O servidor dela vira a fonte; a mesa sincroniza |
| **P3.15** exportar/importar campanha | Backup `.zip` da mesa inteira | O formato do zip pode incluir o JSON da API pra levar de um lado pro outro |
| Docker + ngrok pra acesso externo | **Ponte** própria (jogar sem abrir porta, TLS de ponta a ponta) | Alternativa sem ngrok pra mesa; o servidor dela continua com o Docker |

**Curiosidade:** nossa pasta `content/` (`DndClass`, `Subclass`, `Feature`, `FeatureGrant`, `Source`,
`SourceConfig`) tem quase a mesma modelagem dela. Dá pra unificar os nomes sem trauma.

## 4. O que só ela tem (a mesa passaria a usar)

- Contas e login (JWT), papéis, convite por código de campanha
- Fontes habilitadas por campanha (`CampaignSourceConfig`): a mesa filtraria o compêndio por isso
- Itens com preço/raridade e o upsert por dump (`/item/update-from-dump`)
- Espaços de magia multiclasse e de Pacto
- Futuro: lojas/encomendas, bastiões, conexões entre cidades (viagem com custo e duração)

## 5. Mudanças do nosso lado (quando vocês derem o ok)

1. ~~Renomear Embertale → Customizr~~ **feito** (16/11/2026): telas, `Customizr.exe`, atalho, ponte,
   README/HANDOFF. Pendente, opcional: pacote Java `com.vttcaseiro` → `top.customizr.mesa`, pra combinar
   com o dela (`top.customizr.system`).
2. **Cliente da API dela** na mesa: login, escolher campanha, baixar personagens/itens/magias
   pro compêndio e devolver mudanças. Usaria as rotas que já existem (`/api/auth/login`,
   `/campaign/`, `/character/{id}/sheet`, `/item/all`, `/spell/all`, `/class/all`).
3. **Exportador Azgaar → mundo dela** (World/Country/City com coordenadas), quando ela criar
   esses endpoints (ainda não existem: hoje são só o diagrama).
4. Manter a mesa funcionando **sem servidor** (modo offline/local, como hoje), pra não depender
   de internet nem de conta.

## 6. Coisas que achei no código dela (pra contar pra ela; não mexi)

- **INT e SAB trocados ao salvar atributos**, que é o item "Corrigir o mapeamento de Sabedoria e
  Inteligência" do roadmap. O construtor é `Attributes(str, dex, con, wisdom, intelligence, cha)`
  (`model/Attributes.java:18`), mas `CharacterService.java:186` passa `intelligence` antes de
  `wisdom`. Resultado: quem salva INT 16 / SAB 10 fica com INT 10 / SAB 16. Correção: inverter a
  ordem dos parâmetros no construtor (ou na chamada).
- `application.properties` tem `management.endpoints.web.exposure.include=env`. Hoje não faz nada,
  porque o Actuator não está no `pom.xml`. **Se um dia for adicionado, o endpoint `/actuator/env`
  pode expor variáveis como `JWT_SECRET` e a senha do banco.** Melhor tirar essa linha.
- O padrão do `spring.datasource.url` aponta pra um banco hospedado (Railway). Rodar os testes ou
  o app sem configurar a variável tenta conectar nele. Talvez valha usar o H2 como padrão local
  (o H2 já está no `pom.xml`).

## 7. Perguntas pra decidir juntos

1. ~~Nome da nossa parte~~: decidido, **Customizr**.
2. A mesa deve **exigir conta** do servidor dela ou continuar funcionando sem conta, com o login opcional?
3. O front Angular dela vai ter tela de jogo, ou a tela de jogo no navegador é a nossa (`index.html` da mesa)?
4. Quem é a "fonte da verdade" da ficha durante a sessão: o servidor dela (a mesa salva lá a cada
   mudança) ou a mesa (sincroniza no fim)?

## 8. Mesclagem feita (16/11/2026)

O que coube no padrão dela foi para `D:\customizr-main` (backup do original: `backup-customizr-main-original.zip`
nesta pasta). Os detalhes estão na seção "Mesclagem com a mesa" do README de lá.
Foram de 23 testes, 3 deles quebrados, para 45 testes passando, e o servidor foi conferido por HTTP.
Ficaram só no programa da mesa: mapa tático (tokens, neblina, visão), ponte, sons e Tormenta20/Ordem.
