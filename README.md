

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
class Master {
<<interface>>
}

    class Campaign {
        UUID campaignId
        String name
        String code
        create()
        read()
        update()
        delete()
        «masterOnly» invite()
        acceptInvite()
        «masterOnly» createWorld()
    }

    class Role {
        <<enumeration>>
        PLAYER
        MASTER
    }

    class User {
        UUID userId
        String name
        create()
        read()
        update()
        delete()
    }

    note for User "{selfOnly} update() e delete() exigem principal.userId == self.userId"

    class Membership {
        UUID id
        User user
        Campaign campaign,
        Role role
    }

    class World {
        UUID id
        String name
        String mapUrl
        String masterInfo
        <<masterOnly>> create()
        read()
        <<masterOnly>> update()
        <<masterOnly>> delete()
    }

    class Country {
        UUID countryId
        String name
        String info
        String masterInfo
        String mapUrl
        float worldMapX
        float worldMapY
        «masterOnly» create(name, mapUrl)
        read()
        «masterOnly» update(countryId)
        «masterOnly» delete(countryId)
    }

    class City {
        UUID cityId
        String name
        String cityMapUrl
        float countryMapX
        float countryMapY
        «masterOnly» create(name, country)
        read(cityId)
        readAll()
        «masterOnly» update(cityId)
        «masterOnly» delete(cityId)
        «masterOnly» addPoint(pointId, cityId, x, y)
    }

    class Point {
        UUID pointId
        String name
        PointType type
        float cityMapX
        float cityMapY
        <<masterOnly>> create(name, city, type)
        read()
        <<masterOnly>> update(pointId)
        <<masterOnly>> delete(pointId)
    }

    class PointType {
        <<enumeration>>
        SHOP
        INN
        BASTION
        TOURISTIC
        OTHER
    }

    class Shop {
        NPC shopkeeper
        sell(item)
        buy(item)
        «masterOnly» applyDiscount(item)
    }
    
    class Inn{
        NPC innkeeper
        Enum type ("Squalid", "Poor", "Modest", "Comfortable", "Wealthy", "Aristocratic")
    }
    
    class Bastion{
        Player owner
        Int level
    }

    class NPC {
        UUID npcId
        String name
        String photoUrl
        String personality
        String notes
        String masterInformation
        «masterOnly» make(name)
    }

    class Stock {
        UUID stockId
        Array~[Item, int]~ items
    }

    class Item {
        UUID id
        String name
        String source
        Rarity rarity
        Type type
        String description
        bool custom
        Category category
        String link
        float price
        «masterOnly» create(name, rarity, type, price)
        read(id)
        readAll()
        «masterOnly» update(id)
        «masterOnly» delete(id)
    }

    class Player {
        UUID playerId
        String name
        create()
        read()
        update()
        «masterOnly» delete()
    }

    class Inventory {
        int gold
        int silver
        int copper
        readAllEntries()
    }

    class InventoryEntry {
        Item item
        int quantity
        addEntry()
        removeEntry()
        readEntry()
    }

    class CityConnection {
        UUID id
        float globalDistance
        float seaDistance
        bool hasTrain
        bool hasShip
        float price
        Currency currency
        createConnection(cityOne, cityTwo)
        removeConnection(id)
        readAllFromCountry()
    }

    class Currency {
        <<enumeration>>
        GOLD
        SILVER
        COPPER
    }

    class Order {
        UUID orderId
        int quantity
        OrderStatus status
        float price
        Date createDate
        Date receiveDate
        calculatePrice()
        make()
        receive()
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

    class Encyclopedia {
        UUID id
        add(entry)
        remove(entry)
    }

    class EncyclopediaEntry {
        UUID id
        EntryType entryType
        String entryName
        String information
        bool visible
        changeVisibility()
    }

    class EncyclopediaReferenceable {
        <<interface>>
        UUID getReferenceId()
        String getDisplayName()
    }

    class MasterGuard {
        assertCanManage(User user, Campaign campaign)
        boolean canManage(User user, Campaign campaign)
    }

    %% Capability marker
    Campaign ..|> Master
    Country  ..|> Master
    City     ..|> Master
    Shop     ..|> Master
    NPC      ..|> Master
    Item     ..|> Master
    Player   ..|> Master

    %% Autorização
    MasterGuard --> Membership : verifica role
    MasterGuard --> Master     : valida alvo «masterOnly»

    %% User x Campaign x Role
    User "1" -- "0..*" Membership
    Membership "0..*" -- "1" Campaign
    Membership --> Role

    %% Hierarquia geográfica
    Campaign "1" o-- "0..*" World
    World    "1" o-- "1..*" Country
    Country  "1" o-- "0..*" City
    City     "1" o-- "0..*" Point

    %% Conexão não-dirigida
    City "0..*" -- "0..*" City : connection
    CityConnection .. City : «association class»
    CityConnection --> Currency

    %% Point / Shop
    Point <|-- Shop
    Point --> PointType
    Point <|-- Bastion
    Point <|-- Inn

    %% Shop
    Shop "1" --> "1" NPC : vendor
    Shop "1" *-- "1" Stock
    Shop "1" o-- "0..*" Order

    %% Stock (array de pares)
    Stock "1" o-- "0..*" Item : contém

    %% Player / Inventory
    Player "1" *-- "1" Inventory
    Inventory "1" *-- "0..*" InventoryEntry
    InventoryEntry --> Item
    Player "1" *-- "0..1" Bastion
 
    %% Order
    Order "0..*" --> "1" Item
    Order "0..*" --> "1" Player
    Order --> OrderStatus

    %% Enciclopédia
    Campaign "1" o-- "0..*" NPC
    Campaign "1" o-- "0..*" Item
    Campaign "1" *-- "1" Encyclopedia
    Encyclopedia "1" *-- "0..*" EncyclopediaEntry
    EncyclopediaEntry --> EntryType
    EncyclopediaEntry "1" --> "0..1" EncyclopediaReferenceable : references
    NPC     ..|> EncyclopediaReferenceable
    City    ..|> EncyclopediaReferenceable
    Country ..|> EncyclopediaReferenceable
    Point   ..|> EncyclopediaReferenceable

`````