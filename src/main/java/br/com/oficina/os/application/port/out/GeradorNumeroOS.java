package br.com.oficina.os.application.port.out;

/** Porta de saída: gera o número único e legível da OS (ex.: OS-2026-1A2B3C4D). */
public interface GeradorNumeroOS {

    String proximo();
}
