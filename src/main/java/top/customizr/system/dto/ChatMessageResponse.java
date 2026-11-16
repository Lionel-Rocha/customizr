package top.customizr.system.dto;

import top.customizr.system.model.ChatMessage;
import top.customizr.system.model.ChatMessageType;
import top.customizr.system.model.InlineRolls;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code parts}: o texto ja quebrado em pedacos; os que tem {@code total} sao rolagens
 * (mostrar o total em destaque e {@code detail} ao passar o mouse).
 */
public record ChatMessageResponse(
        UUID id,
        ChatMessageType type,
        UUID authorId,
        String authorName,
        String speakerName,
        String content,
        List<InlineRolls.Part> parts,
        String recipientName,
        Integer rollTotal,
        Integer naturalD20,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getType(),
                message.getAuthor().getUserId(),
                message.getAuthor().getName(),
                message.getSpeakerName(),
                message.getContent(),
                InlineRolls.split(message.getContent()),
                message.getRecipient() == null ? null : message.getRecipient().getName(),
                message.getRollTotal(),
                message.getNaturalD20(),
                message.getCreatedAt()
        );
    }
}
