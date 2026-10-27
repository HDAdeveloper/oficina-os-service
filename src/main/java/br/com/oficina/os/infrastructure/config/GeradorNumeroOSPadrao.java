package br.com.oficina.os.infrastructure.config;

import br.com.oficina.os.application.port.out.GeradorNumeroOS;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Gera números como OS-2026-1A2B3C4D (ano + 8 hexadecimais aleatórios). A coluna é única no banco. */
public class GeradorNumeroOSPadrao implements GeradorNumeroOS {

    private final Clock clock;

    public GeradorNumeroOSPadrao(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String proximo() {
        int ano = LocalDate.now(clock).getYear();
        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "OS-" + ano + "-" + sufixo;
    }
}
