package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.ProcessadorEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Rotina periódica que pede ao orquestrador para reenviar ou compensar os passos sem resposta no prazo.
 * Se duas réplicas tratarem a mesma Saga ao mesmo tempo, o bloqueio otimista deixa uma delas falhar e tentar
 * de novo na próxima rodada.
 */
public class VerificadorDePrazos {

    private static final Logger LOG = LoggerFactory.getLogger(VerificadorDePrazos.class);

    private final ProcessadorEventos orquestrador;

    public VerificadorDePrazos(ProcessadorEventos orquestrador) {
        this.orquestrador = orquestrador;
    }

    @Scheduled(fixedDelayString = "${oficina.mensageria.intervalo-prazos-ms:10000}")
    public void executar() {
        try {
            int tratadas = orquestrador.verificarPrazos();
            if (tratadas > 0) {
                LOG.info("{} Saga(s) com resposta atrasada tratadas", tratadas);
            }
        } catch (RuntimeException e) {
            LOG.warn("Falha ao verificar prazos da Saga; nova tentativa na próxima rodada", e);
        }
    }
}
