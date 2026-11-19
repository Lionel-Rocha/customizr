package com.vttcaseiro.vision;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

/**
 * Regras de luz e visao do D&amp;D 5.5e (Livro do Jogador 2024), sem tela:
 * <ul>
 *   <li>Tres niveis de luz: luz plena, penumbra e escuridao. A cena da o nivel de fundo (clara, penumbra, escura);
 *       cada fonte de luz ilumina com luz plena ate um raio e com penumbra ate outro.</li>
 *   <li>Visao no escuro: dentro do alcance, penumbra conta como luz plena e escuridao como penumbra.</li>
 *   <li>Visao as cegas e visao verdadeira: dentro do alcance, enxerga como se fosse luz plena (parede bloqueia).</li>
 *   <li>Sentido sismico: sente quem esta no chao dentro do alcance, mesmo atras de parede (ver {@link #tremorsenses}).</li>
 * </ul>
 * Penumbra deixa ver, com desvantagem em Percepcao; escuridao nao deixa ver.
 */
public final class Perception {

    public enum Light { DARK, DIM, BRIGHT }

    private Perception() {
    }

    /** Luz de fundo da cena. */
    public static Light ambient(SceneModel scene) {
        if (scene.isDark()) return Light.DARK;
        return scene.isDim() ? Light.DIM : Light.BRIGHT;
    }

    /** Luz de uma fonte num ponto a {@code distanceFeet} dela (linha de visao ja conferida). */
    public static Light fromSource(TokenModel source, double distanceFeet) {
        return fromSource(source.getLightFeet(), source.getDimLightFeet(), distanceFeet);
    }

    /** Fonte com luz plena ate {@code brightFeet} e penumbra por mais {@code dimFeet}. */
    public static Light fromSource(int brightFeet, int dimFeet, double distanceFeet) {
        if (brightFeet > 0 && distanceFeet <= brightFeet) return Light.BRIGHT;
        if (brightFeet + dimFeet > 0 && distanceFeet <= brightFeet + dimFeet) return Light.DIM;
        return Light.DARK;
    }

    public static Light max(Light a, Light b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /**
     * O que {@code viewer} enxerga num ponto com luz {@code light}, a {@code distanceFeet} dele (com linha de visao):
     * visao verdadeira ou as cegas no alcance = luz plena; visao no escuro no alcance sobe um nivel.
     */
    public static Light seen(Light light, double distanceFeet, TokenModel viewer) {
        if (viewer.getTruesightFeet() > 0 && distanceFeet <= viewer.getTruesightFeet()) return Light.BRIGHT;
        if (viewer.getBlindsightFeet() > 0 && distanceFeet <= viewer.getBlindsightFeet()) return Light.BRIGHT;
        if (viewer.getVisionFeet() > 0 && distanceFeet <= viewer.getVisionFeet()) {
            return light == Light.DARK ? Light.DIM : Light.BRIGHT;
        }
        return light;
    }

    /** Sentido sismico: os dois no chao (altura 0) e dentro do alcance; parede nao bloqueia. */
    public static boolean tremorsenses(TokenModel viewer, TokenModel target, double horizontalFeet) {
        return viewer.getTremorsenseFeet() > 0 && viewer.getElevationFeet() == 0 && target.getElevationFeet() == 0
                && horizontalFeet <= viewer.getTremorsenseFeet();
    }

    /** Distancia em pes contando a altura (voar 30 pes acima de quem esta a 40 pes = 50 pes). */
    public static double distanceFeet(double horizontalFeet, TokenModel a, TokenModel b) {
        double dz = a.getElevationFeet() - b.getElevationFeet();
        return Math.hypot(horizontalFeet, dz);
    }

    /** Alcance maximo em pes em que o token pode enxergar algo numa cena com esta luz de fundo. */
    public static double maxSightFeet(TokenModel viewer, Light ambient) {
        if (ambient != Light.DARK) return Double.MAX_VALUE;
        return Math.max(Math.max(viewer.getVisionFeet(), viewer.lightReachFeet()),
                Math.max(viewer.getTruesightFeet(), viewer.getBlindsightFeet()));
    }
}
