package com.vttcaseiro.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.character.CharacterSheet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Importa uma ficha de personagem de um arquivo que o jogador ja tem:
 * <ul>
 *   <li>JSON exportado pelo proprio Customizr;</li>
 *   <li>JSON de ator do Foundry VTT (sistema dnd5e, "Export Data");</li>
 *   <li>JSON de personagem do D&amp;D Beyond;</li>
 *   <li>PDF preenchivel (ficha oficial de 5e, a do D&amp;D Beyond e parecidas).</li>
 * </ul>
 * A ficha volta sempre como uma ficha NOVA (id novo) junto com o que foi lido,
 * pra tela mostrar um resumo antes de salvar.
 */
public final class SheetImport {

    /** Maior arquivo aceito (PDFs de ficha tem poucas centenas de KB; JSON do D&D Beyond ~1 MB). */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    /** O resultado: a ficha montada, de onde veio e avisos pro jogador conferir. */
    public record Result(CharacterSheet sheet, String format, List<String> notes) {
        public String summary() {
            CharacterSheet s = sheet;
            StringBuilder b = new StringBuilder();
            b.append(s.getName());
            String cls = s.getClassName() == null || s.getClassName().isBlank() ? "" : " - " + s.getClassName();
            b.append(cls).append(" nivel ").append(s.getLevel()).append('\n');
            b.append("FOR ").append(s.getStrength()).append("  DES ").append(s.getDexterity())
                    .append("  CON ").append(s.getConstitution()).append("  INT ").append(s.getIntelligence())
                    .append("  SAB ").append(s.getWisdom()).append("  CAR ").append(s.getCharisma()).append('\n');
            b.append("PV ").append(s.getHpMax() == null ? "-" : s.getHpMax())
                    .append("   CA ").append(s.getArmorClass() == null ? "-" : s.getArmorClass())
                    .append("   Perícias treinadas: ").append(s.getSkillProficiencies().size())
                    .append("   Ataques: ").append(s.getAttacks().size())
                    .append("   Magias: ").append(s.getSpells().size())
                    .append("   Itens: ").append(s.getInventory().getItems().size());
            return b.toString();
        }
    }

    private SheetImport() {
    }

    /** Escolhe o leitor pela extensao (e pelo conteudo, no caso do JSON). */
    /** Mesma coisa que fromFile, com os bytes ja na mao (upload pelo navegador). */
    public static Result fromBytes(byte[] bytes, String fileName) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Arquivo grande demais (" + bytes.length / 1024 / 1024 + " MB).");
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") || startsWith(bytes, "%PDF")) return PdfSheetReader.read(bytes);
        return fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    public static Result fromFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_BYTES) throw new IOException("Arquivo grande demais (" + size / 1024 / 1024 + " MB).");
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] bytes = Files.readAllBytes(file);
        if (lower.endsWith(".pdf") || startsWith(bytes, "%PDF")) return PdfSheetReader.read(bytes);
        return fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean startsWith(byte[] b, String magic) {
        if (b.length < magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) if (b[i] != magic.charAt(i)) return false;
        return true;
    }

    /** Detecta qual JSON e (Customizr, Foundry ou D&D Beyond) e le. */
    public static Result fromJson(String json) throws IOException {
        if (json.startsWith("﻿")) json = json.substring(1);
        JsonElement tree;
        try {
            tree = JsonParser.parseString(json);
        } catch (RuntimeException ex) {
            throw new IOException("Não é um JSON válido: " + ex.getMessage());
        }
        if (!tree.isJsonObject()) throw new IOException("Esperava um objeto JSON (uma ficha).");
        JsonObject o = tree.getAsJsonObject();

        // D&D Beyond: {"data": {... "stats": [...]}} ou direto {"stats": [...], "classes": [...]}
        JsonObject ddb = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : o;
        if (ddb.has("stats") && ddb.get("stats").isJsonArray() && ddb.has("classes")) {
            return DdbSheetReader.read(ddb);
        }
        // Foundry: {"name", "type": "character", "system": {"abilities": ...}} (versoes antigas: "data")
        JsonObject sys = Json.obj(o, "system");
        if (sys == null) sys = Json.obj(o, "data");
        if (sys != null && sys.has("abilities")) {
            return FoundrySheetReader.read(o, sys);
        }
        // O nosso proprio formato.
        if (o.has("strength") || o.has("savingThrowProficiencies") || o.has("hitDie")) {
            CharacterSheet sheet = com.vttcaseiro.library.LibraryStorage.parseCharacterJson(json);
            sheet.assignNewId();
            sheet.setOwnerPlayerId(null);
            List<String> notes = new ArrayList<>();
            if (sheet.getPortraitImageAssetId() != null) {
                notes.add("O retrato aponta pra uma imagem de outra mesa; se não aparecer, escolha o retrato de novo.");
            }
            return new Result(sheet, "Customizr (JSON)", notes);
        }
        throw new IOException("Não reconheci esse JSON. Aceito: ficha exportada pelo Customizr, ator do Foundry VTT "
                + "(dnd5e) e personagem do D&D Beyond.");
    }
}
