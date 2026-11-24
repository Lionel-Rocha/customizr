package com.vttcaseiro.net;

import com.vttcaseiro.app.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Uma conexao de jogador do lado do servidor: uma thread LE (e entrega pro
 * GameServer) e outra thread ESCREVE, a partir de uma fila propria.
 * <p>
 * Por que a fila: antes, send() escrevia direto no socket -- e o servidor
 * chama send()/broadcast de dentro do seu lock. Um jogador com internet
 * ruim (socket cheio) fazia a escrita esperar, e o servidor inteiro ficava
 * parado esperando ELE. Agora send() so poe na fila e volta na hora; quem
 * espera a rede e a thread de escrita desse jogador, sozinha. Se a fila
 * passar de {@link #MAX_QUEUED} mensagens (jogador travado de vez), a
 * conexao dele e encerrada em vez de acumular memoria pra sempre.
 */
class ClientHandler implements Runnable {
    private static final Log log = Log.of(ClientHandler.class);

    /** Mensagens pendentes por jogador antes de considera-lo travado. */
    static final int MAX_QUEUED = 5000;
    private static final String POISON = "\u0000fim";

    private final Socket socket;
    private final GameServer server;
    private final Writer out;
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private final Thread writerThread;
    private volatile PlayerSession session;
    /** Desafio desta conexao (ver PasswordProof). */
    private final String challenge = PasswordProof.newChallenge();
    private volatile boolean closed;

    /** IP real de quem veio pela ponte (o socket mostra o IP da ponte). null = o do socket. */
    private volatile String remoteOverride;

    ClientHandler(Socket socket, GameServer server, String remoteOverride) throws IOException {
        this(socket, server);
        this.remoteOverride = remoteOverride;
    }

    ClientHandler(Socket socket, GameServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.writerThread = new Thread(this::writeLoop, "client-writer-" + socket.getRemoteSocketAddress());
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    void setSession(PlayerSession session) {
        this.session = session;
    }

    PlayerSession getSession() {
        return session;
    }

    String getChallenge() {
        return challenge;
    }

    /** IP de quem conectou (pro limite de tentativas de senha). */
    String remoteAddress() {
        if (remoteOverride != null) return "ponte:" + remoteOverride; // nunca conta como conexao local
        return socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
    }

    String getPlayerName() {
        return session != null ? session.playerName() : "desconhecido";
    }

    boolean isGm() {
        return session != null && session.gm();
    }

    /** Tokens que ESTE cliente tem na tela agora (o servidor manda so os que ele ve). */
    final java.util.Set<String> knownTokens = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Grupo dividido: cena separada em que este cliente esta (null = a do grupo). Pro mestre: a que ele olha. */
    volatile String sideScene;

    boolean ownsToken(String tokenOwnerId) {
        return session != null && session.owns(tokenOwnerId);
    }

    /** Codifica e poe na fila (nao bloqueia). */
    void send(NetMessage message) {
        sendEncoded(NetCodec.encode(message));
    }

    /** Linha JSON ja pronta -- o broadcast codifica UMA vez pra todos. */
    void sendEncoded(String line) {
        if (closed) return;
        if (outbox.size() >= MAX_QUEUED) {
            log.warn(getPlayerName() + " nao esta recebendo as mensagens (fila cheia) -- desconectando.");
            abort();
            return;
        }
        outbox.offer(line);
    }

    /** Quantas mensagens esperam pra sair (pros testes). */
    int queuedCount() {
        return outbox.size();
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.poll(1, TimeUnit.SECONDS);
                if (line == null) {
                    if (socket.isClosed()) break;
                    continue;
                }
                if (line == POISON) break;
                out.write(line);
                out.write('\n');
                // Junta o que mais estiver na fila antes de mandar pela rede.
                String next;
                while ((next = outbox.poll()) != null && next != POISON) {
                    out.write(next);
                    out.write('\n');
                }
                out.flush();
                if (next == POISON) break;
            }
        } catch (IOException ex) {
            // socket caiu -- a thread de leitura percebe e limpa a conexao
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            closeSocket();
        }
    }

    @Override
    public void run() {
        try (InputStreamReader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)) {
            BoundedLineReader in = new BoundedLineReader(reader);
            send(NetMessage.challenge(challenge)); // primeira coisa: o desafio da senha
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    NetMessage msg = NetCodec.decode(line);
                    if (msg == null || msg.type == null) {
                        continue;
                    }
                    if ("ping".equals(msg.type)) {
                        send(NetMessage.pong());
                        continue;
                    }
                    server.handleIncoming(msg, this);
                } catch (RuntimeException ex) {
                    // uma linha invalida (JSON quebrado) nao derruba a conexao inteira
                    log.warn("Mensagem invalida de " + getPlayerName() + ": " + ex.getMessage());
                }
            }
        } catch (BoundedLineReader.LineTooLongException ex) {
            log.warn(getPlayerName() + ": " + ex.getMessage());
        } catch (IOException ex) {
            // conexao caiu -- comportamento normal quando o jogador fecha o programa
        } finally {
            server.removeClient(this);
            close();
        }
    }

    /** Fecha com educacao: a thread de escrita manda o que ja estava na fila
     * (ex.: "senha incorreta") e so entao fecha o socket. */
    void close() {
        if (closed) return;
        closed = true;
        outbox.offer(POISON);
    }

    /** Fecha na hora, descartando a fila (jogador travado). */
    void abort() {
        closed = true;
        outbox.clear();
        outbox.offer(POISON);
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
