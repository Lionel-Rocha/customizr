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
        Character c = characterRepository.findById(characterId).orElseThrow(() -> notFound("Character"));
        String clean = c.getName().replaceAll("[^\\p{L}\\p{N} ._-]", "").trim();
        return (clean.isEmpty() ? "ficha" : clean) + ".pdf";
    }

    @Transactional(readOnly = true)
    public byte[] render(UUID characterId, String username) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> notFound("Character"));
        if (!character.getOwner().getName().equals(username)
                && !character.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("You cannot see this character");
        }
        CharacterSheetResponse sheet = CharacterSheetResponse.from(character);
        List<CharacterFeatureResponse> features = featureService.list(characterId, false, username);
        Inventory inventory = inventoryRepository.findByCharacter(character).orElse(null);
        try (Layout pdf = new Layout()) {
            write(pdf, character, sheet, features, inventory);
            return pdf.bytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the PDF");
        }
    }

    // ------------------------------------------------------------------ conteudo

    private void write(Layout pdf, Character character, CharacterSheetResponse s, List<CharacterFeatureResponse> features,
                       Inventory inventory) throws IOException {
        CharacterSheetResponse.DetailsInfo d = s.details();
        CharacterSheetResponse.CombatInfo c = s.combat();
        CharacterStory story = character.getSheet().getStory();

        // cabecalho
        pdf.title(s.name());
        String classes = s.classes().isEmpty() ? "Sem classe" : String.join(" / ", s.classes().stream()
                .map(cc -> cc.className() + " " + cc.level() + (cc.subclass() == null ? "" : " (" + cc.subclass().name() + ")")).toList());
        pdf.line(classes + "  ·  nível " + c.totalLevel());
        pdf.line(join("  ·  ", d.species() == null ? null : "Espécie: " + d.species().name(),
                d.background() == null ? null : "Antecedente: " + d.background().name(),
                story.getAlignment() == null ? null : "Tendência: " + story.getAlignment(),
                "XP: " + d.experiencePoints() + (d.experienceToNextLevel() == null ? "" : " (faltam " + d.experienceToNextLevel() + ")")));
        pdf.gap(6);

        // atributos
        if (s.attributes() != null) {
            int[] v = {s.attributes().strength(), s.attributes().dexterity(), s.attributes().constitution(),
                    s.attributes().intelligence(), s.attributes().wisdom(), s.attributes().charisma()};
            String[] labels = new String[6];
            String[] big = new String[6];
            String[] small = new String[6];
            AbilityScore[] order = AbilityScore.values();
            for (int i = 0; i < 6; i++) {
                labels[i] = ABILITY.get(order[i]);
                int mod = Math.floorDiv(v[i] - 10, 2);
                big[i] = signed(mod);
                small[i] = String.valueOf(v[i]);
            }
            pdf.boxes(labels, big, small);
        } else {
            pdf.line("Atributos ainda não definidos.");
        }

        // combate
        pdf.boxes(new String[]{"CA", "PV", "PV temp.", "Desloc.", "Iniciativa", "Proficiência"},
                new String[]{text(c.armorClass()), c.hpMax() == null ? "—" : c.hpCurrent() + "/" + c.hpMax(), text(c.hpTemp()),
                        (d.currentSpeedFt() != null ? d.currentSpeedFt() : c.speedFt() == null ? "—" : c.speedFt()) + " ft",
                        signed(c.initiative()), signed(c.proficiencyBonus())},
                new String[]{"", "", "", "", "", ""});
        pdf.line(join("  ·  ", "Dados de vida: " + d.hitDiceRemaining() + "/" + d.hitDiceTotal(),
                "Percepção passiva " + c.passivePerception(), "Intuição passiva " + d.passiveInsight(),
                "Investigação passiva " + d.passiveInvestigation(),
                d.heroicInspiration() ? "Inspiração Heroica: sim" : null));
        List<String> state = new ArrayList<>();
        d.conditions().stream().map(Enum::name).sorted().forEach(n -> state.add(CONDITION.getOrDefault(n, n)));
        if (d.exhaustion() > 0) state.add("Exaustão " + d.exhaustion());
        if (d.deathSaveSuccesses() + d.deathSaveFailures() > 0) {
            state.add("Testes contra a morte: " + d.deathSaveSuccesses() + " sucesso(s), " + d.deathSaveFailures() + " falha(s)");
        }
        if (!state.isEmpty()) pdf.line("Estado: " + String.join(", ", state));
        if (d.carryingCapacityLb() != null) {
            pdf.line("Carga: até " + d.carryingCapacityLb() + " lb · empurrar, arrastar ou erguer " + d.pushDragLiftLb() + " lb");
        }

        // salvaguardas e pericias (duas colunas)
        pdf.heading("Salvaguardas e perícias");
        List<String> left = new ArrayList<>();
        for (CharacterSheetResponse.SaveInfo sv : d.savingThrows()) {
            left.add((sv.proficient() ? "• " : "   ") + ABILITY.get(sv.ability()) + "  " + signed(sv.bonus()));
        }
        List<String> right = new ArrayList<>();
        d.skills().stream().sorted(Comparator.comparing(sk -> SKILL.get(sk.skill())))
                .forEach(sk -> right.add((sk.expertise() ? "•• " : sk.proficient() ? "• " : "   ") + SKILL.get(sk.skill())
                        + " (" + ABILITY.get(sk.ability()) + ")  " + signed(sk.bonus())));
        pdf.columns(left, right, 0.3f);
        pdf.small("• proficiente   •• especialização");

        // ataques
        if (!d.attacks().isEmpty()) {
            pdf.heading("Ataques");
            for (CharacterSheetResponse.AttackInfo a : d.attacks()) {
                pdf.line(a.name() + "  " + signed(a.attackBonus()) + "  ·  " + a.damage()
                        + (a.damageType() == null ? "" : " " + a.damageType()) + (a.notes() == null ? "" : "  ·  " + a.notes()));
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
            pdf.heading("História e anotações");
            if (!details.isEmpty()) pdf.line(String.join("  ·  ", details.stream().map(p -> p[0] + ": " + p[1]).toList()));
            for (String[] t : texts) {
                if (t[1] == null) continue;
                pdf.subheading(t[0]);
                pdf.paragraph(t[1]);
            }
        }
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
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
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

        void title(String text) throws IOException {
            write(bold, 20, MARGIN, text, ACCENT);
            y -= 24;
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

        void small(String text) throws IOException {
            ensure(12);
            write(regular, 8, MARGIN, text, Color.DARK_GRAY);
            y -= 11;
        }

        void gap(float h) {
            y -= h;
        }

        /** Texto corrido com quebra de linha (respeita as quebras do proprio texto). */
        void paragraph(String text) throws IOException {
            for (String line : wrap(safe(text), regular, 10, WIDTH)) {
                ensure(13);
                write(regular, 10, MARGIN, line, Color.BLACK);
                y -= 13;
            }
        }

        /** Duas colunas de linhas curtas; {@code split} = fracao da largura da esquerda. */
        void columns(List<String> left, List<String> right, float split) throws IOException {
            int rows = Math.max(left.size(), right.size());
            for (int i = 0; i < rows; i++) {
                ensure(13);
                if (i < left.size()) write(regular, 10, MARGIN, safe(left.get(i)), Color.BLACK);
                if (i < right.size()) write(regular, 10, MARGIN + WIDTH * split, safe(right.get(i)), Color.BLACK);
                y -= 13;
            }
        }

        /** Fileira de caixas iguais: rotulo pequeno em cima, valor grande, detalhe embaixo. */
        void boxes(String[] labels, String[] big, String[] small) throws IOException {
            float h = 46;
            ensure(h + 8);
            float gapX = 6;
            float w = (WIDTH - gapX * (labels.length - 1)) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                float x = MARGIN + i * (w + gapX);
                cs.setStrokingColor(ACCENT);
                cs.setLineWidth(0.8f);
                cs.addRect(x, y - h, w, h);
                cs.stroke();
                centered(regular, 8, x, w, y - 11, labels[i], Color.DARK_GRAY);
                centered(bold, 15, x, w, y - 29, big[i], Color.BLACK);
                if (small[i] != null && !small[i].isEmpty()) centered(regular, 8, x, w, y - 41, small[i], Color.DARK_GRAY);
            }
            y -= h + 16;
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
