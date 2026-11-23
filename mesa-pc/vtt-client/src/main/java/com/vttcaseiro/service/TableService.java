package com.vttcaseiro.service;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.RollAdjustment;
import com.vttcaseiro.character.RollKind;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.dice.DiceException;
import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.model.Encounter;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * As regras da MESA (o que acontece quando alguem mexe num token, numa
 * ficha ligada ou no combate), separadas da tela. Antes isto tudo vivia no
 * Main.java junto com a montagem das janelas -- impossivel de testar sem
 * abrir o programa. Agora o Main so chama estes metodos, e os testes usam
 * um {@link Port} falso.
 * <p>
 * Regra central: num token ligado a ficha, a FICHA e a fonte da verdade
 * (dano, cura, condicoes passam por ela e ela empurra pro token). Token
 * solto (monstro) guarda o proprio estado.
 */
public class TableService {

    /** O que este servico precisa do mundo de fora (rede, chat, janelas). */
    public interface Port {
        boolean isConnected();

        /** Mestre -- ou offline, onde quem usa o programa manda em tudo. */
        boolean isGm();

        String myPlayerId();

        void sendUpdateToken(TokenModel token);

        void sendRemoveToken(String tokenId);

        void sendCharacterSync(CharacterSheet sheet);

        void sendEncounter(Encounter encounter);

        default void sendFog(com.vttcaseiro.model.Fog fog) {
        }

        /** Mensagem pro chat de todo mundo. */
        void announce(String text);

        /** Mensagem so pro mestre (sobre token oculto). */
        default void sendDrawingAdd(com.vttcaseiro.model.Drawing drawing) {
        }

        /** Paredes + configuracao de visao da cena (mestre). */
        default void sendWalls(SceneModel scene) {
        }

        /** Mestre mandando a lista inteira de objetos da cena. */
        default void sendObjects(SceneModel scene) {
        }

        /** Mestre mandando a lista inteira de pins da cena. */
        default void sendPins(SceneModel scene) {
        }

        /** Jogador abrindo/fechando porta (o servidor alterna e manda pra todos). */
        default void sendDoorToggle(String wallId) {
        }

        default void sendDrawingRemove(String drawingId) {
        }

        /** ownerId null = todos (so mestre). */
        default void sendDrawingClear(String ownerId) {
        }

        /** Jogador pedindo pro mestre aplicar algo num token que nao controla. */
        default void requestGm(com.vttcaseiro.model.GmRequest request) {
        }

        default void announceGm(String text) {
            announce(text);
        }

        void error(String text);

        /** A ficha mudou por fora da janela dela (atualizar a janela aberta). */
        void sheetChangedOutside(CharacterSheet sheet);
    }

    private final SceneService scenes;
    private final CharacterService characters;
    private final Port port;

    public TableService(SceneService scenes, CharacterService characters, Port port) {
        this.scenes = scenes;
        this.characters = characters;
        this.port = port;
    }

    private SceneModel scene() {
        return scenes.getScene();
    }

    // ------------------------------------------------------------------
    // Permissoes
    // ------------------------------------------------------------------

    /** Dono do token, dono da ficha ligada, ou mestre. */
    public boolean canControl(TokenModel token) {
        if (!port.isConnected() || port.isGm()) return true;
        String me = port.myPlayerId();
        if (me == null) return false;
        if (me.equals(token.getOwnerId())) return true;
        return linkedSheet(token).map(s -> me.equals(s.getOwnerPlayerId())).orElse(false);
    }

    public boolean canSeeHp(TokenModel token) {
        return token.isHpVisibleToAll() || canControl(token);
    }

    public boolean canEditSheet(CharacterSheet sheet) {
        if (!port.isConnected() || port.isGm()) return true;
        return sheet.getOwnerPlayerId() == null || Objects.equals(sheet.getOwnerPlayerId(), port.myPlayerId());
    }

    /** So o mestre passa o controle de um token (e da ficha ligada) adiante. */
    public void giveControl(TokenModel token, String playerId, String playerName) {
        if (!port.isGm()) {
            port.error("Só o mestre pode passar o controle de um token.");
            return;
        }
        token.setOwnerId(playerId);
        publishTokenUpdate(token);
        linkedSheet(token).ifPresent(sheet -> {
            sheet.setOwnerPlayerId(playerId);
            if (playerName != null) sheet.setPlayerName(playerName);
            saveAndSync(sheet);
            port.sheetChangedOutside(sheet);
        });
        port.announce(token.getName() + " agora e controlado por " + (playerName == null ? "ninguém" : playerName) + ".");
    }

    // ------------------------------------------------------------------
    // Token <-> ficha
    // ------------------------------------------------------------------

    public Optional<CharacterSheet> linkedSheet(TokenModel token) {
        return token.getCharacterSheetId() == null ? Optional.empty() : characters.findById(token.getCharacterSheetId());
    }

    /** Aplica o estado do token aqui e manda pros outros. */
    public void publishTokenUpdate(TokenModel token) {
        scenes.updateToken(token);
        if (port.isConnected()) port.sendUpdateToken(token);
    }

    /** Copia PV/condicoes/efeitos da ficha pra todo token dela na cena atual. */
    public void pushSheetToTokens(CharacterSheet sheet) {
        for (TokenModel token : new ArrayList<>(scene().getTokens())) {
            if (sheet.getId().equals(token.getCharacterSheetId()) && TokenLink.pushSheetToToken(sheet, token)) {
                publishTokenUpdate(token);
            }
        }
    }

    private void saveAndSync(CharacterSheet sheet) {
        try {
            characters.save(sheet);
        } catch (IOException ex) {
            port.error("Não foi possível salvar a ficha: " + ex.getMessage());
        }
        if (port.isConnected()) port.sendCharacterSync(sheet);
    }

    /** A ficha mudou por uma acao FORA da janela dela (menu do token, chat). */
    public void afterSheetEditedOutside(CharacterSheet sheet) {
        saveAndSync(sheet);
        pushSheetToTokens(sheet);
        port.sheetChangedOutside(sheet);
    }

    public void damage(TokenModel token, int amount) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            CharacterSheet.DamageOutcome outcome = sheet.get().takeDamage(amount);
            port.announce(TokenLink.describeDamage(sheet.get().getName(), amount, outcome));
            afterSheetEditedOutside(sheet.get());
        } else {
            announceAbout(token, TokenLink.damageUnlinkedToken(token, amount));
            publishTokenUpdate(token);
        }
    }

    public void heal(TokenModel token, int amount) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            int before = sheet.get().getHpCurrent() == null ? 0 : sheet.get().getHpCurrent();
            int after = sheet.get().heal(amount);
            port.announce(sheet.get().getName() + " recupera " + (after - before) + " PV.");
            afterSheetEditedOutside(sheet.get());
        } else {
            announceAbout(token, TokenLink.healUnlinkedToken(token, amount));
            publishTokenUpdate(token);
        }
    }

    public void setHp(TokenModel token, Integer current, Integer max, Integer temp) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            sheet.get().setHpCurrent(current);
            sheet.get().setHpMax(max);
            sheet.get().setHpTemp(temp == null ? 0 : temp);
            afterSheetEditedOutside(sheet.get());
        } else {
            token.setCurrentHp(current);
            token.setMaxHp(max);
            token.setTempHp(temp != null && temp > 0 ? temp : null);
            publishTokenUpdate(token);
        }
    }

    public void setConditions(TokenModel token, List<String> conditions) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            sheet.get().setConditions(conditions);
            afterSheetEditedOutside(sheet.get());
        } else {
            token.setConditions(new ArrayList<>(conditions));
            publishTokenUpdate(token);
        }
    }

    public void setEffects(TokenModel token, List<String> effects) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            // "Concentrando: X" e "Exaustao N" sao campos proprios da ficha:
            // nao viram efeito livre; apagar o de concentracao encerra ela.
            List<String> free = new ArrayList<>();
            boolean keepsConcentration = false;
            for (String e : effects) {
                if (e.startsWith("Concentrando")) keepsConcentration = true;
                else if (!e.startsWith("Exaustao")) free.add(e);
            }
            if (!keepsConcentration) sheet.get().setConcentratingOn(null);
            sheet.get().setEffects(free);
            afterSheetEditedOutside(sheet.get());
        } else {
            token.setEffects(effects);
            publishTokenUpdate(token);
        }
    }

    /**
     * Recalcula as auras: quem entrou ganha o efeito, quem saiu perde. So o mestre (ou offline) calcula -- e quem
     * manda nos tokens; o resultado vai pela rede como qualquer mudanca de efeito.
     *
     * @return quantos tokens mudaram
     */
    /** Troca as auras do token (manda pra mesa) e ja recalcula quem esta dentro. */
    public void setAuras(TokenModel token, List<com.vttcaseiro.model.Aura> auras) {
        if (token == null) return;
        token.setAuras(auras);
        publishTokenUpdate(token);
        recomputeAuras();
    }

    public int recomputeAuras() {
        if (port.isConnected() && !port.isGm()) return 0;
        SceneModel scene = scene();
        boolean any = scene.getTokens().stream().anyMatch(t -> !t.getAuras().isEmpty()
                || t.getEffects().stream().anyMatch(AuraService::isAuraEffect));
        if (!any) return 0;
        java.util.Map<String, List<String>> want = AuraService.desired(scene);
        int changed = 0;
        for (TokenModel t : new ArrayList<>(scene.getTokens())) {
            Optional<CharacterSheet> sheet = linkedSheet(t);
            List<String> current = sheet.map(s -> (List<String>) new ArrayList<>(s.getEffects())).orElse(new ArrayList<>(t.getEffects()));
            List<String> merged = AuraService.merge(current, want.getOrDefault(t.getId(), List.of()));
            if (!merged.equals(current)) {
                setEffects(t, merged);
                changed++;
            }
        }
        return changed;
    }

    /** Efeito livre a mais (sem tirar os que ja tem). */
    public void addEffect(TokenModel token, String effect) {
        List<String> current = linkedSheet(token).map(TokenLink::tokenEffectsFor).orElse(new ArrayList<>(token.getEffects()));
        if (!current.contains(effect)) current.add(effect);
        setEffects(token, current);
    }

    public void removeEffect(TokenModel token, String effect) {
        List<String> current = linkedSheet(token).map(TokenLink::tokenEffectsFor).orElse(new ArrayList<>(token.getEffects()));
        if (current.remove(effect)) setEffects(token, current);
    }

    /** Camada do mestre: esconder/revelar o token pros jogadores. */
    public void setHidden(TokenModel token, boolean hidden) {
        if (!port.isGm()) {
            port.error("Só o mestre esconde ou revela tokens.");
            return;
        }
        token.setHiddenFromPlayers(hidden);
        publishTokenUpdate(token);
        port.announceGm(token.getName() + (hidden ? " agora está oculto dos jogadores." : " foi revelado aos jogadores."));
    }

    /** Anuncia pra mesa, ou so pro mestre se o token esta oculto (o nome
     * do monstro escondido nao pode vazar no chat). */
    private void announceAbout(TokenModel token, String text) {
        if (token.isHiddenFromPlayers()) port.announceGm(text + " (oculto)");
        else port.announce(text);
    }

    public void removeToken(TokenModel token) {
        if (port.isConnected()) {
            port.sendRemoveToken(token.getId());
        } else {
            scenes.removeToken(token.getId());
        }
    }

    // ------------------------------------------------------------------
    // Iniciativa e combate
    // ------------------------------------------------------------------

    /** Rola 1d20 + bonus (ficha ligada com condicoes, ou o do token) e grava. */
    public RollResult rollInitiative(TokenModel token, boolean announceIt) {
        return rollInitiative(token, announceIt, RollMode.NORMAL);
    }

    /** Igual, com a vantagem/desvantagem escolhida na ficha. Soma o modificador do rastreador. */
    public RollResult rollInitiative(TokenModel token, boolean announceIt, RollMode chosen) {
        int bonus = token.getInitiativeBonus() + token.getInitiativeModifier();
        RollMode mode = chosen == null ? RollMode.NORMAL : chosen;
        String notes = "";
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            RollAdjustment adj = sheet.get().rollAdjustment(RollKind.INITIATIVE, Ability.DEXTERITY, mode);
            bonus = sheet.get().initiative() + adj.modifierDelta() + token.getInitiativeModifier();
            mode = adj.mode();
            notes = adj.notesText();
        } else {
            RollAdjustment adj = com.vttcaseiro.character.CombatRules.rollAdjustment(token.getConditions(), 0,
                    RollKind.INITIATIVE, Ability.DEXTERITY, mode, token.getEffects(),
                    com.vttcaseiro.character.CombatRules::rollDice);
            mode = adj.mode();
            notes = adj.notesText();
        }
        if (sheet.isPresent() && com.vttcaseiro.character.GameSystem.of(sheet.get()).isCustom()) {
            // Tormenta20 / Ordem Paranormal: a pericia Iniciativa (na Ordem, varios d20 e fica o maior)
            String expr = com.vttcaseiro.character.GameSystem.of(sheet.get()).initiativeTest(sheet.get());
            mode = RollMode.NORMAL;
            RollResult custom = withTrackerModifier(rollOrZero(expr), token.getInitiativeModifier());
            return finishInitiative(token, custom, notes, announceIt);
        }
        RollResult result = switch (mode) {
            case ADVANTAGE -> DiceRoller.rollWithAdvantage(bonus);
            case DISADVANTAGE -> DiceRoller.rollWithDisadvantage(bonus);
            case NORMAL -> rollOrZero(d20(bonus));
        };
        // dados extras da ficha (Inspiracao de Bardo, Gloom Stalker...) somam no mesmo resultado
        String extra = sheet.map(CharacterSheet::getInitiativeDice).orElse("");
        if (!extra.isBlank()) {
            RollResult more = rollOrZero(extra);
            result = new RollResult(result.total() + more.total(), result.detail() + " + " + more.detail(), result.naturalD20());
        }
        return finishInitiative(token, result, notes, announceIt);
    }

    private static RollResult withTrackerModifier(RollResult r, int mod) {
        if (mod == 0) return r;
        return new RollResult(r.total() + mod, r.detail() + (mod > 0 ? " + " : " - ") + Math.abs(mod) + " (rastreador)",
                r.naturalD20());
    }

    private RollResult finishInitiative(TokenModel token, RollResult result, String notes, boolean announceIt) {
        token.setInitiative(result.total());
        publishTokenUpdate(token);
        // a ficha guarda o resultado sem o modificador do rastreador (ele soma de novo ao puxar)
        linkedSheet(token).ifPresent(s -> {
            if (canEditSheet(s)) {
                s.setLastInitiative(result.total() - token.getInitiativeModifier());
                saveAndSync(s);
            }
        });
        if (announceIt) announceAbout(token, "Iniciativa de " + token.getName() + ": " + result.detail() + notes);
        return result;
    }

    /**
     * Iniciativa rolada na ficha sem token no mapa: rola aqui (o resultado fica conhecido), anuncia e
     * guarda na ficha pro rastreador puxar quando o token entrar.
     */
    public RollResult rollSheetInitiative(CharacterSheet sheet, RollMode chosen, boolean hidden) {
        RollAdjustment adj = sheet.rollAdjustment(RollKind.INITIATIVE, Ability.DEXTERITY, chosen == null ? RollMode.NORMAL : chosen);
        int bonus = sheet.initiative() + adj.modifierDelta();
        RollResult result;
        if (com.vttcaseiro.character.GameSystem.of(sheet).isCustom()) {
            result = rollOrZero(com.vttcaseiro.character.GameSystem.of(sheet).initiativeTest(sheet));
        } else {
            result = switch (adj.mode()) {
                case ADVANTAGE -> DiceRoller.rollWithAdvantage(bonus);
                case DISADVANTAGE -> DiceRoller.rollWithDisadvantage(bonus);
                case NORMAL -> rollOrZero(d20(bonus));
            };
            if (!sheet.getInitiativeDice().isBlank()) {
                RollResult more = rollOrZero(sheet.getInitiativeDice());
                result = new RollResult(result.total() + more.total(), result.detail() + " + " + more.detail(), result.naturalD20());
            }
        }
        sheet.setLastInitiative(result.total());
        saveAndSync(sheet);
        String text = "Iniciativa de " + sheet.getName() + ": " + result.detail() + adj.notesText();
        if (hidden) port.announceGm(text + " (escondida)");
        else port.announce(text);
        return result;
    }

    /** Muda o modificador do rastreador; a iniciativa ja rolada anda junto (17 com +2 -> 19). */
    public void setInitiativeModifier(TokenModel token, int modifier) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        int before = token.getInitiativeModifier();
        token.setInitiativeModifier(modifier);
        int delta = token.getInitiativeModifier() - before;
        if (token.getInitiative() != null) token.setInitiative(token.getInitiative() + delta);
        publishTokenUpdate(token);
    }

    /** Cor da linha no rastreador ("green", "yellow", "red"; null = cinza). */
    public void setTrackerColor(TokenModel token, String color) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        token.setTrackerColor(color);
        publishTokenUpdate(token);
    }

    /** Puxa a iniciativa rolada na ficha ligada (+ modificador do rastreador). False = ficha sem rolagem. */
    public boolean pullInitiativeFromSheet(TokenModel token) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isEmpty() || sheet.get().getLastInitiative() == null) return false;
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return false;
        }
        token.setInitiative(sheet.get().getLastInitiative() + token.getInitiativeModifier());
        publishTokenUpdate(token);
        return true;
    }

    /** Todos os tokens de ficha que tem iniciativa rolada; devolve quantos puxou. */
    public int pullInitiativesFromSheets() {
        int n = 0;
        for (TokenModel t : new ArrayList<>(scene().getTokens())) {
            if (linkedSheet(t).map(CharacterSheet::getLastInitiative).isPresent() && pullInitiativeFromSheet(t)) n++;
        }
        if (n == 0) port.error("Nenhuma ficha no mapa tem iniciativa rolada ainda.");
        return n;
    }

    /** Rola pra todos, comeca o combate e anuncia a ordem. */
    public void rollInitiativeForAllAndStart() {
        if (scene().getTokens().isEmpty()) {
            port.error("Não há tokens nesta cena pra rolar iniciativa.");
            return;
        }
        for (TokenModel token : new ArrayList<>(scene().getTokens())) {
            rollInitiative(token, false);
        }
        startEncounter();
    }

    public void startEncounter() {
        if (!port.isGm()) {
            port.error("Só o mestre controla os turnos.");
            return;
        }
        Optional<TokenModel> first = EncounterService.start(scene());
        StringBuilder order = new StringBuilder("Combate! Rodada 1. Ordem: ");
        List<TokenModel> sorted = EncounterService.order(scene());
        sorted.removeIf(TokenModel::isHiddenFromPlayers); // a ordem anunciada nao entrega o oculto
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) order.append(" > ");
            TokenModel t = sorted.get(i);
            order.append(t.getName()).append(" (").append(t.getInitiative() == null ? "--" : t.getInitiative()).append(")");
        }
        port.announce(order.toString());
        first.ifPresent(t -> {
            if (t.isHiddenFromPlayers()) port.announceGm("Turno de: " + t.getName() + " (oculto)");
            else port.announce("Turno de: " + t.getName());
        });
        publishEncounter();
    }

    /** Passa a vez; ao virar a rodada, vence os efeitos com duracao. */
    public void nextTurn() {
        if (!port.isGm()) {
            port.error("Só o mestre controla os turnos.");
            return;
        }
        EncounterService.TurnResult r = EncounterService.next(scene());
        if (r.current() == null) {
            port.error("Não há tokens nesta cena.");
            publishEncounter();
            return;
        }
        if (r.newRound()) port.announce("--- Rodada " + r.round() + " ---");
        for (Encounter.TimedEffect fx : r.expired()) {
            scene().findToken(fx.getTokenId()).ifPresent(t -> {
                removeEffect(t, fx.getName());
                port.announce(fx.getName() + " terminou em " + t.getName() + ".");
            });
        }
        if (r.current().isHiddenFromPlayers()) {
            port.announce("Turno de: ???");
            port.announceGm("Turno de: " + r.current().getName() + " (oculto)");
        } else {
            port.announce("Turno de: " + r.current().getName());
        }
        publishEncounter();
    }

    public void endEncounter() {
        if (!port.isGm()) {
            port.error("Só o mestre controla os turnos.");
            return;
        }
        // Efeitos com duracao saem dos tokens junto com o combate.
        for (Encounter.TimedEffect fx : new ArrayList<>(scene().getEncounter().getTimedEffects())) {
            scene().findToken(fx.getTokenId()).ifPresent(t -> removeEffect(t, fx.getName()));
        }
        EncounterService.end(scene());
        port.announce("Fim do combate.");
        publishEncounter();
    }

    /** "Bencao" por 10 rodadas no token (aparece nos efeitos e vence sozinho). */
    public void addTimedEffect(TokenModel token, String name, int rounds) {
        if (name == null || name.isBlank()) return;
        EncounterService.addTimedEffect(scene(), token, name.trim(), rounds);
        addEffect(token, name.trim());
        port.announce(token.getName() + ": " + name.trim() + " por " + rounds + " rodada(s).");
        publishEncounter();
    }

    /** Encontro chegou da rede (o mestre mandou). */
    public void applyRemoteEncounter(Encounter encounter) {
        scene().setEncounter(encounter);
        scenes.encounterChanged();
    }

    private void publishEncounter() {
        if (port.isConnected()) port.sendEncounter(scene().getEncounter());
        scenes.encounterChanged();
    }

    // ------------------------------------------------------------------
    // Neblina de guerra (so mestre)
    // ------------------------------------------------------------------

    public void setFogEnabled(boolean enabled) {
        if (!requireGmForFog()) return;
        scene().getFog().setEnabled(enabled);
        publishFog();
    }

    /** Revela ou cobre um retangulo de casas. Revelar com a neblina
     * desligada ja liga ela (senao a ferramenta pareceria nao fazer nada). */
    public void editFog(int c0, int r0, int c1, int r1, boolean reveal) {
        if (!requireGmForFog()) return;
        if (!scene().getFog().isEnabled()) scene().getFog().setEnabled(true);
        scene().getFog().setArea(c0, r0, c1, r1, reveal);
        publishFog();
    }

    /** Neblina por lista de casas (grade hexagonal: hexagonos q,r). */
    public void editFogCells(java.util.List<int[]> cells, boolean reveal) {
        if (!requireGmForFog()) return;
        if (!scene().getFog().isEnabled()) scene().getFog().setEnabled(true);
        scene().getFog().setCells(cells, reveal);
        publishFog();
    }

    public void revealAllFog(int cols, int rows) {
        if (!requireGmForFog()) return;
        scene().getFog().revealAll(cols, rows);
        publishFog();
    }

    public void coverAllFog() {
        if (!requireGmForFog()) return;
        scene().getFog().setEnabled(true);
        scene().getFog().coverAll();
        publishFog();
    }

    public void applyRemoteFog(com.vttcaseiro.model.Fog fog) {
        scene().setFog(fog);
        scenes.fogChanged();
    }

    private boolean requireGmForFog() {
        if (port.isGm()) return true;
        port.error("Só o mestre mexe na neblina.");
        return false;
    }

    private void publishFog() {
        if (port.isConnected()) port.sendFog(scene().getFog());
        scenes.fogChanged();
    }

    // ------------------------------------------------------------------
    // Tamanho do token
    // ------------------------------------------------------------------

    /** Lado do token em casas: 1 (Pequeno/Medio), 2 (Grande), 3 (Enorme), 4 (Imenso). */
    public void setSize(TokenModel token, int cells) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        int c = Math.max(1, Math.min(6, cells));
        token.setWidthCells(c);
        token.setHeightCells(c);
        publishTokenUpdate(token);
    }

    /** Gira o token (graus; positivo = horario). */
    public void rotateToken(TokenModel token, double degrees) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        token.setRotation(token.getRotation() + degrees);
        publishTokenUpdate(token);
    }

    public void resetRotation(TokenModel token) {
        if (!canControl(token)) return;
        token.setRotation(0);
        publishTokenUpdate(token);
    }

    public static String sizeName(int cells) {
        return switch (cells) {
            case 1 -> "Pequeno/Medio";
            case 2 -> "Grande";
            case 3 -> "Enorme";
            case 4 -> "Imenso";
            default -> cells + "x" + cells;
        };
    }

    // ------------------------------------------------------------------
    // Salvaguarda em grupo (a "Bola de Fogo": todo mundo rola, dano cheio
    // em quem falha, metade em quem passa)
    // ------------------------------------------------------------------

    private java.util.function.Function<String, com.vttcaseiro.mob.MobSheet> mobLookup = id -> null;

    /** Como achar a ficha de monstro de um token (bonus de salvaguarda do bloco). */
    public void setMobLookup(java.util.function.Function<String, com.vttcaseiro.mob.MobSheet> lookup) {
        this.mobLookup = lookup == null ? id -> null : lookup;
    }

    public record SaveResult(TokenModel token, int total, boolean success, String detail, int damage) {
    }

    // ------------------------------------------------------------------
    // Ataque contra a CA dos alvos + dano com resistencias
    // ------------------------------------------------------------------

    /** Resultado de um ataque num alvo. ac == null: CA desconhecida (o mestre decide). */
    public record AttackOutcome(TokenModel target, Integer ac, boolean hit, boolean crit) {
    }

    private record LastAttack(List<AttackOutcome> outcomes, long time) {
    }

    /** Quanto tempo o "último ataque" vale pro dano seguinte cair nos acertados. */
    static final long ATTACK_MEMORY_MS = 3 * 60 * 1000;
    private final java.util.Map<String, LastAttack> lastAttacks = new java.util.HashMap<>();
    private java.util.function.LongSupplier clock = System::currentTimeMillis;

    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /** CA do token: ficha do personagem, bloco do monstro, ou desconhecida. */
    public Optional<Integer> armorClassOf(TokenModel token) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) return Optional.of(sheet.get().totalArmorClass());
        com.vttcaseiro.mob.MobSheet mob = token.getMobSheetId() == null ? null : mobLookup.apply(token.getMobSheetId());
        return mob == null ? Optional.empty()
                : Optional.of(mob.getArmorClass() + com.vttcaseiro.character.EffectModifiers.acBonus(token.getEffects()));
    }

    /** Resistencias/imunidades/vulnerabilidades do token. */
    public com.vttcaseiro.character.DamageTypes.Defenses defensesOf(TokenModel token) {
        Optional<CharacterSheet> sheet = linkedSheet(token);
        com.vttcaseiro.character.DamageTypes.Defenses base;
        List<String> effects;
        if (sheet.isPresent()) {
            base = sheet.get().defenses();
            effects = sheet.get().getEffects();
        } else {
            com.vttcaseiro.mob.MobSheet mob = token.getMobSheetId() == null ? null : mobLookup.apply(token.getMobSheetId());
            base = mob == null ? com.vttcaseiro.character.DamageTypes.Defenses.NONE
                    : com.vttcaseiro.character.DamageTypes.defenses(mob.getDamageResistances(),
                    mob.getDamageImmunities(), mob.getDamageVulnerabilities());
            effects = token.getEffects();
        }
        // Resistencia de efeito (Furia: contundente/perfurante/cortante) soma com a da ficha.
        java.util.Set<String> fromEffects = com.vttcaseiro.character.EffectModifiers.resistances(effects);
        if (fromEffects.isEmpty()) return base;
        java.util.Set<String> resist = new java.util.LinkedHashSet<>(base.resist());
        resist.addAll(fromEffects);
        return new com.vttcaseiro.character.DamageTypes.Defenses(resist, base.immune(), base.vulnerable());
    }

    /**
     * Compara a jogada de ataque com a CA de cada alvo (20 natural sempre
     * acerta e e critico; 1 natural sempre erra), anuncia e guarda quem foi
     * acertado pra o dano seguinte da mesma fonte cair neles. O chat
     * publico NAO mostra a CA de monstro (so o mestre ve).
     */
    public List<AttackOutcome> resolveAttack(String sourceKey, String attacker, int total, Integer naturalD20,
                                             List<TokenModel> targets) {
        List<AttackOutcome> outcomes = new ArrayList<>();
        if (targets == null || targets.isEmpty()) return outcomes;
        StringBuilder pub = new StringBuilder();
        StringBuilder gm = new StringBuilder();
        for (TokenModel t : targets) {
            Integer ac = armorClassOf(t).orElse(null);
            boolean nat20 = naturalD20 != null && naturalD20 == 20;
            boolean nat1 = naturalD20 != null && naturalD20 == 1;
            boolean hit = !nat1 && (nat20 || (ac != null && total >= ac));
            AttackOutcome o = new AttackOutcome(t, ac, hit, nat20 && !nat1);
            outcomes.add(o);
            String verdict = ac == null && !nat20 && !nat1 ? "CA desconhecida (mestre decide)"
                    : o.crit() ? "ACERTO CRÍTICO" : hit ? "acertou" : "errou";
            boolean showAc = ac != null && (t.getCharacterSheetId() != null || port.isGm());
            String line = "\n  " + t.getName() + ": " + verdict;
            if (t.isHiddenFromPlayers()) gm.append(line).append(ac != null ? " (CA " + ac + ")" : "");
            else pub.append(line).append(showAc && t.getCharacterSheetId() != null ? " (CA " + ac + ")" : "");
            if (!t.isHiddenFromPlayers() && ac != null && t.getCharacterSheetId() == null) {
                gm.append("\n  ").append(t.getName()).append(": ").append(total).append(" vs CA ").append(ac);
            }
        }
        lastAttacks.put(sourceKey, new LastAttack(outcomes, clock.getAsLong()));
        if (pub.length() > 0) port.announce(attacker + " ataca (" + total + "):" + pub);
        // So na tela do mestre: num jogador, o "pro mestre" e um sussurro -- e quem sussurra recebe
        // copia, ou seja, o jogador veria a CA do monstro.
        if (gm.length() > 0 && port.isGm()) port.announceGm(attacker + " ataca (" + total + ") -- detalhes do mestre:" + gm);
        return outcomes;
    }

    private Optional<LastAttack> freshAttack(String sourceKey) {
        LastAttack last = lastAttacks.get(sourceKey);
        if (last == null || clock.getAsLong() - last.time() > ATTACK_MEMORY_MS) return Optional.empty();
        return Optional.of(last);
    }

    /** O ultimo ataque desta fonte foi critico em todos os acertados? (pra dobrar os dados do dano) */
    public boolean lastAttackWasCritical(String sourceKey) {
        return freshAttack(sourceKey).map(l -> {
            List<AttackOutcome> hits = l.outcomes().stream().filter(AttackOutcome::hit).toList();
            return !hits.isEmpty() && hits.stream().allMatch(AttackOutcome::crit);
        }).orElse(false);
    }

    /** Tem ataque recente desta fonte esperando o dano? */
    public boolean hasPendingAttack(String sourceKey) {
        return freshAttack(sourceKey).isPresent();
    }

    /** Quem o ultimo ataque desta fonte acertou -- e "consome" o ataque
     * (um segundo clique em Dano nao aplica de novo). */
    public List<TokenModel> takeHits(String sourceKey) {
        Optional<LastAttack> last = freshAttack(sourceKey);
        lastAttacks.remove(sourceKey);
        if (last.isEmpty()) return List.of();
        List<TokenModel> hits = new ArrayList<>();
        for (AttackOutcome o : last.get().outcomes()) {
            if (o.hit() && scene().getTokens().contains(o.target())) hits.add(o.target());
        }
        return hits;
    }

    /**
     * Aplica dano com tipo no token, ja com resistencia/imunidade/
     * vulnerabilidade, avisando no chat quando o numero muda.
     */
    public int applyTypedDamage(TokenModel token, int amount, String damageType) {
        com.vttcaseiro.character.DamageTypes.Result r =
                com.vttcaseiro.character.DamageTypes.apply(amount, damageType, defensesOf(token));
        if (!r.note().isEmpty()) {
            announceAbout(token, token.getName() + " e " + r.note() + ": " + amount + " -> " + r.amount() + " de dano.");
        }
        if (r.amount() > 0) damage(token, r.amount());
        return r.amount();
    }

    /**
     * Dano nos alvos: os que eu controlo (ou todos, se sou o mestre/offline)
     * aplico aqui; os outros viram um pedido pro mestre.
     */
    public void damageTargets(List<TokenModel> targets, int amount, String damageType, String label) {
        List<String> forGm = new ArrayList<>();
        for (TokenModel t : targets) {
            if (port.isGm() || canControl(t)) applyTypedDamage(t, amount, damageType);
            else forGm.add(t.getId());
        }
        if (!forGm.isEmpty()) port.requestGm(com.vttcaseiro.model.GmRequest.damage(forGm, amount, damageType, label));
    }

    public void healTargets(List<TokenModel> targets, int amount, String label) {
        List<String> forGm = new ArrayList<>();
        for (TokenModel t : targets) {
            if (port.isGm() || canControl(t)) heal(t, amount);
            else forGm.add(t.getId());
        }
        if (!forGm.isEmpty()) port.requestGm(com.vttcaseiro.model.GmRequest.heal(forGm, amount, label));
    }

    /** Magia de area nos alvos: mestre rola as salvaguardas; jogador pede. */
    public void saveTargets(List<TokenModel> targets, Ability ability, int dc, int amount, String damageType,
                            boolean half, String label) {
        if (targets.isEmpty()) return;
        if (port.isGm()) {
            groupSave(ability, dc, targets, String.valueOf(amount), half, damageType);
        } else {
            port.requestGm(com.vttcaseiro.model.GmRequest.save(targets.stream().map(TokenModel::getId).toList(),
                    ability.name(), dc, amount, damageType, half, label));
        }
    }

    /**
     * Efeito de magia conjurada (Bencao, Escudo da Fe...). Com combate rolando
     * vira efeito com duracao (sai sozinho); fora de combate, efeito simples.
     * Jogador conectado pede pro mestre (o turno/encontro e do mestre).
     */
    public void applySpellEffect(List<TokenModel> tokens, String effectName, int rounds) {
        if (tokens.isEmpty() || effectName == null || effectName.isBlank()) return;
        if (port.isConnected() && !port.isGm()) {
            port.requestGm(com.vttcaseiro.model.GmRequest.effect(tokens.stream().map(TokenModel::getId).toList(),
                    effectName, rounds));
            return;
        }
        boolean combat = scene().getEncounter().isActive();
        for (TokenModel t : tokens) {
            if (combat && rounds > 0) addTimedEffect(t, effectName, rounds);
            else addEffect(t, effectName);
        }
    }

    /** Mestre recebendo o pedido de um jogador: aplica com as regras daqui. */
    public void handleGmRequest(com.vttcaseiro.model.GmRequest req, String from) {
        if (!port.isGm() || req == null || req.getKind() == null) return;
        List<TokenModel> targets = new ArrayList<>();
        for (String id : req.getTokenIds()) scene().findToken(id).ifPresent(targets::add);
        if (targets.isEmpty()) return;
        String who = from == null ? "jogador" : from;
        int amount = Math.max(0, Math.min(10000, req.getAmount()));
        switch (req.getKind()) {
            case "damage" -> {
                port.announceGm(who + " pediu " + amount + " de dano" + typeSuffix(req.getDamageType())
                        + (req.getLabel() == null ? "" : " (" + req.getLabel() + ")") + ".");
                for (TokenModel t : targets) applyTypedDamage(t, amount, req.getDamageType());
            }
            case "heal" -> {
                for (TokenModel t : targets) heal(t, amount);
            }
            case "effect" -> {
                String name = req.getLabel() == null ? "" : req.getLabel().strip();
                if (name.isEmpty() || name.length() > 80) return;
                port.announceGm(who + " conjurou " + name + ".");
                applySpellEffect(targets, name, amount);
            }
            case "save" -> {
                Ability ability;
                try {
                    ability = Ability.valueOf(req.getAbility());
                } catch (RuntimeException ex) {
                    return;
                }
                port.announceGm(who + " pediu salvaguarda de " + ability.abbreviationPt() + " CD " + req.getDc()
                        + (req.getLabel() == null ? "" : " (" + req.getLabel() + ")") + ".");
                groupSave(ability, req.getDc(), targets, amount > 0 ? String.valueOf(amount) : null, req.isHalf(),
                        req.getDamageType());
            }
            default -> { }
        }
    }

    private static String typeSuffix(String type) {
        return type == null ? "" : " de " + com.vttcaseiro.character.DamageTypes.namePt(type);
    }

    /** Bonus e ajuste (condicoes/exaustao) da salvaguarda de um token. */
    private RollResult rollSave(TokenModel token, Ability ability, StringBuilder notes, boolean[] autoFail) {
        int bonus;
        RollAdjustment adj;
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent()) {
            adj = sheet.get().rollAdjustment(RollKind.SAVING_THROW, ability, RollMode.NORMAL);
            bonus = sheet.get().savingThrowBonus(ability);
        } else {
            com.vttcaseiro.mob.MobSheet mob = token.getMobSheetId() == null ? null : mobLookup.apply(token.getMobSheetId());
            bonus = mob == null ? 0 : mob.saveBonus(ability);
            adj = com.vttcaseiro.character.CombatRules.rollAdjustment(token.getConditions(), 0,
                    RollKind.SAVING_THROW, ability, RollMode.NORMAL, token.getEffects(),
                    com.vttcaseiro.character.CombatRules::rollDice);
        }
        bonus += adj.modifierDelta();
        notes.append(adj.notesText());
        autoFail[0] = adj.autoFail();
        return switch (adj.mode()) {
            case ADVANTAGE -> DiceRoller.rollWithAdvantage(bonus);
            case DISADVANTAGE -> DiceRoller.rollWithDisadvantage(bonus);
            case NORMAL -> rollOrZero(d20(bonus));
        };
    }

    /**
     * Todos os tokens rolam a salvaguarda contra a CD. Com {@code damageExpr}
     * (ex.: "8d6"), rola o dano UMA vez e aplica: cheio em quem falhou,
     * metade (arredondada pra baixo) em quem passou se {@code halfOnSuccess},
     * nada senao. So o mestre pede.
     */
    public List<SaveResult> groupSave(Ability ability, int dc, List<TokenModel> tokens,
                                      String damageExpr, boolean halfOnSuccess) {
        return groupSave(ability, dc, tokens, damageExpr, halfOnSuccess, null);
    }

    /** Igual, com o tipo do dano (aplica resistencia/imunidade/vulnerabilidade). */
    public List<SaveResult> groupSave(Ability ability, int dc, List<TokenModel> tokens,
                                      String damageExpr, boolean halfOnSuccess, String damageType) {
        List<SaveResult> results = new ArrayList<>();
        if (!port.isGm()) {
            port.error("Só o mestre pede salvaguarda em grupo.");
            return results;
        }
        if (tokens.isEmpty()) {
            port.error("Nenhum token pra rolar.");
            return results;
        }
        RollResult damage = null;
        if (damageExpr != null && damageExpr.trim().matches("\\d+")) {
            // Dano ja rolado (ex.: o jogador rolou a Bola de Fogo no chat).
            int fixed = Integer.parseInt(damageExpr.trim());
            damage = new RollResult(fixed, String.valueOf(fixed));
        } else if (damageExpr != null && !damageExpr.isBlank()) {
            try {
                damage = DiceRoller.roll(damageExpr.trim());
            } catch (DiceException ex) {
                port.error("Dano inválido: " + ex.getMessage());
                return results;
            }
        }
        String title = "Salvaguarda de " + ability.abbreviationPt() + " CD " + dc + typeSuffix(damageType);
        StringBuilder pub = new StringBuilder(title + (damage != null ? " -- dano " + damage.detail() : "") + ":");
        StringBuilder gm = new StringBuilder();
        for (TokenModel token : tokens) {
            StringBuilder notes = new StringBuilder();
            boolean[] autoFail = {false};
            RollResult r = rollSave(token, ability, notes, autoFail);
            boolean success = !autoFail[0] && r.total() >= dc;
            int dmg = damage == null ? 0 : success ? (halfOnSuccess ? damage.total() / 2 : 0) : damage.total();
            results.add(new SaveResult(token, r.total(), success, r.detail() + notes, dmg));
            String line = "\n  " + (success ? "PASSOU " : "FALHOU ") + token.getName() + ": "
                    + (autoFail[0] ? "falha automática" : String.valueOf(r.total()))
                    + (dmg > 0 ? " -> " + dmg + " de dano" : "");
            if (token.isHiddenFromPlayers()) gm.append(line); else pub.append(line);
        }
        port.announce(pub.toString());
        if (gm.length() > 0) port.announceGm(title + " (ocultos):" + gm);
        for (SaveResult res : results) {
            if (res.damage() > 0) applyTypedDamage(res.token(), res.damage(), damageType);
        }
        return results;
    }

    // ------------------------------------------------------------------
    // Descanso do grupo (fichas ligadas aos tokens da cena)
    // ------------------------------------------------------------------

    private List<CharacterSheet> sheetsOnScene() {
        java.util.LinkedHashMap<String, CharacterSheet> found = new java.util.LinkedHashMap<>();
        for (TokenModel t : scene().getTokens()) {
            linkedSheet(t).ifPresent(s -> found.putIfAbsent(s.getId(), s));
        }
        return new ArrayList<>(found.values());
    }

    /** Descanso longo pra todo personagem com token no mapa. */
    public List<CharacterSheet> groupLongRest() {
        if (!port.isGm()) {
            port.error("Só o mestre decreta o descanso do grupo.");
            return List.of();
        }
        List<CharacterSheet> sheets = sheetsOnScene();
        if (sheets.isEmpty()) {
            port.error("Nenhum personagem com token no mapa.");
            return sheets;
        }
        List<String> names = new ArrayList<>();
        for (CharacterSheet s : sheets) {
            s.longRest();
            afterSheetEditedOutside(s);
            names.add(s.getName());
        }
        port.announce("Descanso longo: " + String.join(", ", names)
                + " recuperam PV, dados de vida e espacos de magia (e -1 de exaustão).");
        return sheets;
    }

    /**
     * Descanso curto: cada personagem ferido gasta dados de vida enquanto
     * faltar pelo menos a media de um dado e ainda tiver dado sobrando
     * (rola o dado + mod. de CON, minimo 1 por dado). Quem quiser fazer
     * diferente gasta na propria ficha.
     */
    public List<CharacterSheet> groupShortRest() {
        if (!port.isGm()) {
            port.error("Só o mestre decreta o descanso do grupo.");
            return List.of();
        }
        List<CharacterSheet> sheets = sheetsOnScene();
        if (sheets.isEmpty()) {
            port.error("Nenhum personagem com token no mapa.");
            return sheets;
        }
        StringBuilder text = new StringBuilder("Descanso curto:");
        for (CharacterSheet s : sheets) {
            int con = CharacterSheet.abilityModifier(s.getConstitution());
            List<String> rolls = new ArrayList<>();
            int healed = 0;
            // gasta do maior dado que sobrou (multiclasse: d10 antes do d8) enquanto falta pelo menos a media dele
            while (s.getHpMax() != null && s.getHpCurrent() != null) {
                String die = s.hitDiceTotals().keySet().stream().filter(d -> s.hitDiceRemainingOf(d) > 0).findFirst().orElse(null);
                if (die == null) break;
                int sides = dieSides(die);
                if (s.getHpMax() - s.getHpCurrent() < sides / 2 + 1 || !s.spendHitDie(die)) break;
                RollResult r = rollOrZero("1d" + sides);
                int gain = Math.max(1, r.total() + con);
                healed += s.getHpMax() - s.getHpCurrent() < gain ? s.getHpMax() - s.getHpCurrent() : gain;
                s.heal(gain);
                rolls.add(die + ": " + gain);
            }
            List<String> recovered = s.recoverResources(false);
            if (!recovered.isEmpty()) {
                text.append("\n  ").append(s.getName()).append(" recupera ").append(String.join(", ", recovered));
                if (rolls.isEmpty()) afterSheetEditedOutside(s);
            }
            if (rolls.isEmpty()) {
                text.append("\n  ").append(s.getName()).append(": não precisou gastar dados de vida");
            } else {
                text.append("\n  ").append(s.getName()).append(": gastou ").append(rolls.size())
                        .append(rolls.size() == 1 ? " dado de vida (" : " dados de vida (").append(String.join(" + ", rolls))
                        .append(") = +").append(healed).append(" PV");
                afterSheetEditedOutside(s);
            }
        }
        port.announce(text.toString());
        return sheets;
    }

    static int dieSides(String hitDie) {
        try {
            return Integer.parseInt(hitDie.trim().toLowerCase().replace("d", ""));
        } catch (RuntimeException ex) {
            return 8;
        }
    }

    // ------------------------------------------------------------------
    // Paredes, portas e visao dinamica
    // ------------------------------------------------------------------

    private boolean requireGm(String what) {
        if (port.isGm()) return true;
        port.error("Só o mestre " + what + ".");
        return false;
    }

    private void publishWalls() {
        scenes.wallsChanged();
        if (port.isConnected()) port.sendWalls(scene());
    }

    public void addWall(com.vttcaseiro.model.Wall wall) {
        if (wall == null || wall.length() < 2 || !requireGm("desenha paredes")) return;
        if (scene().getWalls().size() >= SceneModel.MAX_WALLS) {
            port.error("Paredes demais nesta cena.");
            return;
        }
        scene().getWalls().add(wall);
        publishWalls();
    }

    public void removeWall(com.vttcaseiro.model.Wall wall) {
        if (wall == null || !requireGm("apaga paredes")) return;
        if (scene().getWalls().removeIf(w -> w.getId().equals(wall.getId()))) publishWalls();
    }

    public void clearWalls() {
        if (!requireGm("apaga paredes")) return;
        scene().getWalls().clear();
        publishWalls();
    }

    /** Qualquer um abre/fecha porta (trancada e secreta: so o mestre); pela rede quem alterna e o servidor. */
    public void toggleDoor(com.vttcaseiro.model.Wall wall) {
        if (wall == null || !wall.isDoor()) return;
        if (wall.gmOnly() && !port.isGm()) {
            port.error(wall.isLocked() ? "A porta está trancada." : "Não há porta aí.");
            return;
        }
        if (port.isConnected() && !port.isGm()) {
            port.sendDoorToggle(wall.getId());
            return;
        }
        scene().findWall(wall.getId()).ifPresent(w -> {
            w.setOpen(!w.isOpen());
            publishWalls();
        });
    }

    /** Muda o tipo/estado de uma parede (porta, janela, trancada, secreta). So o mestre. */
    public void editWall(com.vttcaseiro.model.Wall wall, java.util.function.Consumer<com.vttcaseiro.model.Wall> change) {
        if (wall == null || !requireGm("mexe nas paredes e portas")) return;
        scene().findWall(wall.getId()).ifPresent(w -> {
            change.accept(w);
            publishWalls();
        });
    }

    // ------------------------------------------------------------------
    // Luzes fixas no mapa (so o mestre mexe; viajam junto com as paredes)
    // ------------------------------------------------------------------

    public boolean addLight(com.vttcaseiro.model.MapLight light) {
        if (light == null || !requireGm("põe luzes no mapa")) return false;
        if (scene().getLights().size() >= SceneModel.MAX_LIGHTS) {
            port.error("Luzes demais nesta cena.");
            return false;
        }
        light.sanitize();
        scene().getLights().add(light);
        publishWalls();
        revealFogByVision();
        return true;
    }

    public void editLight(com.vttcaseiro.model.MapLight light, java.util.function.Consumer<com.vttcaseiro.model.MapLight> change) {
        if (light == null || !requireGm("mexe nas luzes")) return;
        scene().findLight(light.getId()).ifPresent(l -> {
            change.accept(l);
            l.sanitize();
            publishWalls();
            revealFogByVision();
        });
    }

    public void removeLight(com.vttcaseiro.model.MapLight light) {
        if (light == null || !requireGm("tira luzes")) return;
        if (scene().getLights().removeIf(l -> l.getId().equals(light.getId()))) publishWalls();
    }

    public void setVisionSettings(boolean enabled, boolean dark) {
        setVisionSettings(enabled, dark, scene().isDim(), scene().isFogByVision());
    }

    /** Visao dinamica, cena escura / em penumbra, e a neblina que a visao dos jogadores revela sozinha. */
    public void setVisionSettings(boolean enabled, boolean dark, boolean dim, boolean fogByVision) {
        if (!requireGm("liga a visão dinâmica")) return;
        scene().setVisionEnabled(enabled);
        scene().setDark(dark);
        scene().setDim(dim);
        scene().setFogByVision(fogByVision);
        publishWalls();
        revealFogByVision();
    }

    /** Alcance maximo (pes) da busca de casas quando a cena nao e escura (enxerga longe). */
    static final int FOG_REVEAL_CAP_FEET = 300;

    /**
     * Neblina pela visao: com a opcao ligada (e visao dinamica + neblina ativas), revela as casas que os tokens dos
     * jogadores enxergam agora (regra do 5.5e, ver VisionCheck). O que foi revelado fica revelado. So no computador
     * do mestre (quem manda na neblina). Devolve quantas casas novas abriram.
     */
    public int revealFogByVision() {
        SceneModel s = scene();
        if (!port.isGm() || !s.isFogByVision() || !s.isVisionEnabled() || !s.getFog().isEnabled()) return 0;
        List<TokenModel> eyes = new ArrayList<>();
        for (TokenModel t : s.getTokens()) {
            if (!t.isHiddenFromPlayers() && (t.getCharacterSheetId() != null || t.getOwnerId() != null)) eyes.add(t);
        }
        if (eyes.isEmpty()) return 0;
        com.vttcaseiro.vision.VisionCheck check = com.vttcaseiro.vision.VisionCheck.of(s, eyes);
        com.vttcaseiro.model.GridSettings g = s.getGrid();
        double cell = Math.max(10, g.getCellSize());
        double feetPerCell = g.getFeetPerCell() <= 0 ? 5 : g.getFeetPerCell();
        com.vttcaseiro.vision.Perception.Light ambient = com.vttcaseiro.vision.Perception.ambient(s);
        List<int[]> open = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();
        for (TokenModel t : eyes) {
            double[] c = check.center(t);
            double reachFeet = Math.min(FOG_REVEAL_CAP_FEET, com.vttcaseiro.vision.Perception.maxSightFeet(t, ambient));
            // luz de outros tokens tambem conta no escuro: alarga ate o cap pra nao perder a tocha do aliado
            if (ambient == com.vttcaseiro.vision.Perception.Light.DARK) {
                for (TokenModel l : s.getTokens()) if (l.lightReachFeet() > 0) reachFeet = FOG_REVEAL_CAP_FEET;
                for (com.vttcaseiro.model.MapLight l : s.getLights()) if (l.isOn() && l.reachFeet() > 0) reachFeet = FOG_REVEAL_CAP_FEET;
            }
            double reach = reachFeet / feetPerCell * cell + cell;
            if (g.isHex()) {
                for (int[] h : g.hexesCovering(c[0] + reach, c[1] + reach)) {
                    double[] hc = g.hexCenter(h[0], h[1]);
                    if (Math.abs(hc[0] - c[0]) > reach || Math.abs(hc[1] - c[1]) > reach) continue;
                    if (s.getFog().isRevealed(h[0], h[1]) || !seenKeys.add(h[0] + "," + h[1])) continue;
                    if (check.canSee(hc[0], hc[1])) open.add(h);
                }
            } else {
                int c0 = Math.max(0, (int) Math.floor((c[0] - reach) / cell));
                int c1 = (int) Math.floor((c[0] + reach) / cell);
                int r0 = Math.max(0, (int) Math.floor((c[1] - reach) / cell));
                int r1 = (int) Math.floor((c[1] + reach) / cell);
                for (int col = c0; col <= c1; col++) {
                    for (int row = r0; row <= r1; row++) {
                        if (s.getFog().isRevealed(col, row) || !seenKeys.add(col + "," + row)) continue;
                        if (check.canSee((col + 0.5) * cell, (row + 0.5) * cell)) open.add(new int[]{col, row});
                    }
                }
            }
        }
        if (open.isEmpty()) return 0;
        s.getFog().setCells(open, true);
        publishFog();
        return open.size();
    }

    // ------------------------------------------------------------------
    // Pins (pontos de informacao / atalhos pra outras cenas) -- so o mestre mexe
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Objetos soltos no mapa (so o mestre mexe)
    // ------------------------------------------------------------------

    private void publishObjects() {
        scenes.objectsChanged();
        if (port.isConnected()) port.sendObjects(scene());
    }

    public boolean addObject(com.vttcaseiro.model.MapObject o) {
        if (o == null || !requireGm("coloca objetos")) return false;
        if (scene().getObjects().size() >= SceneModel.MAX_OBJECTS) {
            port.error("Objetos demais nesta cena.");
            return false;
        }
        o.sanitize();
        if (o.getAssetId() == null) return false;
        scene().getObjects().add(o);
        publishObjects();
        return true;
    }

    /** Depois de mexer (mover, girar, tamanho, ocultar) num objeto que ja esta na cena. */
    public void updateObject(com.vttcaseiro.model.MapObject o) {
        if (o == null || !requireGm("mexe nos objetos")) return;
        o.sanitize();
        publishObjects();
    }

    public void removeObject(com.vttcaseiro.model.MapObject o) {
        if (o == null || !requireGm("apaga objetos")) return;
        if (scene().getObjects().removeIf(x -> x.getId().equals(o.getId()))) publishObjects();
    }

    /** Traz pra frente (true) ou manda pro fundo (false). */
    public void reorderObject(com.vttcaseiro.model.MapObject o, boolean toFront) {
        if (o == null || !requireGm("mexe nos objetos")) return;
        List<com.vttcaseiro.model.MapObject> list = scene().getObjects();
        if (!list.remove(o)) return;
        if (toFront) list.add(o);
        else list.add(0, o);
        publishObjects();
    }

    public void applyRemoteObjects(List<com.vttcaseiro.model.MapObject> objects) {
        scene().setObjects(objects);
        scenes.objectsChanged();
    }

    private void publishPins() {
        scenes.pinsChanged();
        if (port.isConnected()) port.sendPins(scene());
    }

    public boolean addPin(com.vttcaseiro.model.MapPin pin) {
        if (pin == null || !requireGm("cria pins")) return false;
        if (scene().getPins().size() >= SceneModel.MAX_PINS) {
            port.error("Pins demais nesta cena.");
            return false;
        }
        pin.sanitize();
        scene().getPins().add(pin);
        publishPins();
        return true;
    }

    /** Depois de editar titulo/texto/link de um pin que ja esta na cena. */
    public void updatePin(com.vttcaseiro.model.MapPin pin) {
        if (pin == null || !requireGm("edita pins")) return;
        pin.sanitize();
        publishPins();
    }

    public void movePin(com.vttcaseiro.model.MapPin pin, double x, double y) {
        if (pin == null || !requireGm("move pins")) return;
        pin.setPosition(x, y);
        publishPins();
    }

    public void removePin(com.vttcaseiro.model.MapPin pin) {
        if (pin == null || !requireGm("apaga pins")) return;
        if (scene().getPins().removeIf(p -> p.getId().equals(pin.getId()))) publishPins();
    }

    public void applyRemotePins(List<com.vttcaseiro.model.MapPin> pins) {
        scene().setPins(pins);
        scenes.pinsChanged();
    }

    /** Luzes fixas que chegaram pela rede (vem logo antes das paredes). */
    public void applyRemoteLights(List<com.vttcaseiro.model.MapLight> lights) {
        if (lights != null) scene().setLights(lights);
    }

    public void applyRemoteWalls(List<com.vttcaseiro.model.Wall> walls, Boolean enabled, Boolean dark) {
        scene().setWalls(walls);
        if (enabled != null) scene().setVisionEnabled(enabled);
        if (dark != null) scene().setDark(dark);
        scenes.wallsChanged();
    }

    /** Visao no escuro e luz do token (dono ou mestre). */
    /** Nova imagem no token; se ele e de uma ficha, o retrato da ficha muda junto (e os outros tokens dela). */
    public void setTokenImage(TokenModel token, String assetId, String imagePath) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        token.setImageAssetId(assetId);
        token.setImagePath(imagePath);
        publishTokenUpdate(token);
        Optional<CharacterSheet> sheet = linkedSheet(token);
        if (sheet.isPresent() && assetId != null && canEditSheet(sheet.get())) {
            sheet.get().setPortraitImageAssetId(assetId);
            if (imagePath != null) sheet.get().setPortraitImagePath(imagePath);
            afterSheetEditedOutside(sheet.get());
        }
    }

    /** Muda campos do token (altura, direcao, sentidos, luz) e manda pra mesa. Dono ou mestre. */
    public void editToken(TokenModel token, java.util.function.Consumer<TokenModel> change) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        change.accept(token);
        publishTokenUpdate(token);
        revealFogByVision();
    }

    public void setTokenVision(TokenModel token, int visionFeet, int lightFeet) {
        if (!canControl(token)) {
            port.error("Você não controla " + token.getName() + ".");
            return;
        }
        token.setVisionFeet(visionFeet);
        token.setLightFeet(lightFeet);
        publishTokenUpdate(token);
    }

    // ------------------------------------------------------------------
    // Desenhos no mapa
    // ------------------------------------------------------------------

    public void addDrawing(com.vttcaseiro.model.Drawing drawing) {
        if (drawing == null || drawing.getPoints().size() < 2) return;
        if (com.vttcaseiro.model.Drawing.TEXT.equals(drawing.getKind()) && drawing.getText().isBlank()) return;
        drawing.setOwnerId(port.myPlayerId());
        if (!scene().addDrawing(drawing)) {
            port.error("Esta cena já tem desenhos demais -- apague alguns.");
            return;
        }
        scenes.drawingsChanged();
        if (port.isConnected()) port.sendDrawingAdd(drawing);
    }

    /** Quem desenhou ou o mestre. */
    public boolean canErase(com.vttcaseiro.model.Drawing drawing) {
        return port.isGm() || !port.isConnected() || java.util.Objects.equals(drawing.getOwnerId(), port.myPlayerId());
    }

    public void removeDrawing(com.vttcaseiro.model.Drawing drawing) {
        if (drawing == null) return;
        if (!canErase(drawing)) {
            port.error("Só quem desenhou (ou o mestre) apaga esse desenho.");
            return;
        }
        if (scene().removeDrawing(drawing.getId())) {
            scenes.drawingsChanged();
            if (port.isConnected()) port.sendDrawingRemove(drawing.getId());
        }
    }

    /** Mestre: apaga todos. Jogador: so os seus. Devolve quantos. */
    public int clearDrawings() {
        String owner = port.isGm() ? null : port.myPlayerId();
        int n = scene().clearDrawings(owner);
        if (n > 0) scenes.drawingsChanged();
        if (port.isConnected()) port.sendDrawingClear(owner);
        return n;
    }

    public void applyRemoteDrawingAdd(com.vttcaseiro.model.Drawing drawing) {
        if (drawing != null && scene().addDrawing(drawing)) scenes.drawingsChanged();
    }

    public void applyRemoteDrawingRemove(String id) {
        if (scene().removeDrawing(id)) scenes.drawingsChanged();
    }

    public void applyRemoteDrawingClear(String ownerId) {
        if (scene().clearDrawings(ownerId) > 0) scenes.drawingsChanged();
    }

    public Optional<TokenModel> currentTurnToken() {
        Encounter e = scene().getEncounter();
        return e.isActive() ? scene().findToken(e.getCurrentTokenId()) : Optional.empty();
    }

    static String d20(int modifier) {
        return modifier == 0 ? "1d20" : "1d20" + (modifier > 0 ? "+" + modifier : String.valueOf(modifier));
    }

    private static RollResult rollOrZero(String expression) {
        try {
            return DiceRoller.roll(expression);
        } catch (DiceException ex) {
            return new RollResult(0, expression + " invalido");
        }
    }
}
