

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
        invite(userId)
        acceptInvite()
        assignRole(userId)
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
    }

    class Character {
        UUID characterId
        String name
    }

    class Sheet {
        UUID sheetId
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
    }

    Party "1" *-- "2..*" Character : contém

    class CharacterClass {
        UUID id
        int level
    }
    class DndClass {
        String key
        String name
        int hitDie
        String primaryAbility
    }
    class Subclass {
        String key
        String name
    }
    class ClassFeature {
        String key
        String name
        String description
        FeatureType type
    }
    class FeatureProgression {
        UUID id
        int level
        String detail
    }

    Character "1" *-- "0..*" CharacterClass : possui
    CharacterClass "0..*" --> "1" DndClass : estuda
    CharacterClass "1*" --> "1" Subclass : escolhe
    DndClass "1" *-- "0..*" ClassFeature : define
    DndClass "1" o-- "0..*" Subclass : possui
    Subclass "1" *-- "0..*" ClassFeature : define
    ClassFeature "1" *-- "1..*" FeatureProgression : escala em

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

    class Feat {
        UUID featId
        String name
        String source
        String category
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
    class LevelPrerequisite {
        int minimumLevel
    }
    class SpeciesPrerequisite {
        UUID requiredSpeciesId
    }

    Feat "1" *-- "0..*" Prerequisite
    Prerequisite <|.. AbilityPrerequisite
    Prerequisite <|.. FeatPrerequisite
    Prerequisite <|.. LevelPrerequisite
    Prerequisite <|.. SpeciesPrerequisite

    Sheet "0..*" --> "0..*" Feat : possui
    Background "0..1" --> "0..1" Feat : dá um Feat (5.5)

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
    Order "0..*" --> "1" Player
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
    Player "1" *-- "1" Inventory
    Inventory "1" *-- "0..*" InventoryEntry
    InventoryEntry --> Item

    %% === BASTION E SALAS ===
    Player "1" *-- "0..1" Bastion
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