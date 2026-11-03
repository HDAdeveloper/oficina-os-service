package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.SagaRepository;
import br.com.oficina.os.domain.model.PassoSaga;
import br.com.oficina.os.domain.model.Saga;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class SagaRepositoryAdapter implements SagaRepository {

    private final SagaJpa jpa;

    public SagaRepositoryAdapter(SagaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Saga salvar(Saga saga) {
        SagaEntity e = jpa.findById(saga.getId()).orElseGet(SagaEntity::new);
        e.id = saga.getId();
        e.osId = saga.getOsId();
        e.correlationId = saga.getCorrelationId();
        e.estado = saga.getEstado();
        e.orcamentoId = saga.getOrcamentoId();
        e.valorTotal = saga.getValorTotal();
        e.metodoPagamento = saga.getMetodoPagamento();
        e.pagamentoId = saga.getPagamentoId();
        e.linkPagamento = saga.getLinkPagamento();
        e.reservaId = saga.getReservaId();
        e.motivo = saga.getMotivo() == null ? null : cortar(saga.getMotivo(), 1000);
        e.tentativas = saga.getTentativas();
        e.prazo = saga.getPrazo();
        e.criadaEm = saga.getCriadaEm();
        e.atualizadaEm = saga.getAtualizadaEm();
        e.passos.clear();
        for (PassoSaga p : saga.getPassos()) {
            e.passos.add(new PassoEmb(p.em(), p.tipo(), p.detalhe()));
        }
        return paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Saga> buscarPorId(String id) {
        return jpa.findById(id).map(SagaRepositoryAdapter::paraDominio);
    }

    @Override
    public List<Saga> buscarPorOs(Long osId) {
        return jpa.findByOsIdOrderByCriadaEmDesc(osId).stream().map(SagaRepositoryAdapter::paraDominio).toList();
    }

    @Override
    public List<Saga> buscarRecentes(int limite) {
        return jpa.findAllByOrderByAtualizadaEmDesc(PageRequest.of(0, limite)).stream()
                .map(SagaRepositoryAdapter::paraDominio).toList();
    }

    @Override
    public List<Saga> buscarComPrazoVencido(Instant agora) {
        return jpa.findByPrazoLessThanEqual(agora).stream().map(SagaRepositoryAdapter::paraDominio).toList();
    }

    private static Saga paraDominio(SagaEntity e) {
        List<PassoSaga> passos = e.passos.stream().map(p -> new PassoSaga(p.em, p.tipo, p.detalhe)).toList();
        return Saga.reconstituir(new Saga.Dados(e.id, e.osId, e.correlationId, e.estado, e.orcamentoId, e.valorTotal,
                e.metodoPagamento, e.pagamentoId, e.linkPagamento, e.reservaId, e.motivo, e.tentativas, e.prazo, e.criadaEm,
                e.atualizadaEm, e.versao == null ? 0 : e.versao, passos));
    }

    private static String cortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
