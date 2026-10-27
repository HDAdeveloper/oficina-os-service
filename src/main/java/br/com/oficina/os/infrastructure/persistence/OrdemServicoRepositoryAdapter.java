package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.OrdemServicoRepository;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.StatusOS;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class OrdemServicoRepositoryAdapter implements OrdemServicoRepository {

    private final OrdemServicoJpa jpa;

    public OrdemServicoRepositoryAdapter(OrdemServicoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public OrdemServico salvar(OrdemServico os) {
        OrdemServicoEntity e = os.getId() == null
                ? new OrdemServicoEntity()
                : jpa.findById(os.getId()).orElseGet(OrdemServicoEntity::new);
        PersistenciaMapper.aplicar(e, os);
        OrdemServicoEntity salva = jpa.saveAndFlush(e);
        if (os.getId() == null) {
            os.atribuirId(salva.id);
        }
        return os;
    }

    @Override
    public Optional<OrdemServico> buscarPorId(Long id) {
        return jpa.findById(id).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public List<OrdemServico> buscarPorStatus(Collection<StatusOS> status) {
        return jpa.findByStatusIn(status).stream().map(PersistenciaMapper::paraDominio).toList();
    }

    @Override
    public List<OrdemServico> buscarPorCliente(Long clienteId) {
        return jpa.findByClienteIdOrderByDataAberturaDesc(clienteId).stream()
                .map(PersistenciaMapper::paraDominio).toList();
    }
}
