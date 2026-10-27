package br.com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PecaJpa extends JpaRepository<PecaEntity, Long> {
    Optional<PecaEntity> findByCodigo(String codigo);

    List<PecaEntity> findByAtivoTrueOrderByNome();
}
