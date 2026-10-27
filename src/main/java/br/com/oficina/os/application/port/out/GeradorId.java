package br.com.oficina.os.application.port.out;

/** Porta de saída: ids únicos (da Saga). */
public interface GeradorId {
    String novo();
}
