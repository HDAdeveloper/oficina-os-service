package br.com.oficina.os.application.port.in;

import br.com.oficina.os.domain.model.HistoricoStatus;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.StatusOS;

import java.util.List;

/**
 * Porta de entrada: ciclo de vida da OS acionado por atendentes e clientes (REST).
 * As etapas que dependem de outros serviços (orçamento, pagamento) só disparam comandos da Saga;
 * o resultado volta como evento (Etapa 5).
 */
public interface GestaoOrdensServico {

    /** Serviço ou peça (pelo id do catálogo) e a quantidade. */
    record ItemSolicitado(Long itemId, int quantidade) {
    }

    record AberturaOS(String documentoCliente, String placaVeiculo, String descricaoProblema,
                      String observacoes, List<ItemSolicitado> servicos, List<ItemSolicitado> pecas) {
    }

    OrdemServico abrir(AberturaOS dados);

    OrdemServico buscarPorId(Long id);

    StatusOS consultarStatus(Long id);

    List<HistoricoStatus> consultarHistorico(Long id);

    /** OS em andamento, da mais prioritária (em execução) para a menos, mais antigas primeiro. */
    List<OrdemServico> listarAtivas();

    List<OrdemServico> listarPorStatus(StatusOS status);

    /** OS de um cliente (pelo CPF/CNPJ), a mais recente primeiro; é o que o cliente final consulta. */
    List<OrdemServico> listarDoCliente(String documento);

    OrdemServico iniciarDiagnostico(Long id);

    OrdemServico adicionarServico(Long id, ItemSolicitado item);

    OrdemServico adicionarPeca(Long id, ItemSolicitado item);

    /** Pede o orçamento ao Billing. O status só avança quando o Billing responde. */
    OrdemServico solicitarOrcamento(Long id);

    /** Cliente aprovou: dispara a cobrança. O status só avança quando pagamento e estoque forem confirmados. */
    default OrdemServico aprovarOrcamento(Long id) {
        return aprovarOrcamento(id, null);
    }

    /**
     * Como {@link #aprovarOrcamento(Long)}, informando a forma de pagamento. No provedor simulado ela comanda o
     * resultado (APRO aprova, FUND recusa por saldo, ERRO_ESTORNO falha no estorno...); vazio = aprovado.
     */
    OrdemServico aprovarOrcamento(Long id, String metodoPagamento);

    /** Diário da Saga da OS (a mais recente primeiro), para acompanhar pagamento, reserva e compensações. */
    List<Saga> consultarSagas(Long id);

    /** Cliente recusou: volta ao diagnóstico e cancela o orçamento no Billing. */
    OrdemServico recusarOrcamento(Long id, String motivo);

    /** Cancelamento manual, permitido só antes do orçamento (sem pagamento a desfazer). */
    OrdemServico cancelar(Long id, String motivo);

    OrdemServico entregar(Long id);
}
