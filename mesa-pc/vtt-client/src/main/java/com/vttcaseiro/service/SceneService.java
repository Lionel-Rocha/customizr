package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dono da cena (mapa) atual. Antes, o Main.java mexia direto no
 * SceneModel em uma dezena de metodos diferentes (mover token, adicionar
 * token, mudar grade...) sem nenhuma regra central -- cada lugar decidia
 * por conta propria o que era valido. Agora e tudo aqui, com validacao de
 * verdade, e quem precisar saber quando algo muda assina um
 * {@link SceneChangeListener} em vez de o Main.java ter que lembrar de
 * chamar `mapView.rebuildTokens()` manualmente depois de cada mutacao
 * (o jeito antigo, facil de esquecer um lugar e deixar a tela
 * dessincronizada do modelo).
 */
public class SceneService {
    private static final Log log = Log.of(SceneService.class);

    /** Quem quiser saber quando a cena muda implementa isto. Os metodos
     * default fazem nada -- quem so se importa com uma mudanca (ex.: so
     * token movido) nao precisa sobrescrever os outros quatro. */
    public interface SceneChangeListener {
        default void onSceneReplaced(SceneModel newScene) {
        }

        default void onTokenAdded(TokenModel token) {
        }

        default void onTokenMoved(TokenModel token) {
        }

        default void onTokenRemoved(TokenModel token) {
        }

        default void onGridChanged() {
        }

        default void onTokenUpdated(TokenModel token) {
        }

        /** Rodada/turno/efeitos com duracao mudaram. */
        default void onEncounterChanged() {
        }

        /** Neblina de guerra mudou. */
        default void onFogChanged() {
        }

        /** Desenhos do mapa mudaram. */
        default void onDrawingsChanged() {
        }

        /** Paredes, portas ou configuracao de visao mudaram. */
        default void onPinsChanged() {
        }

        default void onObjectsChanged() {
        }

        default void onWallsChanged() {
        }
    }

    private SceneModel scene;
    // CopyOnWrite: trocar de cena reconstroi o mapa, e a barra nova se inscreve aqui
    // DURANTE o aviso -- com ArrayList isso quebrava o loop (ConcurrentModification).
    private final List<SceneChangeListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public SceneService(SceneModel initialScene) {
        this.scene = initialScene;
    }

    public SceneModel getScene() {
        return scene;
    }

    public void addListener(SceneChangeListener listener) {
        listeners.add(listener);
    }

    public void replaceScene(SceneModel newScene) {
        this.scene = newScene;
        log.info("Cena trocada para: " + newScene.getName());
        for (SceneChangeListener l : listeners) {
            l.onSceneReplaced(newScene);
        }
    }

    public void addToken(TokenModel token) {
        scene.addToken(token);
        for (SceneChangeListener l : listeners) {
            l.onTokenAdded(token);
        }
    }

    /** Se o token nao existir (pode acontecer com uma mensagem de rede
     * atrasada chegando depois de uma troca de cena, por exemplo), so
     * registra um aviso e nao faz nada -- nao e um erro grave o bastante
     * pra lancar excecao e arriscar quebrar quem chamou isto. */
    public void moveToken(String tokenId, double x, double y) {
        findToken(tokenId).ifPresentOrElse(token -> {
            token.setX(x);
            token.setY(y);
            for (SceneChangeListener l : listeners) {
                l.onTokenMoved(token);
            }
        }, () -> log.warn("moveToken: token não encontrado: " + tokenId));
    }

    /** Aplica o estado (PV, condicoes, efeitos, iniciativa...) de um token
     * que chegou da rede ou foi editado aqui. */
    public void updateToken(TokenModel incoming) {
        if (incoming == null) return;
        findToken(incoming.getId()).ifPresent(token -> {
            if (token != incoming) token.copyStateFrom(incoming);
            for (SceneChangeListener l : listeners) {
                l.onTokenUpdated(token);
            }
        });
    }

    public void fogChanged() {
        for (SceneChangeListener l : listeners) {
            l.onFogChanged();
        }
    }

    public void wallsChanged() {
        for (SceneChangeListener l : listeners) {
            l.onWallsChanged();
        }
    }

    public void objectsChanged() {
        for (SceneChangeListener l : listeners) {
            l.onObjectsChanged();
        }
    }

    public void pinsChanged() {
        for (SceneChangeListener l : listeners) {
            l.onPinsChanged();
        }
    }

    public void drawingsChanged() {
        for (SceneChangeListener l : listeners) {
            l.onDrawingsChanged();
        }
    }

    public void encounterChanged() {
        for (SceneChangeListener l : listeners) {
            l.onEncounterChanged();
        }
    }

    public void removeToken(String tokenId) {
        findToken(tokenId).ifPresent(token -> {
            scene.removeToken(token);
            for (SceneChangeListener l : listeners) {
                l.onTokenRemoved(token);
            }
        });
    }

    public Optional<TokenModel> findToken(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return scene.getTokens().stream().filter(t -> id.equals(t.getId())).findFirst();
    }

    /** Sobe/desce o PV de um token, sem deixar passar de 0 nem do maximo
     * (se o maximo estiver definido) -- regra de negocio real, nao so um
     * setter cru. Antes isso nao existia em lugar nenhum; o PV podia
     * virar qualquer numero, inclusive negativo. */
    public void applyHpChange(TokenModel token, int delta) {
        int current = token.getCurrentHp() == null ? 0 : token.getCurrentHp();
        int next = current + delta;
        if (next < 0) {
            next = 0;
        }
        if (token.getMaxHp() != null && next > token.getMaxHp()) {
            next = token.getMaxHp();
        }
        token.setCurrentHp(next);
        for (SceneChangeListener l : listeners) {
            l.onTokenMoved(token); // reaproveita o mesmo evento -- a tela so precisa redesenhar o token
        }
    }

    /** @throws IllegalArgumentException se o tamanho nao for maior que 1 */
    public void setGridType(String type) {
        scene.getGrid().setType(type);
        for (SceneChangeListener l : listeners) {
            l.onGridChanged();
        }
    }

    /** Tamanho do mapa em casas (0 = automatico pela imagem). Avisa como mudanca de grade (o mapa redesenha). */
    public void setMapSizeCells(int width, int height) {
        scene.getGrid().setMapSizeCells(width, height);
        for (SceneChangeListener l : listeners) {
            l.onGridChanged();
        }
    }

    public void setGridCellSize(double size) {
        if (size <= 1) {
            throw new IllegalArgumentException("O tamanho da célula precisa ser maior que 1.");
        }
        scene.getGrid().setCellSize(size);
        for (SceneChangeListener l : listeners) {
            l.onGridChanged();
        }
    }

    /** Quantos pes uma celula representa (usado pela regua de medicao do
     * MapView -- ver GridSettings.feetPerCell) -- nao afeta o desenho da
     * grade em si (so o cellSize em pixels faz isso), so o numero que a
     * regua mostra.
     *
     * @throws IllegalArgumentException se nao for maior que 0 */
    public void setGridFeetPerCell(double feet) {
        if (feet <= 0) {
            throw new IllegalArgumentException("Pés por célula precisa ser maior que 0.");
        }
        scene.getGrid().setFeetPerCell(feet);
        for (SceneChangeListener l : listeners) {
            l.onGridChanged();
        }
    }
}
