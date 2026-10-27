package br.com.oficina.os.application.port.out;

/** Porta de saída: dados da requisição em andamento (o correlationId do cabeçalho HTTP). */
public interface ContextoRequisicao {

    /** Id de correlação da requisição atual; nunca nulo (gera um novo se a chamada não trouxe). */
    String correlationId();
}
