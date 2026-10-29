package br.com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Interface do Spring Data; o adaptador a usa para implementar a porta da aplicação. */
public interface ClienteJpa extends JpaRepository<ClienteEntity, String> {
    List<ClienteEntity> findByAtivoTrueOrderByNome();
}
