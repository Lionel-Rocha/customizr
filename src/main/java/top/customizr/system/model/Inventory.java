package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@EqualsAndHashCode(of = "id")
public class Inventory {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", unique = true)
    private Party party;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", unique = true)
    private Character character;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", unique = true)
    private NPC npc;

    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InventoryEntry> entries = new HashSet<>();

    @Column(name = "money_gp", nullable = false, precision = 12, scale = 2)
    private BigDecimal moneyGp = BigDecimal.ZERO;

    /**
     * As moedas separadas (pl/po/pe/pp/pc). Colunas novas e opcionais: inventario antigo (tudo nulo) comeca com o
     * {@code moneyGp} quebrado em po/pp/pc. {@code moneyGp} continua sendo o valor total, pra loja e espolio.
     */
    @jakarta.persistence.Embedded
    @lombok.Getter(lombok.AccessLevel.NONE)
    private CoinPurse coins;

    protected Inventory() {}

    public static Inventory forCharacter(Character character) {
        if (character == null) {
            throw new IllegalArgumentException("Falta informar o personagem");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.character = character;
        return inventory;
    }

    public static Inventory forParty(Party party) {
        if (party == null) {
            throw new IllegalArgumentException("Falta informar o grupo");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.party = party;
        return inventory;
    }

    public static Inventory forNpc(NPC npc) {
        if (npc == null) {
            throw new IllegalArgumentException("Falta informar o NPC");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.npc = npc;
        return inventory;
    }

    public InventoryEntry addItem(Item item, int quantity) {
        if (item == null) {
            throw new IllegalArgumentException("Falta informar o item");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade precisa ser positiva");
        }
        InventoryEntry existing = entries.stream()
                .filter(entry -> entry.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.changeQuantity(quantity);
            return existing;
        }
        InventoryEntry entry = new InventoryEntry(this, item, quantity);
        entries.add(entry);
        return entry;
    }

    public void removeItem(UUID itemId, int quantity) {
        if (itemId == null || quantity <= 0) {
            throw new IllegalArgumentException("Faltam o id do item e uma quantidade positiva");
        }
        InventoryEntry entry = entries.stream()
                .filter(candidate -> candidate.getItem().getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esse item não está neste inventário"));
        if (quantity > entry.getQuantity()) {
            throw new IllegalArgumentException("Não dá pra tirar mais itens do que o inventário tem");
        }
        if (quantity == entry.getQuantity()) {
            entries.remove(entry);
        } else {
            entry.changeQuantity(-quantity);
        }
    }

    public void changeMoney(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Falta o valor em dinheiro");
        }
        BigDecimal updatedAmount = moneyGp.add(amount);
        if (updatedAmount.signum() < 0) {
            throw new IllegalArgumentException("O dinheiro do inventário não pode ficar negativo");
        }
        // as moedas acompanham: receber vira po/pp/pc; pagar usa as moedas menores primeiro e volta troco
        CoinPurse purse = getCoins();
        long copper = CoinPurse.toCopper(amount.abs());
        if (amount.signum() > 0) {
            CoinPurse change = CoinPurse.ofGold(amount);
            for (Currency c : Currency.values()) purse.add(c, change.count(c));
        } else if (amount.signum() < 0) {
            purse.pay(copper);
        }
        moneyGp = purse.totalGold();
    }

    // ------------------------------------------------------------------ moedas separadas

    /** A bolsa de moedas (inventario antigo ganha a dele a partir do {@code moneyGp}). */
    public CoinPurse getCoins() {
        if (coins == null) coins = CoinPurse.ofGold(moneyGp == null ? BigDecimal.ZERO : moneyGp);
        return coins;
    }

    /**
     * Poe ({@code amount} positivo) ou tira moedas de uma moeda. Tirar mais do que tem dessa moeda paga o resto
     * pelo valor, com troco (ex.: tirar 30 pp tendo 10 pp e 5 po).
     */
    public void changeCoins(Currency currency, long amount) {
        if (currency == null) throw new IllegalArgumentException("Falta a moeda");
        if (amount == 0) throw new IllegalArgumentException("A quantidade não pode ser zero");
        CoinPurse purse = getCoins();
        if (amount > 0) {
            purse.add(currency, amount);
        } else {
            long want = -amount;
            long fromSame = Math.min(want, purse.count(currency));
            long rest = (want - fromSame) * CoinPurse.copperValue(currency);
            if (rest > purse.totalCopper() - fromSame * CoinPurse.copperValue(currency)) {
                throw new IllegalArgumentException("O dinheiro do inventário não pode ficar negativo");
            }
            purse.add(currency, -fromSame);
            if (rest > 0) purse.pay(rest);
        }
        moneyGp = purse.totalGold();
    }

    /** Cambio: troca moedas de um tipo por outro (o valor tem que fechar em moedas inteiras). */
    public void exchangeCoins(Currency from, Currency to, long amount) {
        getCoins().exchange(from, to, amount);
        moneyGp = getCoins().totalGold();
    }

    /** Usado pela importacao da campanha: poe exatamente estas moedas. */
    public void replaceCoins(Map<Currency, Long> amounts) {
        CoinPurse purse = CoinPurse.empty();
        if (amounts != null) amounts.forEach((c, n) -> { if (c != null && n != null && n > 0) purse.add(c, n); });
        coins = purse;
        moneyGp = purse.totalGold();
    }
}
