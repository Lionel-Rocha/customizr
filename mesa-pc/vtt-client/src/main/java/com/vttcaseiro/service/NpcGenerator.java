package com.vttcaseiro.service;

import com.vttcaseiro.model.Campaign;

import java.util.Random;

/**
 * PdM rapido: nome, ocupacao, aparencia, um traco de personalidade, o que quer e um segredo. Listas proprias (nada
 * tirado de livro), pra dar um ponto de partida que o mestre ajusta.
 */
public final class NpcGenerator {
    private NpcGenerator() {
    }

    static final String[] FIRST = {"Aldo", "Brisa", "Cael", "Dora", "Enzo", "Fiora", "Gildo", "Hana", "Ivo", "Jana", "Kael",
            "Lia", "Marco", "Nina", "Otto", "Pia", "Quim", "Rosa", "Silas", "Tula", "Ugo", "Vera", "Wim", "Yara", "Zeca",
            "Bento", "Clara", "Dimas", "Elza", "Fausto", "Greta", "Heitor", "Iris", "Joca", "Luna", "Mirela", "Nestor"};
    static final String[] LAST = {"da Ponte", "Pedra-Funda", "Sete-Ventos", "do Moinho", "Corvo", "Mão-Leve", "Ferraz",
            "Barba-Ruiva", "do Vale", "Olho-de-Vidro", "Castanho", "Brasa", "Salgueiro", "Torto", "da Encruzilhada",
            "Lanterna", "Cinzento", "Bom-Vinho", "Martelo", "Dente-de-Ouro"};
    static final String[] JOBS = {"taverneiro", "ferreira", "guarda da cidade", "contrabandista", "sacerdote", "mercadora",
            "caçador", "escriba", "curandeira", "barqueiro", "ladra de rua", "bardo itinerante", "coveiro", "alquimista",
            "cavaleira aposentada", "pescador", "cartógrafa", "agiota", "domador de cavalos", "mendigo que sabe demais"};
    static final String[] LOOKS = {"cicatriz no queixo", "um olho de cada cor", "dedos sempre sujos de tinta",
            "roupa fina mas remendada", "voz rouca e baixa", "sorriso com dente faltando", "tatuagem de serpente no braço",
            "careca e barba trançada", "anda mancando de leve", "cheira a fumaça", "chapéu enorme com pena",
            "mãos calejadas e enormes", "óculos remendados com arame", "cabelo branco apesar de jovem", "sempre com frio"};
    static final String[] TRAITS = {"fala alto e ri de tudo", "desconfia de estranhos", "conta vantagem o tempo todo",
            "é educado demais (e isso assusta)", "responde pergunta com pergunta", "nunca olha nos olhos",
            "adora fofoca", "é supersticioso com números", "tem pressa pra tudo", "é gentil com crianças e animais",
            "barganha até o último cobre", "esquece nomes, nunca rostos", "cita provérbios que não existem",
            "fica nervoso perto de magia"};
    static final String[] WANTS = {"pagar uma dívida antes do inverno", "achar o irmão que sumiu na mina",
            "ser respeitado pela guilda", "fugir da cidade sem ninguém saber", "vingar a morte da mentora",
            "proteger a filha de um casamento arranjado", "provar que não roubou o cálice", "abrir o próprio negócio",
            "recuperar um mapa que perdeu no jogo", "ver o mar uma vez na vida", "descobrir quem anda pelo telhado à noite"};
    static final String[] SECRETS = {"trabalha para o vilão sem saber", "é nobre fugido e usa nome falso",
            "viu o crime e ficou calado", "deve dinheiro a quem não perdoa", "tem um item mágico escondido no colchão",
            "é a pessoa que os heróis estão procurando", "sabe a entrada secreta do castelo", "foi amaldiçoado e esconde a marca",
            "está apaixonado por quem não devia", "falsifica documentos pra quem paga bem"};

    public static Campaign.Npc random(Random r) {
        Campaign.Npc n = new Campaign.Npc();
        n.name = pick(FIRST, r) + " " + pick(LAST, r);
        n.role = pick(JOBS, r);
        n.attitude = "Neutro";
        n.description = "Aparência: " + pick(LOOKS, r) + ".\nJeito: " + pick(TRAITS, r) + ".\nQuer: " + pick(WANTS, r) + ".";
        n.gmNotes = "Segredo: " + pick(SECRETS, r) + ".";
        return n;
    }

    private static String pick(String[] list, Random r) {
        return list[r.nextInt(list.length)];
    }
}
