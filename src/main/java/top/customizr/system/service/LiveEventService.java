package top.customizr.system.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Transmissao ao vivo por campanha (Server-Sent Events): cada navegador conectado em
 * GET /live/campaign/{id}/events recebe os eventos da mesa (token movido, neblina, chat,
 * iniciativa...). Cada evento escolhe quem recebe (ex.: token oculto so pro mestre). Os eventos
 * so saem depois que a transacao confirma, pra ninguem ver algo que foi desfeito.
 */
@Service
public class LiveEventService {

    /** Quem esta ouvindo: o usuario e se ele e o mestre da campanha. */
    public record Listener(UUID userId, String username, boolean master) {}

    private record Subscription(Listener listener, SseEmitter emitter) {}

    public static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<Subscription>> byCampaign = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID campaignId, Listener listener) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Subscription subscription = new Subscription(listener, emitter);
        List<Subscription> list = byCampaign.computeIfAbsent(campaignId, k -> new CopyOnWriteArrayList<>());
        list.add(subscription);
        Runnable remove = () -> list.remove(subscription);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(subscription, "connected", Map.of("campaignId", campaignId, "master", listener.master()));
        return emitter;
    }

    public void publish(UUID campaignId, String type, Object payload) {
        publish(campaignId, type, payload, listener -> true);
    }

    public void publishToMaster(UUID campaignId, String type, Object payload) {
        publish(campaignId, type, payload, Listener::master);
    }

    /** Publica depois do commit (ou na hora, se nao houver transacao). */
    public void publish(UUID campaignId, String type, Object payload, Predicate<Listener> audience) {
        Runnable send = () -> {
            for (Subscription s : byCampaign.getOrDefault(campaignId, List.of())) {
                if (audience.test(s.listener())) send(s, type, payload);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    public int listeners(UUID campaignId) {
        return byCampaign.getOrDefault(campaignId, List.of()).size();
    }

    private void send(Subscription s, String type, Object payload) {
        try {
            s.emitter().send(SseEmitter.event().name(type).data(payload));
        } catch (IOException | IllegalStateException ex) {
            s.emitter().completeWithError(ex);
        }
    }
}
