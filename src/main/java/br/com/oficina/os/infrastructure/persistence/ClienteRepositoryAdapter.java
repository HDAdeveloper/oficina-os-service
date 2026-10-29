package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.domain.model.Cliente;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClienteRepositoryAdapter implements ClienteRepository {

    private final ClienteJpa jpa;

    public ClienteRepositoryAdapter(ClienteJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Cliente salvar(Cliente cliente) {
        ClienteEntity e = jpa.findById(cliente.id()).orElseGet(ClienteEntity::new);
        PersistenciaMapper.aplicar(e, cliente);
        return PersistenciaMapper.paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Cliente> buscarPorId(String id) {
        return jpa.findById(id).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public List<Cliente> listarAtivos() {
        return jpa.findByAtivoTrueOrderByNome().stream().map(PersistenciaMapper::paraDominio).toList();
    }
}
