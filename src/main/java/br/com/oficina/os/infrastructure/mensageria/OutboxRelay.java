package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.infrastructure.persistence.OutboxComandoEntity;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoJpa;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Envia ao SNS os comandos gravados na outbox, na ordem em que foram criados. Se o envio de um comando falha,
 * o lote para ali (preserva a ordem), a falha fica registrada e a próxima rodada tenta de novo.
 * Entrega at-least-once: Billing e Execution descartam a repetição pelo {@code messageId}.
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxComandoJpa outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final PublicadorMensagens publicador;
    private final Clock relogio;
    private final int lote;

    public OutboxRelay(OutboxComandoJpa outbox, TransactionTemplate tx, ObjectMapper json,
                       PublicadorMensagens publicador, Clock relogio, int lote) {
        this.outbox = outbox;
        this.tx = tx;
        this.json = json;
        this.publicador = publicador;
        this.relogio = relogio;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${oficina.mensageria.intervalo-relay-ms:1000}")
    public void executar() {
        try {
            publicarPendentes();
        } catch (RuntimeException e) {
            LOG.error("Erro ao publicar a outbox", e);
        }
    }

    /** Publica um lote de pendentes; devolve quantos saíram. */
    public int publicarPendentes() {
        Integer publicados = tx.execute(status -> {
            List<OutboxComandoEntity> pendentes = outbox.buscarPendentesParaPublicar(PageRequest.of(0, lote));
            int ok = 0;
            for (OutboxComandoEntity e : pendentes) {
                try {
                    publicador.publicar(e.getTipo(), e.getDestino(), envelope(e));
                    e.marcarPublicado(Instant.now(relogio));
                    ok++;
                } catch (RuntimeException ex) {
                    LOG.warn("Falha ao publicar o comando {} ({}); tentará de novo", e.getMessageId(), e.getTipo(), ex);
                    e.registrarFalha(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    break;
                }
            }
            return ok;
        });
        return publicados == null ? 0 : publicados;
    }

    String envelope(OutboxComandoEntity e) {
        try {
            ObjectNode no = json.createObjectNode();
            no.put("messageId", e.getMessageId());
            no.put("sagaId", e.getSagaId());
            if (e.getOsId() == null) {
                no.putNull("osId");
            } else {
                no.put("osId", e.getOsId());
            }
            no.put("type", e.getTipo());
            no.put("version", 1);
            no.put("occurredAt", e.getCriadoEm().toString());
            no.put("correlationId", e.getCorrelationId());
            no.set("payload", json.readTree(e.getPayload()));
            return json.writeValueAsString(no);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload da outbox inválido (comando " + e.getMessageId() + ")", ex);
        }
    }
}
