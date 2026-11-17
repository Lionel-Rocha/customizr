package com.vttcaseiro.character.prerequisite;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * O Gson, sozinho, NAO sabe salvar/carregar uma lista de {@link Prerequisite}
 * (uma interface com varias classes diferentes por baixo -- LevelPrerequisite,
 * AbilityScorePrerequisite, FeatPrerequisite). Na hora de SALVAR ate
 * funcionaria sem isto (o Gson escreve os campos que achar via reflexao).
 * O problema e CARREGAR de volta: sem saber que classe concreta cada
 * pedaco do JSON representa, o Gson nao tem como recriar o objeto certo.
 * <p>
 * Esse adaptador resolve isso guardando um campo extra "type" (o nome da
 * classe) junto com os dados -- o mesmo principio que o "type" das
 * mensagens de rede (NetMessage) ja usa neste projeto, so que aqui para
 * polimorfismo de verdade (classes diferentes, nao so campos diferentes
 * na mesma classe).
 * <p>
 * Registrar isto e obrigatorio em QUALQUER Gson usado pra salvar/carregar
 * algo que contenha um Feat (que tem {@code List<Prerequisite>}) --
 * ver LibraryStorage. Esquecer de registrar nao da erro na hora de
 * salvar, so na hora de carregar de volta -- por isso essa classe tem um
 * aviso grande no Javadoc, nao e um bug que aparece rapido em teste
 * superficial.
 * <p>
 * IMPORTANTE: NAO consegui testar esta classe de verdade (o ambiente onde
 * escrevi isto nao tem o Gson disponivel pra rodar nada). A logica de
 * NEGOCIO do Prerequisite/Feat (isMetBy, isAvailableFor) foi testada à
 * exaustao; esta classe especifica (o encaixe com o Gson) foi so
 * revisada com cuidado, igual o resto do codigo JavaFX deste projeto.
 */
public class PrerequisiteAdapter implements JsonSerializer<Prerequisite>, JsonDeserializer<Prerequisite> {

    @Override
    public JsonElement serialize(Prerequisite src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", src.getClass().getSimpleName());
        wrapper.add("data", context.serialize(src, src.getClass()));
        return wrapper;
    }

    @Override
    public Prerequisite deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject wrapper = json.getAsJsonObject();
        String type = wrapper.has("type") ? wrapper.get("type").getAsString() : null;
        JsonElement data = wrapper.get("data");
        if (type == null || data == null) {
            throw new JsonParseException("Pre-requisito salvo num formato que eu nao reconheco.");
        }
        Class<? extends Prerequisite> concreteClass = switch (type) {
            case "LevelPrerequisite" -> LevelPrerequisite.class;
            case "AbilityScorePrerequisite" -> AbilityScorePrerequisite.class;
            case "FeatPrerequisite" -> FeatPrerequisite.class;
            default -> throw new JsonParseException("Tipo de pre-requisito desconhecido: " + type);
        };
        return context.deserialize(data, concreteClass);
    }
}
