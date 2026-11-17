package com.vttcaseiro.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.EffectModifiers;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Resource;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.CharacterService;
import com.vttcaseiro.service.CombatFlow;
import com.vttcaseiro.service.SceneService;
import com.vttcaseiro.service.TableService;
import com.vttcaseiro.service.TokenLink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Um "cliente sem tela" por navegador conectado: guarda uma copia da mesa
 * (o que o servidor manda pra esta conexao) e roda as MESMAS regras do
 * programa ({@link TableService}, {@link CombatFlow}) -- ataque contra a CA
 * dos alvos, dano nos acertados com resistencias, magias, recursos, e as
 * acoes do mestre. O navegador so pede ("web_roll", "web_cast", "web_gm"...);
 * quem decide e o Java, igual no programa.
 */
final class WebSession implements TableService.Port {
    private static final Log log = Log.of(WebSession.class);

    private final Consumer<String> toGame;
    private final Consumer<String> toBrowser;
    private final SceneService scenes = new SceneService(new SceneModel());
    private final Path tempDir;
    private final CharacterService chars;
    private final Map<String, MobSheet> mobs = new ConcurrentHashMap<>();
    final TableService table;
    private volatile String playerId;
    private volatile String playerName;
    private volatile boolean gm;
    private final Map<String, ArrayDeque<Consumer<RollResult>>> pending = new HashMap<>();

    WebSession(Consumer<String> toGame, Consumer<String> toBrowser) throws IOException {
        this.toGame = toGame;
        this.toBrowser = toBrowser;
        this.tempDir = Files.createTempDirectory("vtt-web");
        this.chars = new CharacterService(new LibraryStorage(tempDir));
        this.table = new TableService(scenes, chars, this);
        this.table.setMobLookup(mobs::get);
    }

    void close() {
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    SceneModel scene() {
        return scenes.getScene();
    }

    boolean isGmSession() {
        return gm;
    }

    // ------------------------------------------------------------------
    // Servidor -> copia da mesa
    // ------------------------------------------------------------------

    synchronized void fromServer(NetMessage m) {
        if (m == null || m.type == null) return;
        switch (m.type) {
            case "scene_sync" -> { if (m.scene != null) scenes.replaceScene(m.scene); }
            case "move_token" -> { if (m.x != null && m.y != null) scenes.moveToken(m.tokenId, m.x, m.y); }
            case "add_token" -> { if (m.token != null) scenes.addToken(m.token); }
            case "update_token" -> {
                if (m.token == null) break;
                Optional<TokenModel> t = scene().findToken(m.token.getId());
                if (t.isPresent()) {
                    t.get().copyStateFrom(m.token);
                    t.get().setOwnerId(m.token.getOwnerId());
                } else {
                    scenes.addToken(m.token);
                }
            }
            case "remove_token" -> scenes.removeToken(m.tokenId);
            case "encounter_sync" -> scene().setEncounter(m.encounter);
            case "fog_sync" -> scene().setFog(m.fog);
            case "pins_sync" -> scene().setPins(m.pins);
            case "objects_sync" -> scene().setObjects(m.objects);
            case "walls_sync" -> {
                scene().setWalls(m.walls);
                if (m.visionOn != null) scene().setVisionEnabled(m.visionOn);
                if (m.sceneDark != null) scene().setDark(m.sceneDark);
            }
            case "character_sync" -> {
                if (m.character != null && m.character.getId() != null) {
                    try {
                        chars.storeFromNetwork(m.character);
                    } catch (IOException | RuntimeException ex) {
                        log.warn("Ficha recebida não guardada na copia web: " + ex.getMessage());
                    }
                }
            }
            case "mob_sync" -> { if (m.mob != null && m.mob.getId() != null) mobs.put(m.mob.getId(), m.mob); }
            case "presence" -> {
                if (m.players != null && playerId != null) {
                    for (NetMessage.PlayerInfo p : m.players) if (playerId.equals(p.playerId)) gm = p.gm;
                }
            }
            case "roll_result" -> {
                if (m.label == null || m.player == null || !m.player.equals(playerName)) break;
                ArrayDeque<Consumer<RollResult>> q = pending.get(m.label);
                if (q == null || q.isEmpty()) break;
                Consumer<RollResult> after = q.poll();
                if (q.isEmpty()) pending.remove(m.label);
                try {
                    after.accept(new RollResult(m.total == null ? 0 : m.total, m.detail, m.naturalD20));
                } catch (RuntimeException ex) {
                    log.warn("Erro aplicando resultado da rolagem web: " + ex.getMessage());
                }
            }
            default -> { }
        }
    }

    synchronized void sawHello(JsonObject hello) {
        playerId = str(hello, "playerId");
        playerName = str(hello, "player");
    }

    // ------------------------------------------------------------------
    // Rolagens com "depois" (o servidor rola; o resultado volta pra ca)
    // ------------------------------------------------------------------

    private final CombatFlow.Roller roller = (label, expression, hidden, after) -> send(rollMsg(label, expression, null, 0, hidden), after);

    private NetMessage rollMsg(String label, String expression, String mode, int modifier, boolean hidden) {
        NetMessage m = NetMessage.roll(playerName, expression);
        m.label = label;
        if (mode != null) {
            m.rollMode = mode;
            m.modifier = modifier;
        }
        if (hidden) m.hidden = true;
        return m;
    }

    private void send(NetMessage roll, Consumer<RollResult> after) {
        if (after != null && roll.label != null) pending.computeIfAbsent(roll.label, k -> new ArrayDeque<>()).add(after);
        toGame.accept(NetCodec.encode(roll));
    }

    // ------------------------------------------------------------------
    // Navegador -> regras
    // ------------------------------------------------------------------

    private Optional<CharacterSheet> mySheet(JsonObject o) {
        Optional<CharacterSheet> s = chars.findById(str(o, "sheetId"));
        if (s.isEmpty()) return s;
        if (!gm && (playerId == null || !playerId.equals(s.get().getOwnerPlayerId()))) {
            error("Essa ficha não e sua.");
            return Optional.empty();
        }
        return s;
    }

    private List<TokenModel> targets(JsonObject o) {
        List<TokenModel> out = new ArrayList<>();
        JsonElement e = o.get("targets");
        if (e != null && e.isJsonArray()) {
            for (JsonElement id : e.getAsJsonArray()) scene().findToken(id.getAsString()).ifPresent(out::add);
        }
        return out;
    }

    /** Rolagem pela ficha: d20 com as regras; ataque com alvos compara a CA; dano cai nos acertados. */
    synchronized void webRoll(JsonObject o) {
        Optional<CharacterSheet> maybe = mySheet(o);
        if (maybe.isEmpty()) return;
        CharacterSheet s = maybe.get();
        String kind = String.valueOf(str(o, "rollKind"));
        boolean hidden = o.has("hidden") && o.get("hidden").getAsBoolean();
        String key = "sheet:" + s.getId();
        if (kind.equals("damage") || kind.equals("crit")) {
            int i = o.has("index") ? o.get("index").getAsInt() : -1;
            if (i < 0 || i >= s.getAttacks().size()) return;
            Attack a = s.getAttacks().get(i);
            if (a.damageExpression().isEmpty()) return;
            String expr = a.damageExpression().get();
            boolean crit = kind.equals("crit");
            if (crit) expr = com.vttcaseiro.dice.DiceRoller.doubleDice(expr);
            String label = s.getName() + " - " + (crit ? "Dano CRÍTICO: " : "Dano: ") + a.getName() + " (" + a.getDamageAndType() + ")";
            CombatFlow.rollFromSource(table, roller, key, label, CombatFlow.withDamageBonus(label, expr, s.getEffects()), hidden);
            return;
        }
        NetMessage m = WebGateway.webRoll(o, Map.of(s.getId(), s));
        if (m == null) return;
        List<TokenModel> targets = targets(o);
        boolean attack = kind.equals("attack") || kind.equals("spellAttack");
        send(m, attack && !targets.isEmpty()
                ? r -> table.resolveAttack(key, s.getName(), r.total(), r.naturalD20(), targets) : null);
    }

    /** Conjurar pela ficha: gasta o espaco, efeito (Bencao...), dados da magia nos alvos. */
    synchronized void webCast(JsonObject o) {
        Optional<CharacterSheet> maybe = mySheet(o);
        if (maybe.isEmpty()) return;
        CharacterSheet s = maybe.get();
        int index = o.has("index") ? o.get("index").getAsInt() : -1;
        if (index < 0 || index >= s.getSpells().size()) return;
        PreparedSpell spell = s.getSpells().get(index);
        int wanted = o.has("slot") ? o.get("slot").getAsInt() : spell.getLevel();
        int slot = CombatFlow.slotFor(s, spell, wanted);
        String text = CombatFlow.spendForCast(s, spell, wanted);
        if (text == null) {
            error(s.getName() + " não tem espaco de magia de nível " + spell.getLevel() + " ou maior pra " + spell.getName() + ".");
            return;
        }
        announce(text);
        List<TokenModel> targets = targets(o);
        String notice = CombatFlow.applySpellEffect(table, scene().getTokens(), targets, s, spell);
        if (notice != null) error(notice);
        String dice = spell.diceForSlot(Math.max(slot, spell.getLevel()));
        if (!dice.isBlank()) {
            CombatFlow.castDice(table, roller, targets, s, spell, Math.max(slot, spell.getLevel()), dice,
                    o.has("hidden") && o.get("hidden").getAsBoolean());
        }
        sendCharacterSync(s);
    }

    /** Usar um recurso (Furia...): gasta 1; se for efeito conhecido, liga o efeito. */
    synchronized void webResource(JsonObject o) {
        Optional<CharacterSheet> maybe = mySheet(o);
        if (maybe.isEmpty()) return;
        CharacterSheet s = maybe.get();
        Optional<Resource> r = s.findResource(str(o, "name"));
        if (r.isEmpty()) return;
        if (!r.get().use()) {
            error(r.get().getName() + " acabou.");
            return;
        }
        String extra = "";
        if (EffectModifiers.termsOf(r.get().getName()) != null
                && s.getEffects().stream().noneMatch(x -> x.equalsIgnoreCase(r.get().getName()))) {
            List<String> effects = new ArrayList<>(s.getEffects());
            effects.add(r.get().getName());
            s.setEffects(effects);
            extra = " -- efeito ativo";
        }
        announce(s.getName() + " usa " + r.get().getName() + " (" + r.get().usesText() + " restantes)" + extra + ".");
        sendCharacterSync(s);
        table.pushSheetToTokens(s);
    }

    /** Maior arquivo de ficha aceito pelo navegador (o WebSocket aceita ate 8 MB por mensagem). */
    static final int MAX_IMPORT_BYTES = 5 * 1024 * 1024;

    /** Jogador pelo navegador importando uma ficha (PDF ou JSON): vira uma ficha nova DELE. */
    synchronized void webImport(JsonObject o) {
        String name = str(o, "name");
        String data = str(o, "data");
        if (data == null || data.isBlank()) return;
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            error("Arquivo corrompido no envio.");
            return;
        }
        if (bytes.length > MAX_IMPORT_BYTES) {
            error("Arquivo grande demais (máximo 5 MB).");
            return;
        }
        com.vttcaseiro.importer.SheetImport.Result r;
        try {
            r = com.vttcaseiro.importer.SheetImport.fromBytes(bytes, name);
        } catch (IOException | RuntimeException ex) {
            error("Não foi possível importar a ficha: " + ex.getMessage());
            return;
        }
        CharacterSheet s = r.sheet();
        String original = s.getName();
        int n = 2;
        while (chars.list().contains(s.getName())) s.setName(original + " (" + n++ + ")");
        s.setOwnerPlayerId(playerId);
        if (s.getPlayerName() == null || s.getPlayerName().isBlank()) s.setPlayerName(playerName);
        try {
            chars.storeFromNetwork(s);
        } catch (IOException ex) {
            // o espelho local e so cache; a ficha vai pela rede de qualquer jeito
        }
        sendCharacterSync(s);
        error("Ficha importada (" + r.format() + "): " + s.getName()
                + (r.notes().isEmpty() ? "" : ". Confira: " + String.join(" ", r.notes())));
    }

    /** Dano/cura na propria ficha (regras de PV temporario, concentracao...). */
    synchronized void webHp(JsonObject o) {
        Optional<CharacterSheet> maybe = mySheet(o);
        if (maybe.isEmpty() || !o.has("amount")) return;
        CharacterSheet s = maybe.get();
        int amount = Math.max(0, Math.min(10000, o.get("amount").getAsInt()));
        if (amount == 0) return;
        if (o.has("heal") && o.get("heal").getAsBoolean()) {
            int before = s.getHpCurrent() == null ? 0 : s.getHpCurrent();
            int after = s.heal(amount);
            announce(s.getName() + " recupera " + (after - before) + " PV.");
        } else {
            announce(TokenLink.describeDamage(s.getName(), amount, s.takeDamage(amount)));
        }
        sendCharacterSync(s);
    }

    /** Mestre pelo navegador: as acoes essenciais da mesa, com as regras do programa. */
    synchronized void webGm(JsonObject o) {
        if (!gm) {
            error("Só o mestre.");
            return;
        }
        String action = String.valueOf(str(o, "action"));
        Optional<TokenModel> t = scene().findToken(str(o, "tokenId"));
        int amount = o.has("amount") ? Math.max(0, Math.min(10000, o.get("amount").getAsInt())) : 0;
        switch (action) {
            case "damage" -> t.ifPresent(x -> { if (amount > 0) table.damage(x, amount); });
            case "heal" -> t.ifPresent(x -> { if (amount > 0) table.heal(x, amount); });
            case "hide" -> t.ifPresent(x -> table.setHidden(x, true));
            case "reveal" -> t.ifPresent(x -> table.setHidden(x, false));
            case "initiative" -> t.ifPresent(x -> table.rollInitiative(x, true));
            case "start" -> table.rollInitiativeForAllAndStart();
            case "next" -> table.nextTurn();
            case "end" -> table.endEncounter();
            default -> { }
        }
    }

    /** Numeros a mais pra ficha web: magias e espacos (o resto esta em WebGateway.derived). */
    static void addSpells(JsonObject d, CharacterSheet s) {
        JsonArray spells = new JsonArray();
        for (int i = 0; i < s.getSpells().size(); i++) {
            PreparedSpell p = s.getSpells().get(i);
            JsonObject o = new JsonObject();
            o.addProperty("index", i);
            o.addProperty("name", p.getName());
            o.addProperty("level", p.getLevel());
            o.addProperty("dice", p.getDice());
            o.addProperty("healing", p.isHealing());
            o.addProperty("attack", p.isSpellAttack());
            o.addProperty("concentration", p.isConcentration());
            if (p.saveAbilityOrNull() != null) o.addProperty("save", p.saveAbilityOrNull().abbreviationPt());
            spells.add(o);
        }
        d.add("spells", spells);
        JsonArray slots = new JsonArray();
        for (int lvl = com.vttcaseiro.character.SpellSlots.MIN_LEVEL; lvl <= com.vttcaseiro.character.SpellSlots.MAX_LEVEL; lvl++) {
            int max = s.getSpellSlots().getMax(lvl);
            if (max <= 0) continue;
            JsonObject o = new JsonObject();
            o.addProperty("level", lvl);
            o.addProperty("current", s.getSpellSlots().getCurrent(lvl));
            o.addProperty("max", max);
            slots.add(o);
        }
        d.add("slots", slots);
    }

    // ------------------------------------------------------------------
    // TableService.Port: o que as regras pedem vira mensagem do protocolo
    // ------------------------------------------------------------------

    @Override public boolean isConnected() { return true; }
    @Override public boolean isGm() { return gm; }
    @Override public String myPlayerId() { return playerId; }
    @Override public void sendUpdateToken(TokenModel t) { toGame.accept(NetCodec.encode(NetMessage.updateToken(t))); }
    @Override public void sendRemoveToken(String id) { toGame.accept(NetCodec.encode(NetMessage.removeToken(id))); }

    @Override
    public void sendCharacterSync(CharacterSheet s) {
        String line = NetCodec.encode(NetMessage.characterSync(s));
        toGame.accept(line);
        // O servidor manda a ficha pros OUTROS: o proprio navegador recebe daqui.
        JsonObject obj = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
        obj.add("derived", WebGateway.derived(s));
        toBrowser.accept(obj.toString());
    }

    @Override
    public void sendEncounter(com.vttcaseiro.model.Encounter e) {
        String line = NetCodec.encode(NetMessage.encounterSync(e));
        toGame.accept(line);
        toBrowser.accept(line); // o servidor manda pros outros; o navegador do mestre ve daqui
    }
    @Override public void sendFog(com.vttcaseiro.model.Fog f) { toGame.accept(NetCodec.encode(NetMessage.fogSync(f))); }
    @Override public void sendWalls(SceneModel s) { toGame.accept(NetCodec.encode(NetMessage.wallsSync(s))); }
    @Override public void sendPins(SceneModel s) { toGame.accept(NetCodec.encode(NetMessage.pinsSync(s.getPins()))); }
    @Override public void sendDoorToggle(String id) { toGame.accept(NetCodec.encode(NetMessage.doorToggle(id))); }
    @Override public void announce(String text) { toGame.accept(NetCodec.encode(NetMessage.chat(playerName, text))); }

    @Override
    public void announceGm(String text) {
        NetMessage m = NetMessage.chat(playerName, text);
        m.whisperTo = "mestre";
        toGame.accept(NetCodec.encode(m));
    }

    @Override
    public void error(String text) {
        toBrowser.accept(NetCodec.encode(NetMessage.chat("servidor", text)));
    }

    @Override public void sheetChangedOutside(CharacterSheet s) { }
    @Override public void requestGm(com.vttcaseiro.model.GmRequest r) { toGame.accept(NetCodec.encode(NetMessage.gmRequest(r))); }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
