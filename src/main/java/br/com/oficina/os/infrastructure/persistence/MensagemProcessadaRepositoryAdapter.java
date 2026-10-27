package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.MensagemProcessadaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Registra o id da mensagem com {@code INSERT ... ON CONFLICT DO NOTHING}: quem inserir primeiro processa,
 * a repetição recebe 0 linhas afetadas. Duas réplicas recebendo a mesma mensagem não se atropelam, e a
 * transação não é abortada pelo conflito.
 */
@Component
public class MensagemProcessadaRepositoryAdapter implements MensagemProcessadaRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean registrar(String messageId, Instant quando) {
        int linhas = em.createNativeQuery("""
                        INSERT INTO mensagem_processada (message_id, processada_em) VALUES (:id, :em)
                        ON CONFLICT (message_id) DO NOTHING""")
                .setParameter("id", messageId)
                .setParameter("em", Timestamp.from(quando))
                .executeUpdate();
        return linhas == 1;
    }
}
