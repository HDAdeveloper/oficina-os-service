package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.ProcessadorEventos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Trata o corpo de uma mensagem de {@code os-events}: lê o envelope, converte no evento e chama o orquestrador.
 * Se lançar exceção, a mensagem NÃO deve ser confirmada na fila (volta e, depois de 3 vezes, vai para a DLQ).
 */
public class ConsumidorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumidorEventos.class);

    private final ObjectMapper json;
    private final EventoMapper mapper;
    private final ProcessadorEventos processador;

    public ConsumidorEventos(ObjectMapper json, EventoMapper mapper, ProcessadorEventos processador) {
        this.json = json;
        this.mapper = mapper;
        this.processador = processador;
    }

    public void tratar(String corpo) {
        EnvelopeMensagem envelope;
        try {
            envelope = json.readValue(corpo, EnvelopeMensagem.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MensagemInvalidaException("Corpo da mensagem não é um envelope JSON válido", e);
        }
        EventoMapper.Mapeado mapeado = mapper.mapear(envelope);
        MDC.put("correlationId", String.valueOf(envelope.correlationId()));
        MDC.put("sagaId", String.valueOf(envelope.sagaId()));
        try {
            LOG.info("Evento {} recebido (messageId={}, osId={})", envelope.type(), envelope.messageId(),
                    envelope.osId());
            processador.processar(mapeado.contexto(), mapeado.evento());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("sagaId");
        }
    }
}
