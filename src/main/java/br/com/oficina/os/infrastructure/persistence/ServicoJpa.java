package br.com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoJpa extends JpaRepository<ServicoEntity, Long> {
    List<ServicoEntity> findByAtivoTrueOrderByNome();
}
