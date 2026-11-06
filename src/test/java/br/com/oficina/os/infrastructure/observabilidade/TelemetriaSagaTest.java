package br.com.oficina.os.infrastructure.observabilidade;

import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.infrastructure.observabilidade.TelemetriaSaga.Evento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TelemetriaSagaTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    private Saga sagaComTresPassos() {
        Saga s = Saga.iniciar("s-1", 7L, "corr-1", T0, T0.plusSeconds(60));
        s.mover(EstadoSaga.AGUARDANDO_APROVACAO, "Orçamento o-1", T0.plusSeconds(5), null);
        s.mover(EstadoSaga.COBRANDO, "Cobrança", T0.plusSeconds(65), T0.plusSeconds(120));
        return s;
    }

    @Test
    void cadaPassoNovoViraEventoEAsMudancasDeEtapaViramTransicao() {
        List<Evento> eventos = TelemetriaSaga.eventos(sagaComTresPassos(), 0);

        assertThat(eventos).extracting(Evento::tipo).containsExactly(
                "SagaPasso",
                "SagaPasso", "SagaTransicao",
                "SagaPasso", "SagaTransicao");
        Evento primeiraTransicao = eventos.get(2);
        assertThat(primeiraTransicao.atributos())
                .containsEntry("de", "ORCAMENTO_SOLICITADO")
                .containsEntry("para", "AGUARDANDO_APROVACAO")
                .containsEntry("msNaEtapaAnterior", 5000L)
                .containsEntry("osId", 7L);
        Evento segunda = eventos.get(4);
        assertThat(segunda.atributos())
                .containsEntry("de", "AGUARDANDO_APROVACAO")
                .containsEntry("para", "COBRANDO")
                .containsEntry("msNaEtapaAnterior", 60000L);
    }

    @Test
    void soEnviaOsPassosAindaNaoRegistrados() {
        List<Evento> eventos = TelemetriaSaga.eventos(sagaComTresPassos(), 2);

        assertThat(eventos).extracting(Evento::tipo).containsExactly("SagaPasso", "SagaTransicao");
        assertThat(eventos.get(1).atributos()).containsEntry("para", "COBRANDO");
    }

    @Test
    void etapaTerminalInformaOTempoTotalDaSaga() {
        Saga s = sagaComTresPassos();
        s.mover(EstadoSaga.CANCELADA, "Compensada", T0.plusSeconds(100), null);

        List<Evento> eventos = TelemetriaSaga.eventos(s, 3);

        assertThat(eventos.get(1).atributos())
                .containsEntry("terminal", true)
                .containsEntry("msTotal", 100000L);
    }

    @Test
    void reenvioEhPassoMasNaoEhTransicao() {
        Saga s = Saga.iniciar("s-2", 8L, "corr-2", T0, T0.plusSeconds(60));
        s.reenviar(T0.plusSeconds(61), T0.plusSeconds(120));

        List<Evento> eventos = TelemetriaSaga.eventos(s, 1);

        assertThat(eventos).extracting(Evento::tipo).containsExactly("SagaPasso");
        assertThat(eventos.get(0).atributos()).containsEntry("tipo", "REENVIO");
    }

    @Test
    void semAgenteOEnvioNaoFazNadaENaoLancaExcecao() {
        assertThatCode(() -> TelemetriaSaga.registrar(sagaComTresPassos(), 0)).doesNotThrowAnyException();
    }
}
