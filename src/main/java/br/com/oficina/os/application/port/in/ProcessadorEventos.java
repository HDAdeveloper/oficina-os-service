package br.com.oficina.os.application.port.in;

/**
 * Porta de entrada da mensageria: trata um evento da Saga. Idempotente por {@code messageId};
 * evento que não cabe no passo atual da Saga é registrado e ignorado.
 */
public interface ProcessadorEventos {

    void processar(ContextoMensagem contexto, EventoSaga evento);

    /** Reenvia ou compensa os passos cuja resposta passou do prazo. Devolve quantas Sagas foram tratadas. */
    int verificarPrazos();
}
