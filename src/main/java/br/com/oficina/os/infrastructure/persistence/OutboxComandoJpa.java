package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface OutboxComandoJpa extends JpaRepository<OutboxComandoEntity, Long> {

    /** Lote de pendentes para o relay, com SKIP LOCKED: duas réplicas não pegam os mesmos comandos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxComandoEntity e where e.publicadoEm is null order by e.id asc")
    List<OutboxComandoEntity> buscarPendentesParaPublicar(Pageable limite);

    List<OutboxComandoEntity> findByOsIdOrderByIdAsc(Long osId);
}
