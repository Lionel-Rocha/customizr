package top.customizr.system.dto;

import top.customizr.system.model.TokenLayer;

import java.util.List;
import java.util.UUID;

/**
 * Altura, camada e fichas do token, cada campo so muda se vier.
 * elevationFt: o dono tambem muda (voar, subir no telhado); 0 = no chao.
 * layer, memberIds (as fichas que viajam juntas no token, ex.: a caravana) e o tamanho: so o mestre.
 * widthCells / heightCells: em casas, de 0,5 a 30 (meia casa = Miudo; Imenso = 4 ou mais; objeto pode ser retangulo).
 */
public record TokenPlacementRequest(Integer elevationFt, TokenLayer layer, List<UUID> memberIds,
                                    Double widthCells, Double heightCells) {

    public TokenPlacementRequest(Integer elevationFt, TokenLayer layer, List<UUID> memberIds) {
        this(elevationFt, layer, memberIds, null, null);
    }
}
