package com.vttcaseiro.character;

/**
 * Slots de magia por nivel (1 a 9) de uma ficha -- cantrips (nivel 0) nao
 * gastam slot, entao nem aparecem aqui. Guarda total ("max", quanto a
 * classe/nivel da personagem da nesse nivel de magia) e quanto ainda
 * resta ("current") separados, no mesmo espirito de hpCurrent/hpMax em
 * {@link CharacterSheet} -- um descanso longo restaura tudo de uma vez
 * (ver {@link #restoreAll()}), em vez de "current" e "max" serem sempre
 * iguais.
 * <p>
 * Guardado como arrays de tamanho fixo (index 0 sem uso, 1..9 validos)
 * em vez de Map/List pra o Gson salvar/carregar sem nenhuma configuracao
 * especial -- mesma razao pratica que levou savingThrowProficiencies a
 * usar Set em vez de EnumSet em CharacterSheet.
 */
public class SpellSlots {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 9;

    private int[] max = new int[MAX_LEVEL + 1];
    private int[] current = new int[MAX_LEVEL + 1];

    public int getMax(int level) {
        checkLevel(level);
        return max[level];
    }

    /** Muda o maximo de slots desse nivel (ex.: subiu de nivel). Se o
     * atual ja usado for maior que o novo maximo, ele e cortado pro novo
     * maximo -- nunca fica "current > max". */
    public void setMax(int level, int value) {
        checkLevel(level);
        max[level] = Math.max(0, value);
        current[level] = Math.min(current[level], max[level]);
    }

    public int getCurrent(int level) {
        checkLevel(level);
        return current[level];
    }

    /** Ajuste direto do quanto resta (ex.: tela deixando digitar o
     * numero) -- sempre travado entre 0 e o maximo daquele nivel. */
    public void setCurrent(int level, int value) {
        checkLevel(level);
        current[level] = Math.max(0, Math.min(value, max[level]));
    }

    /** Gasta um slot desse nivel, se tiver algum sobrando. Devolve false
     * sem mudar nada se nao tiver nenhum disponivel -- quem chama decide
     * o que fazer com isso (ex.: avisar "sem slots de nivel X" no chat). */
    public boolean useSlot(int level) {
        checkLevel(level);
        if (current[level] <= 0) {
            return false;
        }
        current[level]--;
        return true;
    }

    /** Devolve um slot gasto (ex.: Magia Sutil cancelada antes de rolar,
     * um recurso que devolve slot) -- nunca passa do maximo daquele
     * nivel, mesmo chamado mais vezes do que devia. */
    public void restoreSlot(int level) {
        checkLevel(level);
        current[level] = Math.min(max[level], current[level] + 1);
    }

    /** Descanso longo: todo nivel volta pro maximo dele. */
    public void restoreAll() {
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            current[level] = max[level];
        }
    }

    /** Pra tela decidir se mostra a secao de slots -- um guerreiro puro
     * (sem nenhum slot em nenhum nivel) nao precisa da secao aparecendo
     * vazia. */
    public boolean hasAnySlots() {
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            if (max[level] > 0) {
                return true;
            }
        }
        return false;
    }

    private static void checkLevel(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Nivel de magia invalido: " + level + " (precisa ser 1 a 9)");
        }
    }
}
