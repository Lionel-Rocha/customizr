package com.vttcaseiro.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

/**
 * Uma NetMessage = uma linha de JSON (sem pretty-print -- o Gson por
 * padrao nao gera quebra de linha dentro do JSON compacto, entao uma
 * mensagem sempre cabe numa linha so, que e o que o protocolo linha-a-linha
 * do GameServer/GameClient espera).
 */
public final class NetCodec {
    /**
     * JSON estrito: sem NaN/Infinity (o leitor padrao aceita, e depois o servidor nao conseguia mais escrever a
     * cena -- um jogador travava a mesa de todos com uma posicao "NaN").
     */
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private NetCodec() {
    }

    public static String encode(NetMessage message) {
        return GSON.toJson(message);
    }

    /** @throws com.google.gson.JsonSyntaxException se a linha nao for um JSON valido */
    /** Copia profunda via JSON (mesmo formato que viaja pela rede). */
    public static <T> T deepCopy(T value, Class<T> type) {
        return GSON.fromJson(GSON.toJson(value), type);
    }

    public static NetMessage decode(String line) {
        return GSON.fromJson(line, NetMessage.class);
    }
}
