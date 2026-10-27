package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;

/**
 * Porta de saída: comandos da Saga enviados aos outros serviços (docs/eventos.md). A implementação grava o
 * comando na outbox, dentro da transação do orquestrador; um relay o publica depois.
 *
 * Os destinatários tratam os comandos de forma idempotente, então o reenvio por timeout é seguro.
 */
public interface SagaCommandPort {

    /** CRIAR_ORCAMENTO para o Billing. */
    void criarOrcamento(Saga saga, OrdemServico os, Cliente cliente);

    /** COBRAR_ORCAMENTO para o Billing. */
    void cobrarOrcamento(Saga saga, OrdemServico os);

    /** CANCELAR_ORCAMENTO para o Billing. */
    void cancelarOrcamento(Saga saga, OrdemServico os, String orcamentoId, String motivo);

    /** RESERVAR_ESTOQUE para o Execution (peças e serviços da OS). */
    void reservarEstoque(Saga saga, OrdemServico os);

    /** LIBERAR_ESTOQUE para o Execution (compensação). */
    void liberarEstoque(Saga saga, OrdemServico os, String motivo);

    /** ESTORNAR_PAGAMENTO para o Billing (compensação). */
    void estornarPagamento(Saga saga, OrdemServico os, String pagamentoId, String motivo);
}
