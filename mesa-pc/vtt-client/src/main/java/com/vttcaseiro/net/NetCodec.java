package com.vttcaseiro.net;

import com.google.gson.Gson;

/**
 * Uma NetMessage = uma linha de JSON (sem pretty-print -- o Gson por
 * padrao nao gera quebra de linha dentro do JSON compacto, entao uma
 * mensagem sempre cabe numa linha so, que e o que o protocolo linha-a-linha
 * do GameServer/GameClient espera).
 */
public final class NetCodec {
    private static final Gson GSON = new Gson();

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
