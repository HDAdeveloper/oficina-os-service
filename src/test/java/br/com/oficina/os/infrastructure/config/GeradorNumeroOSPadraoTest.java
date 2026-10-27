package br.com.oficina.os.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GeradorNumeroOSPadraoTest {

    @Test
    void geraNumeroComAnoEHexadecimal() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        GeradorNumeroOSPadrao gerador = new GeradorNumeroOSPadrao(relogio);

        String a = gerador.proximo();
        String b = gerador.proximo();

        assertThat(a).matches("OS-2026-[0-9A-F]{8}");
        assertThat(a).isNotEqualTo(b);
    }
}
