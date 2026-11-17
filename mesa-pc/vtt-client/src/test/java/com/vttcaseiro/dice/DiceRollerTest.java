package com.vttcaseiro.dice;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do motor de dados. Roda com "mvn test". Os mesmos casos (e as
 * mesmas mensagens de erro) ja foram validados na versao em Lua do projeto
 * do Firecast -- isso aqui confere se a traducao pra Java ficou fiel.
 */
class DiceRollerTest {

    @RepeatedTest(200)
    void rolarUmDadoFicaEntreUmESeisLados() throws DiceException {
        RollResult result = DiceRoller.roll("1d6");
        assertTrue(result.total() >= 1 && result.total() <= 6,
                "1d6 deveria dar entre 1 e 6, deu " + result.total());
    }

    @Test
    void somaDoisTermosComDados() throws DiceException {
        RollResult result = DiceRoller.roll("2d6+3");
        assertTrue(result.total() >= 5 && result.total() <= 15,
                "2d6+3 deveria dar entre 5 e 15, deu " + result.total());
        assertTrue(result.detail().endsWith(" = " + result.total()));
    }

    @Test
    void subtracaoFuncionaEPodeDarNegativo() throws DiceException {
        // "1-100" sozinho nao tem dado nenhum (e o outro teste exige erro pra
        // isso), e o rolador exige pelo menos 2 faces -- 1d2-100 da -99 ou -98.
        RollResult result = DiceRoller.roll("1d2-100");
        assertTrue(result.total() == -99 || result.total() == -98, "total: " + result.total());
    }

    @Test
    void expressaoVaziaLancaErro() {
        DiceException ex = assertThrows(DiceException.class, () -> DiceRoller.roll(""));
        assertEquals("Digite uma expressao, ex.: 2d6+3", ex.getMessage());
    }

    @Test
    void expressaoSoComNumeroSemDadoLancaErro() {
        DiceException ex = assertThrows(DiceException.class, () -> DiceRoller.roll("5+3"));
        assertEquals("Inclua ao menos um dado (ex.: 1d20).", ex.getMessage());
    }

    @Test
    void expressaoInvalidaLancaErro() {
        assertThrows(DiceException.class, () -> DiceRoller.roll("abc"));
        assertThrows(DiceException.class, () -> DiceRoller.roll("1d6++2"));
        assertThrows(DiceException.class, () -> DiceRoller.roll("1d"));
    }

    @Test
    void limiteDeDadosEFaces() {
        assertThrows(DiceException.class, () -> DiceRoller.roll("500d6"));
        assertThrows(DiceException.class, () -> DiceRoller.roll("1d1"));
        assertThrows(DiceException.class, () -> DiceRoller.roll("1d2000"));
    }

    @Test
    void semSinalNoPrimeiroTermoFuncionaIgual() throws DiceException {
        RollResult comSinal = DiceRoller.roll("+1d20");
        assertTrue(comSinal.total() >= 1 && comSinal.total() <= 20);
    }

    @RepeatedTest(300)
    void vantagemFicaNaFaixaEMarcaQualDadoFoiDescartado() {
        RollResult r = DiceRoller.rollWithAdvantage(5);
        assertTrue(r.total() >= 6 && r.total() <= 25, "esperado 6-25, deu " + r.total());
        assertTrue(r.detail().contains("descartado"));
    }

    @RepeatedTest(300)
    void desvantagemFicaNaFaixa() {
        RollResult r = DiceRoller.rollWithDisadvantage(-1);
        // 1d20 - 1 vai de 0 a 19 (a faixa antiga, -1 a 18, falhava ~1 em 20 rolagens).
        assertTrue(r.total() >= 0 && r.total() <= 19, "esperado 0 a 19, deu " + r.total());
    }

    @Test
    void vantagemDaMediaMaiorQueDesvantagem() {
        long somaVantagem = 0, somaDesvantagem = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            somaVantagem += DiceRoller.rollWithAdvantage(0).total();
            somaDesvantagem += DiceRoller.rollWithDisadvantage(0).total();
        }
        double mediaVantagem = somaVantagem / (double) n;
        double mediaDesvantagem = somaDesvantagem / (double) n;
        // teorico: vantagem ~13.825, desvantagem ~7.175 -- confere so que a
        // diferenca e grande o bastante pra nao ser coincidencia de sorteio
        assertTrue(mediaVantagem > mediaDesvantagem + 3,
                "vantagem=" + mediaVantagem + " desvantagem=" + mediaDesvantagem);
    }

    @RepeatedTest(200)
    void rollAbilityScoreFicaEntreTresEDezoito() {
        int score = DiceRoller.rollAbilityScore();
        assertTrue(score >= 3 && score <= 18, "4d6 descartando o menor deveria dar 3-18, deu " + score);
    }
}
