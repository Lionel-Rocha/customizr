package top.customizr.system.dto;

import top.customizr.system.model.GridType;

public record SceneRequest(String name, String mapUrl, GridType gridType, Integer gridSizePx, Integer feetPerCell,
                           Integer widthPx, Integer heightPx) {}
