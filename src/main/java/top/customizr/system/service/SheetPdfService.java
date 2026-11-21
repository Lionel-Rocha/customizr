package top.customizr.system.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterFeatureResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.SharedKind;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterStory;
import top.customizr.system.model.Condition;
import top.customizr.system.model.Currency;
import top.customizr.system.model.Inventory;
import top.customizr.system.model.ProficiencySuggestions;
import top.customizr.system.model.ResourceRecharge;
import top.customizr.system.model.Skill;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.InventoryRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ficha em PDF (A4, texto em portugues como a tela): cabecalho, atributos, combate, salvaguardas e pericias,
 * ataques, magia, recursos, habilidades, proficiencias, equipamento e historia. As notas do mestre nao entram
 * (o PDF costuma ir pra mao do jogador).
 */
@Service
public class SheetPdfService {

    private static final Map<AbilityScore, String> ABILITY = new EnumMap<>(Map.of(
            AbilityScore.STRENGTH, "FOR", AbilityScore.DEXTERITY, "DES", AbilityScore.CONSTITUTION, "CON",
            AbilityScore.INTELLIGENCE, "INT", AbilityScore.WISDOM, "SAB", AbilityScore.CHARISMA, "CAR"));
    private static final Map<Skill, String> SKILL = new EnumMap<>(Skill.class);
    private static final Map<String, String> RESOURCE = new HashMap<>();
    private static final Map<ResourceRecharge, String> RECHARGE = new EnumMap<>(Map.of(
            ResourceRecharge.SHORT_REST, "descanso curto", ResourceRecharge.SHORT_REST_ONE, "1 no curto, tudo no longo",
            ResourceRecharge.LONG_REST, "descanso longo", ResourceRecharge.MANUAL, "manual"));
    private static final Map<String, String> CONDITION = new HashMap<>();

    static {
        String[][] skills = {{"ACROBATICS", "Acrobacia"}, {"ANIMAL_HANDLING", "Lidar com Animais"}, {"ARCANA", "Arcanismo"},
                {"ATHLETICS", "Atletismo"}, {"DECEPTION", "Enganação"}, {"HISTORY", "História"}, {"INSIGHT", "Intuição"},
                {"INTIMIDATION", "Intimidação"}, {"INVESTIGATION", "Investigação"}, {"MEDICINE", "Medicina"},
                {"NATURE", "Natureza"}, {"PERCEPTION", "Percepção"}, {"PERFORMANCE", "Atuação"}, {"PERSUASION", "Persuasão"},
                {"RELIGION", "Religião"}, {"SLEIGHT_OF_HAND", "Prestidigitação"}, {"STEALTH", "Furtividade"},
                {"SURVIVAL", "Sobrevivência"}};
        for (String[] s : skills) SKILL.put(Skill.valueOf(s[0]), s[1]);
        String[][] resources = {{"rage", "Fúria"}, {"bardic-inspiration", "Inspiração de Bardo"},
                {"channel-divinity", "Canalizar Divindade"}, {"divine-intervention", "Intervenção Divina"},
                {"wild-shape", "Forma Selvagem"}, {"second-wind", "Retomar o Fôlego"}, {"action-surge", "Surto de Ação"},
                {"indomitable", "Indomável"}, {"focus-points", "Pontos de Foco"}, {"ki", "Pontos de Ki"},
                {"uncanny-metabolism", "Metabolismo Sobrenatural"}, {"lay-on-hands", "Cura pelas Mãos"},
                {"favored-enemy", "Inimigo Favorito"}, {"stroke-of-luck", "Golpe de Sorte"},
                {"innate-sorcery", "Feitiçaria Inata"}, {"sorcery-points", "Pontos de Feitiçaria"},
                {"magical-cunning", "Astúcia Mágica"}, {"arcane-recovery", "Recuperação Arcana"}};
        for (String[] r : resources) RESOURCE.put(r[0], r[1]);
        String[][] conditions = {{"BLINDED", "Cego"}, {"CHARMED", "Enfeitiçado"}, {"DEAFENED", "Surdo"},
                {"FRIGHTENED", "Amedrontado"}, {"GRAPPLED", "Agarrado"}, {"INCAPACITATED", "Incapacitado"},
                {"INVISIBLE", "Invisível"}, {"PARALYZED", "Paralisado"}, {"PETRIFIED", "Petrificado"},
                {"POISONED", "Envenenado"}, {"PRONE", "Caído"}, {"RESTRAINED", "Impedido"}, {"STUNNED", "Atordoado"},
                {"UNCONSCIOUS", "Inconsciente"}};
        for (String[] c : conditions) CONDITION.put(c[0], c[1]);
    }

    private final CharacterRepository characterRepository;
    private final InventoryRepository inventoryRepository;
    private final CharacterFeatureService featureService;

    public SheetPdfService(CharacterRepository characterRepository, InventoryRepository inventoryRepository,
                           CharacterFeatureService featureService) {
        this.characterRepository = characterRepository;
        this.inventoryRepository = inventoryRepository;
        this.featureService = featureService;
    }

    /** Nome de arquivo pra baixar: "Aria.pdf" (so letras, numeros, espaco, ponto, hifen). */
    @Transactional(readOnly = true)
    public String fileName(UUID characterId) {
        Character c = characterRepository.findById(characterId).orElseThrow(() -> notFound("personagem"));
        String clean = c.getName().replaceAll("[^\\p{L}\\p{N} ._-]", "").trim();
        return (clean.isEmpty() ? "ficha" : clean) + ".pdf";
    }

    @Transactional(readOnly = true)
    public byte[] render(UUID characterId, String username) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> notFound("personagem"));
        if (!character.getOwner().getName().equals(username)
                && !character.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Você não pode ver este personagem");
        }
        CharacterSheetResponse sheet = CharacterSheetResponse.from(character);
        List<CharacterFeatureResponse> features = featureService.list(characterId, false, username);
        Inventory inventory = inventoryRepository.findByCharacter(character).orElse(null);
        try (Layout pdf = new Layout()) {
            write(pdf, character, sheet, features, inventory);
            return pdf.bytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não deu pra montar o PDF");
        }
    }

    // ------------------------------------------------------------------ conteudo

    private void write(Layout pdf, Character character, CharacterSheetResponse s, List<CharacterFeatureResponse> features,
                       Inventory inventory) throws IOException {
        CharacterSheetResponse.DetailsInfo d = s.details();
        CharacterSheetResponse.CombatInfo c = s.combat();
        CharacterStory story = character.getSheet().getStory();

        // ---------------- primeira pagina: o que se olha durante a sessao (como a frente da ficha do Firecast/2024)
        String classes = s.classes().isEmpty() ? "—" : String.join(" / ", s.classes().stream()
                .map(cc -> cc.className() + " " + cc.level() + (cc.subclass() == null ? "" : " (" + cc.subclass().name() + ")")).toList());
        pdf.banner(s.name(), new String[][]{
                {classes, "CLASSE E NÍVEL"},
                {d.background() == null ? "—" : d.background().name(), "ANTECEDENTE"},
                {character.getOwner().getName(), "JOGADOR"},
                {d.species() == null ? "—" : d.species().name(), "ESPÉCIE"},
                {story.getAlignment() == null ? "—" : story.getAlignment(), "TENDÊNCIA"},
                {d.experiencePoints() + (d.experienceToNextLevel() == null ? "" : "  (faltam " + d.experienceToNextLevel() + ")"),
                        "XP · NÍVEL " + c.totalLevel()}});

        Integer speed = d.currentSpeedFt() != null ? d.currentSpeedFt() : c.speedFt();
        pdf.strip(
                new String[]{"CLASSE DE ARMADURA", "INICIATIVA", "DESLOCAMENTO", "BÔNUS DE PROFICIÊNCIA",
                        "INSPIRAÇÃO HEROICA", "PERCEPÇÃO PASSIVA", "INTUIÇÃO PASSIVA", "INVESTIGAÇÃO PASSIVA"},
                new String[]{text(c.armorClass()), signed(c.initiative()), speed == null ? "—" : speed + " ft",
                        signed(c.proficiencyBonus()), d.heroicInspiration() ? "SIM" : "—", String.valueOf(c.passivePerception()),
                        String.valueOf(d.passiveInsight()), String.valueOf(d.passiveInvestigation())},
                new String[]{"", "", speed == null ? "" : meters(speed), "", "", "", "", ""});

        // atributos: cada caixa com modificador, valor, salvaguarda e as pericias daquele atributo
        List<AbilityBlock> blocks = new ArrayList<>();
        int[] scores = s.attributes() == null ? null : new int[]{s.attributes().strength(), s.attributes().dexterity(),
                s.attributes().constitution(), s.attributes().intelligence(), s.attributes().wisdom(), s.attributes().charisma()};
        for (AbilityScore ability : AbilityScore.values()) {
            CharacterSheetResponse.SaveInfo save = d.savingThrows().stream().filter(sv -> sv.ability() == ability).findFirst().orElse(null);
            List<Mark> lines = new ArrayList<>();
            if (save != null) lines.add(new Mark("Salvaguarda", signed(save.bonus()), save.proficient() ? 1 : 0, true));
            d.skills().stream().filter(sk -> sk.ability() == ability).sorted(Comparator.comparing(sk -> SKILL.get(sk.skill())))
                    .forEach(sk -> lines.add(new Mark(SKILL.get(sk.skill()), signed(sk.bonus()),
                            sk.expertise() ? 2 : sk.proficient() ? 1 : 0, false)));
            int score = scores == null ? 10 : scores[ability.ordinal()];
            blocks.add(new AbilityBlock(ABILITY_NAME.get(ability), scores == null ? "—" : signed(Math.floorDiv(score - 10, 2)),
                    scores == null ? "—" : String.valueOf(score), lines));
        }
        pdf.abilityGrid(blocks);

        // vida: PV, dados de vida, testes contra a morte
        pdf.vitals(c.hpMax(), c.hpCurrent(), c.hpTemp(), d.hitDiceRemaining() + "/" + d.hitDiceTotal(),
                hitDiceByDie(character), d.deathSaveSuccesses(), d.deathSaveFailures());
        List<String> state = new ArrayList<>();
        d.conditions().stream().map(Enum::name).sorted().forEach(n -> state.add(CONDITION.getOrDefault(n, n)));
        if (d.exhaustion() > 0) state.add("Exaustão " + d.exhaustion());
        pdf.line(join("   ·   ", state.isEmpty() ? "Condições: nenhuma" : "Condições: " + String.join(", ", state),
                d.carryingCapacityLb() == null ? null
                        : "Carga: até " + d.carryingCapacityLb() + " lb (empurrar, arrastar ou erguer " + d.pushDragLiftLb() + " lb)"));
        pdf.legend();

        // ataques
        if (!d.attacks().isEmpty()) {
            pdf.heading("Ataques");
            float[] cols = {0, 0.36f, 0.48f, 0.70f};
            pdf.row(new String[]{"NOME", "ACERTO", "DANO", "NOTAS"}, cols, true);
            for (CharacterSheetResponse.AttackInfo a : d.attacks()) {
                pdf.row(new String[]{a.name(), signed(a.attackBonus()),
                        a.damage() + (a.damageType() == null ? "" : " " + a.damageType()), a.notes() == null ? "" : a.notes()}, cols, false);
            }
        }

        // magia
        if (!d.spellSlots().isEmpty()) {
            pdf.heading("Espaços de magia");
            pdf.paragraph(String.join("   ", d.spellSlots().stream().map(sl -> (sl.pool().equals("PACT") ? "Pacto " : "")
                    + sl.level() + "º: " + (sl.total() - sl.spent()) + "/" + sl.total()).toList()));
        }
        List<String> spells = features.stream().filter(f -> f.kind() == SharedKind.SPELL)
                .map(f -> f.name() + (f.origin() == null ? "" : " (" + f.origin() + ")")).toList();
        if (!spells.isEmpty()) {
            pdf.heading("Magias");
            pdf.paragraph(String.join(", ", spells));
        }

        // recursos
        if (!d.resources().isEmpty()) {
            pdf.heading("Recursos");
            for (CharacterSheetResponse.ResourceInfo r : d.resources()) {
                String name = r.custom() ? r.name() : RESOURCE.getOrDefault(r.id().substring(r.id().lastIndexOf('.') + 1), r.name());
                pdf.line(name + "  " + r.remaining() + "/" + r.max() + ("HP".equals(r.unit()) ? " PV" : "")
                        + "  ·  " + RECHARGE.get(r.recharge()) + (r.notes() == null ? "" : "  ·  " + r.notes()));
            }
        }

        // habilidades e talentos
        List<String> traits = features.stream().filter(f -> f.kind() != SharedKind.SPELL && f.kind() != SharedKind.ITEM)
                .map(f -> f.name() + (f.origin() == null ? "" : " — " + f.origin())).distinct().toList();
        if (!traits.isEmpty()) {
            pdf.heading("Habilidades e talentos");
            for (String t : traits) pdf.line(t);
        }

        // proficiencias
        if (!d.languages().isEmpty() || !d.toolProficiencies().isEmpty() || !d.weaponProficiencies().isEmpty()
                || !d.armorProficiencies().isEmpty()) {
            pdf.heading("Idiomas e proficiências");
            labeled(pdf, "Idiomas", d.languages());
            labeled(pdf, "Ferramentas", d.toolProficiencies());
            labeled(pdf, "Armas", d.weaponProficiencies());
            labeled(pdf, "Armaduras", d.armorProficiencies());
        }

        // equipamento
        pdf.heading("Equipamento");
        if (inventory != null) {
            Map<Currency, Long> coins = inventory.getCoins().asMap();
            pdf.line("Moedas: " + coins.get(Currency.PLATINUM) + " pl, " + coins.get(Currency.GOLD) + " po, "
                    + coins.get(Currency.ELECTRUM) + " pe, " + coins.get(Currency.SILVER) + " pp, "
                    + coins.get(Currency.COPPER) + " pc  (total " + inventory.getMoneyGp().stripTrailingZeros().toPlainString().replace('.', ',') + " po)");
            List<String> items = inventory.getEntries().stream()
                    .sorted(Comparator.comparing(e -> e.getItem().getName(), String.CASE_INSENSITIVE_ORDER))
                    .map(e -> e.getItem().getName() + (e.getQuantity() > 1 ? " ×" + e.getQuantity() : "")).toList();
            pdf.paragraph(items.isEmpty() ? "Nenhum item." : String.join(", ", items));
        } else {
            pdf.line("Nenhum item.");
        }

        // historia
        List<String[]> details = new ArrayList<>();
        addPair(details, "Idade", story.getAge());
        addPair(details, "Altura", story.getHeight());
        addPair(details, "Peso", story.getWeight());
        addPair(details, "Olhos", story.getEyes());
        addPair(details, "Cabelo", story.getHair());
        addPair(details, "Pele", story.getSkin());
        String[][] texts = {{"Aparência", story.getAppearance()}, {"Traços de personalidade", story.getPersonality()},
                {"Ideais", story.getIdeals()}, {"Vínculos", story.getBonds()}, {"Defeitos", story.getFlaws()},
                {"História", story.getBackstory()}, {"Aliados e organizações", story.getAllies()}, {"Anotações", story.getNotes()}};
        boolean anyText = false;
        for (String[] t : texts) anyText |= t[1] != null;
        if (!details.isEmpty() || anyText) {
            pdf.keepTogether(140);   // nao deixa o titulo sozinho no pe da pagina
            pdf.heading("História e anotações");
            if (!details.isEmpty()) pdf.line(String.join("  ·  ", details.stream().map(p -> p[0] + ": " + p[1]).toList()));
            for (String[] t : texts) {
                if (t[1] == null) continue;
                pdf.subheading(t[0]);
                pdf.paragraph(t[1]);
            }
        }
    }

    /** Uma linha da caixa de atributo: salvaguarda (losango) ou pericia (bolinha); mark 0 nada, 1 proficiente, 2 especialista. */
    private record Mark(String label, String bonus, int mark, boolean save) {}

    private record AbilityBlock(String title, String modifier, String score, List<Mark> lines) {}

    private static final Map<AbilityScore, String> ABILITY_NAME = new EnumMap<>(Map.of(
            AbilityScore.STRENGTH, "FORÇA", AbilityScore.DEXTERITY, "DESTREZA", AbilityScore.CONSTITUTION, "CONSTITUIÇÃO",
            AbilityScore.INTELLIGENCE, "INTELIGÊNCIA", AbilityScore.WISDOM, "SABEDORIA", AbilityScore.CHARISMA, "CARISMA"));

    /** 30 ft = 9 m (5 ft = 1,5 m, como no livro em portugues). */
    private static String meters(int feet) {
        double m = feet * 0.3;
        return (m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m).replace('.', ',')) + " m";
    }

    /** "d10 2/4 · d8 3/3": os gastos saem do dado maior primeiro, como no descanso curto. */
    private static String hitDiceByDie(Character character) {
        Map<Integer, Integer> total = new java.util.TreeMap<>(Comparator.reverseOrder());
        for (var cc : character.getSheet().getClasses()) {
            total.merge(Math.max(1, cc.getDndClass().getHitDie()), cc.getLevel(), Integer::sum);
        }
        int spent = character.getSheet().getHitDiceSpent();
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : total.entrySet()) {
            int used = Math.min(spent, e.getValue());
            spent -= used;
            parts.add("d" + e.getKey() + " " + (e.getValue() - used) + "/" + e.getValue());
        }
        return String.join("  ·  ", parts);
    }

    private static void labeled(Layout pdf, String label, List<String> values) throws IOException {
        if (!values.isEmpty()) pdf.paragraph(label + ": " + String.join(", ", values));
    }

    private static void addPair(List<String[]> out, String label, String value) {
        if (value != null) out.add(new String[]{label, value});
    }

    private static String join(String sep, String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) out.add(p);
        return String.join(sep, out);
    }

    private static String signed(int v) {
        return (v >= 0 ? "+" : "") + v;
    }

    private static String text(Integer v) {
        return v == null ? "—" : String.valueOf(v);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " não encontrado");
    }

    // ------------------------------------------------------------------ diagramacao

    /** Paginas A4 com texto que quebra linha e muda de pagina sozinho. */
    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 40;
        private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private static final Color ACCENT = new Color(0x8A, 0x4B, 0x12);

        private final PDDocument doc = new PDDocument();
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream cs;
        private float y;
        private int pageNumber;

        Layout() throws IOException {
            newPage();
        }

        void heading(String text) throws IOException {
            ensure(34);
            y -= 8;
            write(bold, 12, MARGIN, text, ACCENT);
            cs.setStrokingColor(ACCENT);
            cs.setLineWidth(0.6f);
            cs.moveTo(MARGIN, y - 3);
            cs.lineTo(MARGIN + WIDTH, y - 3);
            cs.stroke();
            y -= 17;
        }

        void subheading(String text) throws IOException {
            ensure(26);
            write(bold, 10, MARGIN, text, Color.BLACK);
            y -= 13;
        }

        void line(String text) throws IOException {
            paragraph(text);
        }

        /** Muda de pagina se nao couber {@code h} (pra um titulo nao ficar sozinho no pe da pagina). */
        void keepTogether(float h) throws IOException {
            ensure(h);
        }

        /** Texto corrido com quebra de linha (respeita as quebras do proprio texto). */
        void paragraph(String text) throws IOException {
            for (String line : wrap(safe(text), regular, 10, WIDTH)) {
                ensure(13);
                write(regular, 10, MARGIN, line, Color.BLACK);
                y -= 13;
            }
        }

        // ---------------- blocos da primeira pagina (posicao fixa a partir do cursor)

        private static final Color FILL = new Color(0xF4, 0xEC, 0xE1);
        private static final Color LABEL_INK = new Color(0x55, 0x47, 0x3B);
        private static final float LABEL = 6.5f;

        /** Topo do bloco a partir do cursor (o cursor e a linha de base do proximo texto). */
        private float blockTop() {
            return y + 9;
        }

        /** Depois do bloco: o cursor vai pra linha de base logo abaixo, com {@code gap} de folga. */
        private void after(float bottom, float gap) {
            y = bottom - gap - 9;
        }

        /** Faixa do topo: nome grande a esquerda; a direita, grade 3 x 2 com valor em cima e rotulo embaixo. */
        void banner(String name, String[][] cells) throws IOException {
            float top = blockTop();
            float h = 64;
            float nameW = 190;
            rect(MARGIN, top - h, WIDTH, h, ACCENT, FILL, 1f);
            // nome: diminui a letra ate caber
            String n = safe(name);
            float size = 20;
            while (size > 11 && bold.getStringWidth(n) / 1000 * size > nameW - 16) size -= 0.5f;
            text(bold, size, MARGIN + 10, top - 30, fit(n, bold, size, nameW - 16), INK);
            hline(MARGIN + 10, MARGIN + nameW - 6, top - 38, LABEL_INK, 0.5f);
            text(bold, LABEL, MARGIN + 10, top - 47, "NOME DO PERSONAGEM", LABEL_INK);
            // grade
            float gx = MARGIN + nameW + 4;
            float gw = (WIDTH - nameW - 14) / 3;
            for (int i = 0; i < cells.length; i++) {
                float cx = gx + (i % 3) * gw;
                float cy = top - 8 - (i / 3) * 28;
                text(regular, 9.5f, cx + 4, cy - 10, fit(safe(cells[i][0]), regular, 9.5f, gw - 10), INK);
                hline(cx + 4, cx + gw - 6, cy - 13, LABEL_INK, 0.5f);
                text(bold, LABEL, cx + 4, cy - 21, fit(safe(cells[i][1]), bold, LABEL, gw - 10), LABEL_INK);
            }
            after(top - h, 8);
        }

        /** Fileira de caixinhas de resumo: rotulo (ate 2 linhas) em cima, valor grande, detalhe embaixo. */
        void strip(String[] labels, String[] values, String[] subs) throws IOException {
            float top = blockTop();
            float h = 48;
            float gapX = 5;
            float w = (WIDTH - gapX * (labels.length - 1)) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                float x = MARGIN + i * (w + gapX);
                rect(x, top - h, w, h, ACCENT, null, 0.8f);
                // palavra comprida (DESLOCAMENTO) diminui a letra em vez de quebrar no meio
                float size = LABEL;
                for (String word : labels[i].split(" ")) {
                    while (size > 5 && bold.getStringWidth(word) / 1000 * size > w - 6) size -= 0.25f;
                }
                List<String> label = wrap(labels[i], bold, size, w - 6);
                for (int l = 0; l < Math.min(2, label.size()); l++) {
                    centered(bold, size, x, w, top - 9 - l * 7.5f, label.get(l), LABEL_INK);
                }
                centered(bold, 14, x, w, top - 34, values[i], INK);
                if (subs[i] != null && !subs[i].isEmpty()) centered(regular, 7, x, w, top - 43, subs[i], LABEL_INK);
            }
            after(top - h, 8);
        }

        /** As seis caixas de atributo em 3 colunas x 2 linhas; a altura da linha acompanha a caixa com mais pericias. */
        void abilityGrid(List<AbilityBlock> blocks) throws IOException {
            float gapX = 8;
            float w = (WIDTH - 2 * gapX) / 3;
            float lineH = 12.5f;
            float top = blockTop();
            for (int row = 0; row < 2; row++) {
                int maxLines = 0;
                for (int col = 0; col < 3; col++) maxLines = Math.max(maxLines, blocks.get(row * 3 + col).lines().size());
                float h = 16 + Math.max(50, maxLines * lineH + 8);
                for (int col = 0; col < 3; col++) {
                    AbilityBlock b = blocks.get(row * 3 + col);
                    float x = MARGIN + col * (w + gapX);
                    rect(x, top - h, w, h, ACCENT, null, 1f);
                    rect(x, top - 16, w, 16, ACCENT, ACCENT, 1f);
                    centered(bold, 8.5f, x, w, top - 11.5f, b.title(), Color.WHITE);
                    // modificador grande + valor
                    float colW = 46;
                    centered(bold, 22, x, colW, top - 44, b.modifier(), INK);
                    rect(x + 11, top - 66, colW - 22, 14, LABEL_INK, FILL, 0.6f);
                    centered(bold, 9, x + 11, colW - 22, top - 62, b.score(), INK);
                    // salvaguarda e pericias
                    float lx = x + colW + 4;
                    float ly = top - 16 - 11;
                    for (int i = 0; i < b.lines().size(); i++) {
                        Mark m = b.lines().get(i);
                        if (m.save()) marker(lx + 3, ly + 2.6f, m.mark(), true);
                        else marker(lx + 3, ly + 2.6f, m.mark(), false);
                        text(m.save() ? bold : regular, 8.5f, lx + 10, ly, fit(safe(m.label()), regular, 8.5f, w - colW - 42), INK);
                        rightAligned(bold, 8.5f, x + w - 6, ly, m.bonus(), INK);
                        if (m.save() && b.lines().size() > 1) hline(lx, x + w - 6, ly - 3.5f, FILL.darker(), 0.6f);
                        ly -= lineH;
                    }
                }
                top -= h + 6;
            }
            after(top + 6, 8);
        }

        /** PV maximo, atual e temporario; dados de vida (total e por dado); testes contra a morte em bolinhas. */
        void vitals(Integer hpMax, Integer hpCurrent, Integer hpTemp, String hitDice, String hitDiceDetail,
                    int successes, int failures) throws IOException {
            float top = blockTop();
            float h = 58;
            float gapX = 6;
            float[] widths = {78, 96, 78, 150};
            float x = MARGIN;
            String[][] boxes = {{"PV MÁXIMO", SheetPdfService.text(hpMax)}, {"PV ATUAL", SheetPdfService.text(hpCurrent)}, {"PV TEMPORÁRIO", SheetPdfService.text(hpTemp)},
                    {"DADOS DE VIDA", hitDice}};
            for (int i = 0; i < boxes.length; i++) {
                float w = widths[i];
                rect(x, top - h, w, h, ACCENT, i == 1 ? FILL : null, i == 1 ? 1.4f : 0.8f);
                centered(bold, LABEL, x, w, top - 10, boxes[i][0], LABEL_INK);
                centered(bold, i == 1 ? 22 : 16, x, w, top - (i == 1 ? 38 : 35), boxes[i][1], INK);
                if (i == 3) centered(regular, 7.5f, x, w, top - 50, fit(safe(hitDiceDetail), regular, 7.5f, w - 8), LABEL_INK);
                x += w + gapX;
            }
            float w = MARGIN + WIDTH - x;
            rect(x, top - h, w, h, ACCENT, null, 0.8f);
            centered(bold, LABEL, x, w, top - 10, "TESTES CONTRA A MORTE", LABEL_INK);
            text(bold, 7, x + 6, top - 28, "SUCESSOS", INK);
            text(bold, 7, x + 6, top - 45, "FALHAS", INK);
            for (int i = 0; i < 3; i++) {
                circle(x + w - 34 + i * 12, top - 25.5f, 4f, i < successes, 1f);
                circle(x + w - 34 + i * 12, top - 42.5f, 4f, i < failures, 1f);
            }
            after(top - h, 8);
        }

        /** Legenda dos marcadores, desenhada (a fonte padrao do PDF nao tem esses simbolos). */
        void legend() throws IOException {
            ensure(12);
            float x = MARGIN;
            marker(x + 3, y + 2.6f, 1, true);
            text(regular, 7.5f, x + 10, y, "salvaguarda", LABEL_INK);
            x += 62;
            marker(x + 3, y + 2.6f, 0, false);
            text(regular, 7.5f, x + 10, y, "perícia", LABEL_INK);
            x += 44;
            marker(x + 3, y + 2.6f, 1, false);
            text(regular, 7.5f, x + 10, y, "proficiente", LABEL_INK);
            x += 58;
            marker(x + 3, y + 2.6f, 2, false);
            text(regular, 7.5f, x + 10, y, "especialização", LABEL_INK);
            y -= 13;
        }

        /** Linha de tabela: celulas nas posicoes {@code cols} (fracao da largura); cabecalho em negrito pequeno. */
        void row(String[] cells, float[] cols, boolean header) throws IOException {
            ensure(14);
            for (int i = 0; i < cells.length; i++) {
                float x = MARGIN + WIDTH * cols[i];
                float next = i + 1 < cols.length ? MARGIN + WIDTH * cols[i + 1] : MARGIN + WIDTH;
                PDType1Font f = header ? bold : regular;
                float size = header ? LABEL + 0.5f : 9.5f;
                text(f, size, x, y, fit(safe(cells[i]), f, size, next - x - 6), header ? LABEL_INK : INK);
            }
            if (!header) hline(MARGIN, MARGIN + WIDTH, y - 4, FILL.darker(), 0.5f);
            y -= header ? 12 : 14;
        }

        // ---------------- primitivas

        private static final Color INK = new Color(0x22, 0x1A, 0x14);

        private void rect(float x, float yBottom, float w, float h, Color stroke, Color fill, float lineWidth) throws IOException {
            if (fill != null) {
                cs.setNonStrokingColor(fill);
                cs.addRect(x, yBottom, w, h);
                cs.fill();
            }
            cs.setStrokingColor(stroke);
            cs.setLineWidth(lineWidth);
            cs.addRect(x, yBottom, w, h);
            cs.stroke();
        }

        private void hline(float x1, float x2, float yy, Color color, float lineWidth) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(lineWidth);
            cs.moveTo(x1, yy);
            cs.lineTo(x2, yy);
            cs.stroke();
        }

        private void circle(float cx, float cy, float r, boolean filled, float lineWidth) throws IOException {
            float k = 0.5523f * r;
            cs.moveTo(cx + r, cy);
            cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
            cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
            cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
            cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
            cs.closePath();
            cs.setStrokingColor(INK);
            cs.setNonStrokingColor(INK);
            cs.setLineWidth(lineWidth);
            if (filled) cs.fillAndStroke();
            else cs.stroke();
        }

        /** Losango (salvaguarda) ou bolinha (pericia): vazio, cheio (proficiente) ou cheio com anel (especialista). */
        private void marker(float cx, float cy, int mark, boolean diamond) throws IOException {
            if (diamond) {
                float r = 3.4f;
                cs.moveTo(cx, cy + r);
                cs.lineTo(cx + r, cy);
                cs.lineTo(cx, cy - r);
                cs.lineTo(cx - r, cy);
                cs.closePath();
                cs.setStrokingColor(INK);
                cs.setNonStrokingColor(INK);
                cs.setLineWidth(0.8f);
                if (mark > 0) cs.fillAndStroke();
                else cs.stroke();
            } else {
                circle(cx, cy, 2.6f, mark > 0, 0.8f);
            }
            if (mark == 2) circle(cx, cy, 4.4f, false, 0.6f);
        }

        private void text(PDType1Font font, float size, float x, float baseline, String text, Color color) throws IOException {
            write(font, size, x, safe(text), color, baseline);
        }

        private void rightAligned(PDType1Font font, float size, float right, float baseline, String text, Color color) throws IOException {
            String t = safe(text);
            write(font, size, right - font.getStringWidth(t) / 1000 * size, t, color, baseline);
        }

        /** Corta com "…" se nao couber na largura. */
        private String fit(String text, PDType1Font font, float size, float width) throws IOException {
            if (font.getStringWidth(text) / 1000 * size <= width) return text;
            String t = text;
            while (t.length() > 1 && font.getStringWidth(t + "…") / 1000 * size > width) t = t.substring(0, t.length() - 1);
            return t.stripTrailing() + "…";
        }

        byte[] bytes() throws IOException {
            footer();
            cs.close();
            cs = null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }

        @Override
        public void close() throws IOException {
            if (cs != null) cs.close();
            doc.close();
        }

        private void ensure(float h) throws IOException {
            if (y - h < MARGIN + 14) {
                footer();
                cs.close();
                newPage();
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
            pageNumber++;
        }

        private void footer() throws IOException {
            write(regular, 8, MARGIN, "Customizr · página " + pageNumber, Color.GRAY, MARGIN - 14);
        }

        private void centered(PDType1Font font, float size, float x, float w, float baseline, String text, Color color) throws IOException {
            String t = safe(text);
            float tw = font.getStringWidth(t) / 1000 * size;
            write(font, size, x + Math.max(0, (w - tw) / 2), t, color, baseline);
        }

        private void write(PDType1Font font, float size, float x, String text, Color color) throws IOException {
            write(font, size, x, safe(text), color, y);
        }

        private void write(PDType1Font font, float size, float x, String text, Color color, float baseline) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, baseline);
            cs.showText(text);
            cs.endText();
        }

        /** Tira as tags do 5etools e troca o que a fonte padrao do PDF nao tem (emoji, setas) por "?". */
        private String safe(String text) {
            if (text == null) return "";
            String t = ProficiencySuggestions.untag(text).replace('\t', ' ').replace('\r', ' ');
            StringBuilder out = new StringBuilder();
            t.codePoints().forEach(cp -> {
                if (cp == '\n') { out.append('\n'); return; }
                String ch = new String(java.lang.Character.toChars(cp));
                try {
                    regular.encode(ch);
                    out.append(ch);
                } catch (IOException | IllegalArgumentException ex) {
                    out.append(cp == '→' ? "->" : cp == '—' ? "-" : "?");
                }
            });
            return out.toString();
        }

        private static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String para : text.split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (String word : para.split(" ")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (font.getStringWidth(candidate) / 1000 * size <= width) {
                        line.setLength(0);
                        line.append(candidate);
                        continue;
                    }
                    if (!line.isEmpty()) lines.add(line.toString());
                    line.setLength(0);
                    // palavra maior que a linha: corta
                    String rest = word;
                    while (font.getStringWidth(rest) / 1000 * size > width && rest.length() > 1) {
                        int cut = rest.length() - 1;
                        while (cut > 1 && font.getStringWidth(rest.substring(0, cut)) / 1000 * size > width) cut--;
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    line.append(rest);
                }
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
