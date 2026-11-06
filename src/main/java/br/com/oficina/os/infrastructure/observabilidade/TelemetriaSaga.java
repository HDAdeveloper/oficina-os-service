package br.com.oficina.os.infrastructure.observabilidade;

import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.PassoSaga;
import br.com.oficina.os.domain.model.Saga;
import com.newrelic.api.agent.NewRelic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eventos customizados do New Relic para os painéis (tempo em cada etapa da Saga, falhas, compensações).
 * Sem o agente, {@link NewRelic} não faz nada; com ele, cada passo novo do diário vira um evento
 * {@code SagaPasso} e cada mudança de etapa vira {@code SagaTransicao} (com o tempo gasto na etapa anterior).
 */
public final class TelemetriaSaga {

    static final String EVENTO_PASSO = "SagaPasso";
    static final String EVENTO_TRANSICAO = "SagaTransicao";
    private static final String INICIO = "SAGA_INICIADA";

    private TelemetriaSaga() {
    }

    /** Envia ao New Relic os passos novos (a partir de {@code jaRegistrados}). Nunca lança exceção. */
    public static void registrar(Saga saga, int jaRegistrados) {
        try {
            for (Evento e : eventos(saga, jaRegistrados)) {
                NewRelic.getAgent().getInsights().recordCustomEvent(e.tipo(), e.atributos());
            }
        } catch (RuntimeException ignorada) {
            // observabilidade nunca pode derrubar o fluxo da Saga
        }
    }

    record Evento(String tipo, Map<String, Object> atributos) {
    }

    /** Monta os eventos dos passos novos; separado do envio para poder ser testado sem agente. */
    static List<Evento> eventos(Saga saga, int jaRegistrados) {
        List<PassoSaga> passos = saga.getPassos();
        List<Evento> saida = new ArrayList<>();
        for (int i = Math.max(jaRegistrados, 0); i < passos.size(); i++) {
            PassoSaga atual = passos.get(i);
            Map<String, Object> comum = new HashMap<>();
            comum.put("sagaId", saga.getId());
            comum.put("osId", saga.getOsId());
            comum.put("correlationId", saga.getCorrelationId());
            comum.put("tipo", atual.tipo());
            comum.put("estadoAtual", saga.getEstado().name());
            if (i > 0) {
                comum.put("msDesdePassoAnterior", Duration.between(passos.get(i - 1).em(), atual.em()).toMillis());
            }
            saida.add(new Evento(EVENTO_PASSO, comum));

            EstadoSaga para = estadoDe(atual.tipo());
            if (para != null) {
                saida.add(new Evento(EVENTO_TRANSICAO, transicao(saga, passos, i, atual, para)));
            }
        }
        return saida;
    }

    private static Map<String, Object> transicao(Saga saga, List<PassoSaga> passos, int indice, PassoSaga atual,
                                                 EstadoSaga para) {
        Map<String, Object> a = new HashMap<>();
        a.put("sagaId", saga.getId());
        a.put("osId", saga.getOsId());
        a.put("correlationId", saga.getCorrelationId());
        a.put("para", para.name());
        for (int j = indice - 1; j >= 0; j--) {
            PassoSaga anterior = passos.get(j);
            EstadoSaga de = INICIO.equals(anterior.tipo()) ? EstadoSaga.ORCAMENTO_SOLICITADO : estadoDe(anterior.tipo());
            if (de != null) {
                a.put("de", de.name());
                a.put("msNaEtapaAnterior", Duration.between(anterior.em(), atual.em()).toMillis());
                break;
            }
        }
        if (para.isTerminal()) {
            a.put("terminal", true);
            a.put("msTotal", Duration.between(saga.getCriadaEm(), atual.em()).toMillis());
        }
        return a;
    }

    private static EstadoSaga estadoDe(String tipo) {
        try {
            return EstadoSaga.valueOf(tipo);
        } catch (IllegalArgumentException naoEhEstado) {
            return null;
        }
    }
}
