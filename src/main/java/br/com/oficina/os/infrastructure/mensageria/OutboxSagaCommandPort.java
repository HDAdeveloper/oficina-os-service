package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.out.GeradorId;
import br.com.oficina.os.application.port.out.SagaCommandPort;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.ItemPecaOS;
import br.com.oficina.os.domain.model.ItemServicoOS;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoEntity;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoJpa;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Implementa a porta de comandos gravando na outbox, dentro da transação do orquestrador. O envio ao SNS
 * é feito depois pelo {@link OutboxRelay}. Aqui ficam os payloads de cada comando (docs/eventos.md).
 */
@Component
public class OutboxSagaCommandPort implements SagaCommandPort {

    static final String BILLING = "billing";
    static final String EXECUTION = "execution";

    private final OutboxComandoJpa jpa;
    private final ObjectMapper json;
    private final GeradorId ids;
    private final Clock relogio;

    public OutboxSagaCommandPort(OutboxComandoJpa jpa, ObjectMapper json, GeradorId ids, Clock relogio) {
        this.jpa = jpa;
        this.json = json;
        this.ids = ids;
        this.relogio = relogio;
    }

    @Override
    public void criarOrcamento(Saga saga, OrdemServico os, Cliente cliente) {
        ObjectNode p = json.createObjectNode();
        p.put("clienteId", cliente.id());
        p.put("emailCliente", cliente.email());
        p.put("valorTotal", os.valorTotal());
        ArrayNode itens = p.putArray("itens");
        for (ItemServicoOS s : os.getItensServico()) {
            itens.addObject().put("tipo", "SERVICO").put("codigo", "SRV-" + s.servicoId()).put("descricao", s.nome())
                    .put("quantidade", s.quantidade()).put("precoUnitario", s.precoUnitario());
        }
        for (ItemPecaOS i : os.getItensPeca()) {
            itens.addObject().put("tipo", "PECA").put("codigo", i.codigo()).put("descricao", i.nome())
                    .put("quantidade", i.quantidade()).put("precoUnitario", i.precoUnitario());
        }
        gravar("CRIAR_ORCAMENTO", BILLING, saga, p);
    }

    @Override
    public void cobrarOrcamento(Saga saga, OrdemServico os) {
        ObjectNode p = json.createObjectNode();
        p.put("orcamentoId", saga.getOrcamentoId());
        p.put("valorTotal", saga.getValorTotal() != null ? saga.getValorTotal() : os.valorTotal());
        if (saga.getMetodoPagamento() != null && !saga.getMetodoPagamento().isBlank()) {
            p.put("metodo", saga.getMetodoPagamento());
        }
        gravar("COBRAR_ORCAMENTO", BILLING, saga, p);
    }

    @Override
    public void cancelarOrcamento(Saga saga, OrdemServico os, String orcamentoId, String motivo) {
        ObjectNode p = json.createObjectNode();
        p.put("orcamentoId", orcamentoId);
        p.put("motivo", motivo);
        gravar("CANCELAR_ORCAMENTO", BILLING, saga, p);
    }

    @Override
    public void reservarEstoque(Saga saga, OrdemServico os) {
        ObjectNode p = json.createObjectNode();
        ArrayNode pecas = p.putArray("pecas");
        for (ItemPecaOS i : os.getItensPeca()) {
            pecas.addObject().put("codigo", i.codigo()).put("quantidade", i.quantidade());
        }
        ArrayNode servicos = p.putArray("servicos");
        for (ItemServicoOS s : os.getItensServico()) {
            servicos.add(s.nome());
        }
        gravar("RESERVAR_ESTOQUE", EXECUTION, saga, p);
    }

    @Override
    public void liberarEstoque(Saga saga, OrdemServico os, String motivo) {
        ObjectNode p = json.createObjectNode();
        p.put("motivo", motivo);
        gravar("LIBERAR_ESTOQUE", EXECUTION, saga, p);
    }

    @Override
    public void estornarPagamento(Saga saga, OrdemServico os, String pagamentoId, String motivo) {
        ObjectNode p = json.createObjectNode();
        p.put("pagamentoId", pagamentoId);
        p.put("motivo", motivo);
        gravar("ESTORNAR_PAGAMENTO", BILLING, saga, p);
    }

    private void gravar(String tipo, String destino, Saga saga, ObjectNode payload) {
        String corpo;
        try {
            corpo = json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload do comando " + tipo + " inválido", ex);
        }
        OutboxComandoEntity e = OutboxComandoEntity.novo(ids.novo(), tipo, destino, saga.getOsId(), saga.getId(),
                saga.getCorrelationId(), corpo, Instant.now(relogio));
        jpa.save(e);
    }
}
