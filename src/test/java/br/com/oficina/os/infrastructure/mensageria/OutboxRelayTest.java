package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.infrastructure.config.GeradorIdUuid;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoEntity;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoJpa;
import br.com.oficina.os.infrastructure.persistence.PostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxRelayTest extends PostgresTestBase {

    private static final AtomicLong OS = new AtomicLong(8000);

    @Autowired OutboxComandoJpa outbox;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;

    /** Guarda o que seria enviado ao SNS; pode ser configurado para falhar. */
    static class PublicadorFake implements PublicadorMensagens {
        final List<String> tipos = new ArrayList<>();
        final List<String> destinos = new ArrayList<>();
        final List<String> corpos = new ArrayList<>();
        boolean falhar;

        @Override
        public void publicar(String tipo, String destino, String envelopeJson) {
            if (falhar) {
                throw new IllegalStateException("SNS indisponível");
            }
            tipos.add(tipo);
            destinos.add(destino);
            corpos.add(envelopeJson);
        }
    }

    private OutboxRelay relay(PublicadorFake fake) {
        return new OutboxRelay(outbox, tx, json, fake, Clock.systemUTC(), 100);
    }

    private long novoComando() {
        long os = OS.incrementAndGet();
        Saga saga = Saga.iniciar("saga-" + os, os, "corr-" + os, Instant.now(), Instant.now().plusSeconds(60));
        var porta = new OutboxSagaCommandPort(outbox, json, new GeradorIdUuid(), Clock.systemUTC());
        tx.executeWithoutResult(s -> porta.cancelarOrcamento(saga, null, "orc-" + os, "teste"));
        return os;
    }

    private List<OutboxComandoEntity> daOs(long os) {
        return outbox.findByOsIdOrderByIdAsc(os);
    }

    @Test
    void publicaOsPendentesComEnvelopeCompletoEMarcaComoEnviados() throws Exception {
        relay(new PublicadorFake()).publicarPendentes();
        long os = novoComando();
        var fake = new PublicadorFake();

        relay(fake).publicarPendentes();

        assertThat(fake.tipos).containsExactly("CANCELAR_ORCAMENTO");
        assertThat(fake.destinos).containsExactly("billing");
        JsonNode env = json.readTree(fake.corpos.get(0));
        assertThat(env.get("type").asText()).isEqualTo("CANCELAR_ORCAMENTO");
        assertThat(env.get("sagaId").asText()).isEqualTo("saga-" + os);
        assertThat(env.get("osId").asLong()).isEqualTo(os);
        assertThat(env.get("correlationId").asText()).isEqualTo("corr-" + os);
        assertThat(env.get("version").asInt()).isEqualTo(1);
        assertThat(env.get("occurredAt").asText()).isNotBlank();
        assertThat(env.get("messageId").asText()).isEqualTo(daOs(os).get(0).getMessageId());
        assertThat(env.get("payload").get("orcamentoId").asText()).isEqualTo("orc-" + os);
        assertThat(daOs(os)).allSatisfy(e -> assertThat(e.getPublicadoEm()).isNotNull());
    }

    @Test
    void naoRepublicaOQueJaFoiEnviado() {
        novoComando();
        var fake = new PublicadorFake();
        var relay = relay(fake);
        relay.publicarPendentes();
        int antes = fake.tipos.size();

        assertThat(relay.publicarPendentes()).isZero();
        assertThat(fake.tipos).hasSize(antes);
    }

    @Test
    void falhaNoEnvioMantemOComandoPendenteERegistraOErro() {
        relay(new PublicadorFake()).publicarPendentes();
        long os = novoComando();
        var fake = new PublicadorFake();
        fake.falhar = true;
        var relay = relay(fake);

        relay.publicarPendentes();

        OutboxComandoEntity e = daOs(os).get(0);
        assertThat(e.getPublicadoEm()).isNull();
        assertThat(e.getTentativas()).isEqualTo(1);
        assertThat(e.getUltimoErro()).contains("SNS indisponível");

        fake.falhar = false;
        relay.publicarPendentes();

        OutboxComandoEntity depois = daOs(os).get(0);
        assertThat(depois.getPublicadoEm()).isNotNull();
        assertThat(depois.getUltimoErro()).isNull();
    }

    @Test
    void executarNaoPropagaErros() {
        novoComando();
        var fake = new PublicadorFake();
        fake.falhar = true;

        relay(fake).executar();
    }
}
