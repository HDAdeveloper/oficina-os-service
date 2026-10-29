package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.OrdemServico;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrdemServicoResponse(
        Long id, String numero, String status, String statusDescricao,
        String clienteId, Long veiculoId, String descricaoProblema, String observacoes,
        String orcamentoId, String motivoCancelamento,
        List<ItemServico> servicos, List<ItemPeca> pecas, BigDecimal valorTotal,
        Instant dataAbertura, Instant dataInicio, Instant dataFinalizacao, Instant dataEntrega) {

    public record ItemServico(Long servicoId, String nome, int quantidade, BigDecimal precoUnitario,
                              BigDecimal subtotal) {
    }

    public record ItemPeca(Long pecaId, String codigo, String nome, int quantidade, BigDecimal precoUnitario,
                           BigDecimal subtotal) {
    }

    public static OrdemServicoResponse de(OrdemServico os) {
        return new OrdemServicoResponse(
                os.getId(), os.getNumero(), os.getStatus().name(), os.getStatus().getDescricao(),
                os.getClienteId(), os.getVeiculoId(), os.getDescricaoProblema(), os.getObservacoes(),
                os.getOrcamentoId(), os.getMotivoCancelamento(),
                os.getItensServico().stream()
                        .map(i -> new ItemServico(i.servicoId(), i.nome(), i.quantidade(), i.precoUnitario(),
                                i.subtotal())).toList(),
                os.getItensPeca().stream()
                        .map(i -> new ItemPeca(i.pecaId(), i.codigo(), i.nome(), i.quantidade(), i.precoUnitario(),
                                i.subtotal())).toList(),
                os.valorTotal(), os.getDataAbertura(), os.getDataInicio(), os.getDataFinalizacao(),
                os.getDataEntrega());
    }
}
