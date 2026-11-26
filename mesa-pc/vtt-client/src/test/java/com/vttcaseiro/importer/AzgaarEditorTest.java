package com.vttcaseiro.importer;

import com.google.gson.Gson;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.SavedWorld;
import com.vttcaseiro.model.WorldEdits;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Editor do mundo do Azgaar (nomes, cores, populacao) e nomes do mapa como rotulos da mesa. */
class AzgaarEditorTest {

    static AzgaarJson.Result world() throws Exception {
        return AzgaarJson.read(new StringReader(AzgaarJsonTest.fakeFull()));
    }

    static WorldEdits edits() {
        WorldEdits e = new WorldEdits();
        WorldEdits.StateEdit se = new WorldEdits.StateEdit();
        se.name = "Ocidente";
        se.color = "#112233";
        e.states().put(1, se);
        WorldEdits.BurgEdit be = new WorldEdits.BurgEdit();
        be.name = "Cabo Branco";
        be.population = 12.5;
        e.burgs().put(1, be);
        return e;
    }

    @Test
    void appliesNamesColorsAndPopulationWithoutTouchingTheRest() throws Exception {
        AzgaarJson.Result raw = world();
        AzgaarJson.Result r = AzgaarEditor.apply(raw, edits());
        assertEquals("Ocidente", r.world().stateName(1));
        assertEquals("Reino do Oeste", r.world().states().get(0).fullName(), "nome completo sem edicao fica");
        assertEquals("Leste", r.world().stateName(2));
        assertEquals("Cabo Branco", r.world().burgs().get(0).name());
        assertEquals(12.5, r.world().burgs().get(0).population());
        assertEquals("Porto Leste", r.world().burgs().get(1).name());
        assertEquals("#112233", r.geometry().stateColors()[1]);
        assertEquals("#5b8bc9", r.geometry().stateColors()[2]);
        assertTrue(r.geometry().stateNames().contains("Ocidente"));
        assertTrue(r.world().journalText().contains("capital: Cabo Branco"), r.world().journalText());
        assertTrue(r.world().factions().get(1).enemies.contains("Ocidente"));
        // o original nao muda
        assertEquals("Oeste", raw.world().stateName(1));
        assertEquals("#c96b5b", raw.geometry().stateColors()[1]);
        assertSame(raw, AzgaarEditor.apply(raw, new WorldEdits()));
    }

    @Test
    void editsSurviveTheSaveFile() {
        SavedWorld sw = new SavedWorld("Ilha", "mundos/a.json.gz", "x", "Ilha");
        sw.setEdits(edits());
        sw.setStyle("POLITICO");
        SavedWorld back = new Gson().fromJson(new Gson().toJson(sw), SavedWorld.class);
        assertEquals("Ocidente", back.getEdits().states().get(1).name);
        assertEquals(12.5, back.getEdits().burgs().get(1).population);
        assertTrue(back.isDrawn());
        SavedWorld old = new Gson().fromJson("{\"name\":\"Velho\",\"mapFile\":\"mundos/v.map\"}", SavedWorld.class);
        assertFalse(old.isDrawn(), "mundo de antes desta versao nao e redesenhado");
        assertTrue(old.getEdits().isEmpty());
        assertTrue(old.getRegions().isEmpty());
    }

    @Test
    void namesBecomeLabelPinsScaledToTheSceneImage() throws Exception {
        AzgaarJson.Result r = world();
        List<MapPin> full = AzgaarEditor.labelPins(r.world(), r.geometry(), 0, 0, 800, 400, 1600, 800, 1600);
        List<MapPin> half = AzgaarEditor.labelPins(r.world(), r.geometry(), 0, 0, 800, 400, 1600, 800, 800);
        MapPin state = full.stream().filter(p -> "azgaar:state:1".equals(p.getRef())).findFirst().orElseThrow();
        assertEquals(MapPin.LABEL, state.getKind());
        assertEquals("OESTE", state.getTitle());
        assertEquals(MapPin.STYLE_TITLE, state.getLabelStyle());
        assertEquals(600, state.getX(), 3, "polo do reino (300) na imagem 2x");
        MapPin town = full.stream().filter(p -> "azgaar:burg:1".equals(p.getRef())).findFirst().orElseThrow();
        assertEquals("Ponta Oeste", town.getTitle());
        assertEquals(MapPin.STYLE_PLACE, town.getLabelStyle());
        assertTrue(town.getText().contains("Capital de Oeste"), town.getText());
        assertTrue(town.getY() < 380, "nome acima do ponto da cidade (y 190 * 2)");
        MapPin halfState = half.stream().filter(p -> "azgaar:state:1".equals(p.getRef())).findFirst().orElseThrow();
        assertEquals(state.getX() / 2, halfState.getX(), 0.01, "imagem reduzida: posicao acompanha");
        assertEquals(state.getLabelSize() / 2, halfState.getLabelSize(), 1);
        // sem nomes no desenho: a imagem muda
        byte[] with = AzgaarRender.png(r.world(), r.geometry(), 0, 0, 800, 400, 1600, 800, AzgaarRender.Style.RELEVO);
        byte[] without = AzgaarRender.png(r.world(), r.geometry(), 0, 0, 800, 400, 1600, 800, AzgaarRender.Style.RELEVO, false);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(without)));
        assertFalse(java.util.Arrays.equals(with, without));
    }

    @Test
    void renamesPinsByRefOrOldNameButKeepsManualChanges() throws Exception {
        AzgaarJson.Result raw = world();
        AzgaarJson.Result after = AzgaarEditor.apply(raw, edits());
        List<MapPin> pins = new ArrayList<>(AzgaarEditor.labelPins(raw.world(), raw.geometry(), 0, 0, 800, 400, 1600, 800, 1600));
        pins.addAll(raw.world().pins(1600, 800, 60, false));
        MapPin oldStyle = new MapPin(1, 1, "Ponta Oeste", "capital"); // pin de antes (sem ref)
        pins.add(oldStyle);
        MapPin manual = new MapPin(1, 1, "Porto Leste", "cidade");
        manual.setRef("azgaar:burg:2"); // nao foi editado
        pins.add(manual);
        MapPin renamedByHand = new MapPin(1, 1, "Minha Vila", "vila");
        renamedByHand.setRef("azgaar:burg:1");
        pins.add(renamedByHand);

        int n = AzgaarEditor.renamePins(pins, raw.world(), after.world());
        assertTrue(n >= 4, "rotulo do reino, rotulo e pin da cidade, pin antigo: " + n);
        assertTrue(pins.stream().anyMatch(p -> p.getTitle().equals("OCIDENTE") && p.isLabel()));
        assertEquals(0, pins.stream().filter(p -> p.getTitle().equals("Ponta Oeste")).count());
        assertEquals("Cabo Branco", oldStyle.getTitle());
        assertEquals("Porto Leste", manual.getTitle());
        assertEquals("Minha Vila", renamedByHand.getTitle(), "nome posto pelo mestre fica");
        MapPin cityPin = pins.stream().filter(p -> "azgaar:burg:1".equals(p.getRef()) && !p.isLabel() && p != renamedByHand).findFirst().orElseThrow();
        assertTrue(cityPin.getText().contains("Capital de Ocidente"), "cartao refeito: " + cityPin.getText());
    }

    @Test
    void renamesFactionsAndTheirAlliesAndEnemies() throws Exception {
        AzgaarJson.Result raw = world();
        AzgaarJson.Result after = AzgaarEditor.apply(raw, edits());
        List<Campaign.Faction> factions = raw.world().factions();
        Campaign.Faction other = new Campaign.Faction();
        other.name = "Guilda";
        other.enemies = "Oestelandia, Oeste (rival)";
        factions.add(other);
        int n = AzgaarEditor.renameFactions(factions, raw.world(), after.world());
        assertEquals(2, n);
        assertEquals("Reino do Oeste", factions.get(0).name, "nome completo nao mudou");
        assertEquals("Ocidente (rival)", factions.get(1).enemies);
        assertEquals("Oestelandia, Ocidente (rival)", other.enemies, "so o item inteiro");
    }

    @Test
    void labelPinFieldsAreSanitized() {
        MapPin p = new MapPin(0, 0, "Mar Grande", MapPin.LABEL);
        assertTrue(p.isLabel());
        assertEquals(24, p.getLabelSize());
        p.setLabelSize(9999);
        assertEquals(400, p.getLabelSize());
        p.setLabelSize(Double.NaN);
        p.sanitize();
        assertEquals(24, p.getLabelSize());
        p.setLabelStyle("qualquer");
        assertEquals(MapPin.STYLE_PLACE, p.getLabelStyle());
        MapPin back = new Gson().fromJson(new Gson().toJson(p), MapPin.class);
        assertEquals(MapPin.LABEL, back.getKind());
    }
}
