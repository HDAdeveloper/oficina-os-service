package br.com.oficina.os.infrastructure.mensageria;

/** Saída para o barramento (SNS). Interface própria para o relay da outbox ser testável sem AWS. */
public interface PublicadorMensagens {

    /**
     * Publica o envelope JSON no tópico de comandos; {@code tipo} e {@code destino} (billing ou execution)
     * vão como atributos da mensagem, e o destino decide em qual fila ela cai. Lança exceção se falhar.
     */
    void publicar(String tipo, String destino, String envelopeJson);
}
