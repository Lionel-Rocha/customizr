package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.mob.MobSheet;

import java.util.Map;

/**
 * Liga os campos do PDF (ficha de modelo) aos dados que o programa usa no mapa: nome, PV, CA, iniciativa,
 * deslocamento (os "papeis" do modelo). Vale nos dois sentidos: escrever o PV no PDF muda o token; dano no token
 * muda o PV no PDF.
 */
public final class TemplateSync {

    private TemplateSync() {
    }

    /** Campos do PDF -> ficha de personagem (o que o token e o rastreador leem). */
    public static void toCharacter(CharacterSheet s) {
        SheetTemplate t = s.getTemplate();
        if (t == null) return;
        Map<String, String> v = s.getTemplateValues();
        String name = t.valueOfRole(v, SheetTemplate.ROLE_NAME);
        if (name != null && !name.isBlank()) s.setName(name.trim());
        Integer max = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_HP_MAX));
        Integer hp = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_HP));
        if (max != null) s.setHpMax(Math.max(0, max));
        if (hp != null) s.setHpCurrent(hp);
        else if (max != null && s.getHpCurrent() == null) s.setHpCurrent(max);
        if (hp != null && max == null && s.getHpMax() == null) s.setHpMax(hp);
        Integer ac = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_AC));
        if (ac != null) s.setArmorClass(ac);
        Integer init = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_INIT));
        if (init != null) s.setInitiativeBonus(init - CharacterSheet.abilityModifier(s.getDexterity()));
        String speed = t.valueOfRole(v, SheetTemplate.ROLE_SPEED);
        if (speed != null && !speed.isBlank()) s.setSpeed(speed.trim());
    }

    /** Ficha de personagem -> campos do PDF (o PV mudou por dano/cura no token). Devolve true se mudou algo. */
    public static boolean fromCharacter(CharacterSheet s) {
        SheetTemplate t = s.getTemplate();
        if (t == null || s.getHpCurrent() == null) return false;
        String field = t.getRoles().get(SheetTemplate.ROLE_HP);
        if (field == null) return false;
        Integer inPdf = SheetTemplate.intOf(s.getTemplateValues().get(field));
        if (inPdf != null && inPdf.equals(s.getHpCurrent())) return false;
        s.getTemplateValues().put(field, String.valueOf(s.getHpCurrent()));
        return true;
    }

    /** Campos do PDF -> ficha de monstro (nome, PV, CA, deslocamento). */
    public static void toMob(MobSheet m) {
        SheetTemplate t = m.getTemplate();
        if (t == null) return;
        Map<String, String> v = m.getTemplateValues();
        String name = t.valueOfRole(v, SheetTemplate.ROLE_NAME);
        if (name != null && !name.isBlank()) m.setName(name.trim());
        Integer max = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_HP_MAX));
        Integer hp = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_HP));
        Integer pv = max != null ? max : hp;
        if (pv != null) m.setHitPointsAverage(Math.max(1, pv));
        Integer ac = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_AC));
        if (ac != null) m.setArmorClass(ac);
        Integer speed = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_SPEED));
        if (speed != null) m.setSpeedWalk(Math.max(0, speed));
        Integer init = SheetTemplate.intOf(t.valueOfRole(v, SheetTemplate.ROLE_INIT));
        if (init != null) m.setDexterity(10 + 2 * init); // a iniciativa do monstro sai do modificador de Destreza
    }
}
