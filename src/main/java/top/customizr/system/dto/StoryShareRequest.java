package top.customizr.system.dto;

/** Mostrar no chat uma parte da historia: {@code part} = APPEARANCE (aparencia e detalhes) ou BACKSTORY. */
public record StoryShareRequest(Part part) {

    public enum Part { APPEARANCE, BACKSTORY }
}
