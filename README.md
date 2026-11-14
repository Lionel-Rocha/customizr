

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

# TO-DO / Roadmap

A ordem prioriza uma primeira versão jogável e segura; depois, os recursos avançados de criação de mundo.

## P0 — Completar e estabilizar o núcleo

1. [ ] Expor as funcionalidades que já têm serviço/modelo, mas ainda não têm API completa.
   - [ ] Implementar `ItemController` para criar, consultar, pesquisar, atualizar e excluir itens.
   - [ ] Criar endpoints para inventários de personagens, parties e NPCs: consultar, adicionar/remover itens e atualizar dinheiro.
   - [ ] Criar endpoints para NPCs: listar por campanha, consultar, atualizar e excluir.
2. [ ] Completar a experiência de party.
   - [ ] Consultar party e seus membros; respeitar `maxSize` e as regras de campanha.
   - [ ] Consultar e usar o inventário compartilhado da party pela API e pela interface.
3. [ ] Fechar regras de acesso e testes dos fluxos principais.
   - [ ] Garantir que apenas membros da campanha acessem seus dados e que ações de mestre sejam exclusivas do mestre.
   - [ ] Cobrir com testes os fluxos de campanha, convite, personagem, party, inventário e importação, incluindo erros e permissões.
   - [ ] Padronizar respostas de erro e validação dos endpoints.
4. [ ] Completar e corrigir a ficha do personagem.
   - [ ] Corrigir e testar o mapeamento de Sabedoria e Inteligência ao atualizar atributos.
   - [ ] Adicionar pontos de vida, classe de armadura e demais valores derivados necessários à ficha.
   - [ ] Adicionar imagem de perfil para personagem e NPC, com armazenamento de imagens em vez de depender apenas de URLs externas.

## P1 — Tornar personagens e conteúdo jogáveis

5. [ ] Importar conteúdo de jogo por JSON.
   - [ ] Importar itens, validando campos e fonte, e retornar um relatório de itens importados, ignorados e inválidos.
   - [ ] Permitir importar e atualizar conteúdo sem criar duplicatas.
   - [ ] Expandir a importação para espécies, antecedentes, magias, talentos e monstros.
6. [ ] Completar a criação e progressão de personagens.
   - [ ] Implementar espécies e antecedentes, incluindo características e aumentos de atributos.
   - [ ] Implementar progressão de nível, pontos de vida e benefícios recebidos por nível.
   - [ ] Implementar magias: catálogo, listas por classe, espaços, conhecidas/preparadas e seleção na ficha.
   - [ ] Implementar talentos e seus pré-requisitos de atributos, nível, espécie, talento e característica.
7. [ ] Completar NPCs e monstros.
   - [ ] Criar fichas de NPC e monstro, com estatísticas, características, imagem e inventário opcional.
   - [ ] Controlar quais informações do NPC ficam visíveis para jogadores e quais são exclusivas do mestre.

## P2 — Ferramentas de criação de mundo

8. [ ] Implementar a hierarquia de mundo: mundo, país, cidade e pontos de interesse, com operações de criação, consulta, atualização e exclusão.
   - [ ] Adicionar mapas, coordenadas e pins para países, cidades e pontos de interesse.
   - [ ] Implementar conexões entre cidades com distâncias, meios de transporte, duração e custo.
9. [ ] Implementar a enciclopédia da campanha.
   - [ ] Criar, editar, consultar, pesquisar e ocultar/mostrar entradas.
   - [ ] Vincular entradas a NPCs, cidades, países e pontos de interesse.
10. [ ] Implementar lojas e economia.
    - [ ] Criar lojas com estoque e permitir compra e venda de itens.
    - [ ] Criar encomendas de itens, estados do pedido e módulo de entrega.
    - [ ] Suportar moedas de platina, ouro, prata e cobre, além de conversões e preços.
11. [ ] Implementar bastiões e salas.
    - [ ] Criar bastiões de personagens, com nível, limite de salas e tipos de sala.
    - [ ] Aplicar requisitos, tamanhos e efeitos de cada tipo de sala.

## P3 — Recursos adicionais e acabamento

12. [ ] Implementar chat por campanha, com histórico e acesso restrito aos participantes.
13. [ ] Completar a interface web para campanhas, personagens, parties, inventários, mapas, enciclopédia e lojas.
    - [ ] Tornar a interface responsiva e acessível.
    - [ ] Adicionar buscas, filtros e paginação onde houver listas grandes.
14. [ ] Sincronizar README, diagrama de classes e tabela de rotas com o comportamento real da aplicação.
15. [ ] Preparar a aplicação para uso contínuo.
    - [ ] Adicionar métricas e logs para localizar lentidão e erros.
    - [ ] Revisar consultas para evitar N+1 e adicionar paginação/cache quando necessário.
    - [ ] Definir migrações de banco, backups e exportação/importação de campanhas.

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
| `POST` | `/party/campaign/{campaignId}` | Cria uma party dentro da campanha. |
| `PUT` | `/party/{partyId}/characters/{characterId}` | Adiciona um personagem à party. |
| `DELETE` | `/party/{partyId}/characters/{characterId}` | Remove um personagem da party. |
| `POST` | `/source/create` | Cria uma fonte de conteúdo. |
| `GET` | `/source/{code}` | Consulta uma fonte pelo código. |
| `GET` | `/source/all` | Lista todas as fontes disponíveis. |

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
    }
    
    class CharacterStatus {
        <<enumeration>>
        ALIVE
        DEAD
        RETIRED
    }
    
    class ClassDefinition {
        UUID id
        String name
        int hitDie
        int subclassUnlockLevel
        Source source
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
    CharacterClass "0..*" --> "1" ClassDefinition : uses
    CharacterClass "0..*" --> "0..1" SubclassDefinition : chooses
    ClassDefinition "1" o-- "0..*" SubclassDefinition : offers
    ClassDefinition "1" *-- "0..*" ClassFeatureGrant : grants
    ClassFeatureGrant "0..*" --> "1" FeatureDefinition : references
    SubclassDefinition "1" *-- "0..*" SubclassFeatureGrant : grants
    SubclassFeatureGrant "0..*" --> "1" FeatureDefinition : references

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