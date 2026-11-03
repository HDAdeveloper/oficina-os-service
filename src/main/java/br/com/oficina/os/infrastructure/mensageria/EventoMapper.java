package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Traduz o envelope recebido em {@code os-events} no evento da aplicação. */
public class EventoMapper {

    public record Mapeado(ContextoMensagem contexto, EventoSaga evento) {
    }

    public Mapeado mapear(EnvelopeMensagem env) {
        if (env == null || vazio(env.type())) {
            throw new MensagemInvalidaException("Mensagem sem o campo type");
        }
        if (vazio(env.messageId()) || vazio(env.sagaId())) {
            throw new MensagemInvalidaException("Mensagem " + env.type() + " sem messageId ou sagaId");
        }
        JsonNode p = env.payload();
        EventoSaga evento = switch (env.type()) {
            case "ORCAMENTO_CRIADO" -> new EventoSaga.OrcamentoCriado(obrigatorio(p, "orcamentoId"),
                    decimal(p, "valorTotal"));
            case "ORCAMENTO_FALHOU" -> new EventoSaga.OrcamentoFalhou(texto(p, "motivo"));
            case "ORCAMENTO_CANCELADO" -> new EventoSaga.OrcamentoCancelado();
            case "LINK_PAGAMENTO_GERADO" -> new EventoSaga.LinkPagamentoGerado(obrigatorio(p, "pagamentoId"),
                    obrigatorio(p, "link"), instante(p, "expiraEm"));
            case "PAGAMENTO_APROVADO" -> new EventoSaga.PagamentoAprovado(obrigatorio(p, "pagamentoId"),
                    texto(p, "orcamentoId"));
            case "PAGAMENTO_RECUSADO" -> new EventoSaga.PagamentoRecusado(texto(p, "orcamentoId"),
                    texto(p, "motivo"));
            case "PAGAMENTO_ESTORNADO" -> new EventoSaga.PagamentoEstornado(texto(p, "pagamentoId"));
            case "ESTOQUE_RESERVADO" -> new EventoSaga.EstoqueReservado(obrigatorio(p, "reservaId"));
            case "ESTOQUE_INSUFICIENTE" -> new EventoSaga.EstoqueInsuficiente(descreverFaltas(p));
            case "ESTOQUE_LIBERADO" -> new EventoSaga.EstoqueLiberado();
            case "EXECUCAO_INICIADA" -> new EventoSaga.ExecucaoIniciada();
            case "EXECUCAO_FINALIZADA" -> new EventoSaga.ExecucaoFinalizada();
            case "EXECUCAO_FALHOU" -> new EventoSaga.ExecucaoFalhou(texto(p, "motivo"));
            default -> throw new MensagemInvalidaException("Evento desconhecido para o OS Service: " + env.type());
        };
        return new Mapeado(new ContextoMensagem(env.messageId(), env.sagaId(), env.osId(), env.correlationId()),
                evento);
    }

    /** "FLT-01 (pedido 3, disponível 1); ..." ou o motivo de um pedido inválido. */
    private static String descreverFaltas(JsonNode p) {
        List<String> itens = new ArrayList<>();
        JsonNode faltantes = p == null ? null : p.get("faltantes");
        if (faltantes != null && faltantes.isArray()) {
            for (JsonNode f : faltantes) {
                itens.add(texto(f, "codigo") + " (pedido " + inteiro(f, "solicitado") + ", disponível "
                        + inteiro(f, "disponivel") + ")");
            }
        }
        String motivo = texto(p, "motivo");
        if (itens.isEmpty()) {
            return vazio(motivo) ? "sem detalhes" : motivo;
        }
        return String.join("; ", itens);
    }

    private static String obrigatorio(JsonNode p, String campo) {
        String valor = texto(p, campo);
        if (vazio(valor)) {
            throw new MensagemInvalidaException("Payload sem o campo obrigatório " + campo);
        }
        return valor;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static int inteiro(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || !n.canConvertToInt() ? 0 : n.asInt();
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            return n.isNumber() ? n.decimalValue() : new BigDecimal(n.asText());
        } catch (NumberFormatException e) {
            throw new MensagemInvalidaException("Valor numérico inválido em " + campo);
        }
    }

    private static Instant instante(JsonNode no, String campo) {
        String valor = texto(no, campo);
        if (vazio(valor)) {
            return null;
        }
        try {
            return Instant.parse(valor);
        } catch (DateTimeParseException e) {
            throw new MensagemInvalidaException("Data inválida em " + campo + ": " + valor);
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
