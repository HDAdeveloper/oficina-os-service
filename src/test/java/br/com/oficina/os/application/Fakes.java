package br.com.oficina.os.application;

import br.com.oficina.os.application.port.out.*;
import br.com.oficina.os.domain.model.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** Implementações em memória das portas de saída, usadas nos testes dos casos de uso. */
final class Fakes {

    private Fakes() {
    }

    abstract static class Mem<T> {
        protected final Map<Long, T> dados = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        protected long proximoId() {
            return seq.incrementAndGet();
        }

        protected List<T> filtrar(Predicate<T> p) {
            return dados.values().stream().filter(p).toList();
        }
    }

    /** A chave do cliente é o documento (CPF/CNPJ), então não há id gerado como nos outros fakes. */
    static class ClientesMem implements ClienteRepository {
        private final Map<String, Cliente> dados = new LinkedHashMap<>();

        @Override
        public Cliente salvar(Cliente c) {
            dados.put(c.id(), c);
            return c;
        }

        @Override
        public Optional<Cliente> buscarPorId(String id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<Cliente> listarAtivos() {
            return dados.values().stream().filter(Cliente::ativo).toList();
        }
    }

    static class VeiculosMem extends Mem<Veiculo> implements VeiculoRepository {
        @Override
        public Veiculo salvar(Veiculo v) {
            Veiculo s = v.id() == null
                    ? new Veiculo(proximoId(), v.placa(), v.marca(), v.modelo(), v.ano(), v.clienteId(), v.ativo())
                    : v;
            dados.put(s.id(), s);
            return s;
        }

        @Override
        public Optional<Veiculo> buscarPorId(Long id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public Optional<Veiculo> buscarPorPlaca(String placa) {
            return dados.values().stream().filter(v -> v.placa().valor().equals(placa)).findFirst();
        }

        @Override
        public List<Veiculo> listarPorCliente(String clienteId) {
            return filtrar(v -> v.clienteId().equals(clienteId));
        }

        @Override
        public List<Veiculo> listarAtivos() {
            return filtrar(Veiculo::ativo);
        }
    }

    static class ServicosMem extends Mem<Servico> implements ServicoRepository {
        @Override
        public Servico salvar(Servico s) {
            Servico r = s.id() == null
                    ? new Servico(proximoId(), s.nome(), s.descricao(), s.preco(), s.tempoEstimadoMinutos(), s.ativo())
                    : s;
            dados.put(r.id(), r);
            return r;
        }

        @Override
        public Optional<Servico> buscarPorId(Long id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<Servico> listarAtivos() {
            return filtrar(Servico::ativo);
        }
    }

    static class PecasMem extends Mem<Peca> implements PecaRepository {
        @Override
        public Peca salvar(Peca p) {
            Peca r = p.id() == null
                    ? new Peca(proximoId(), p.codigo(), p.nome(), p.descricao(), p.precoUnitario(), p.ativo())
                    : p;
            dados.put(r.id(), r);
            return r;
        }

        @Override
        public Optional<Peca> buscarPorId(Long id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public Optional<Peca> buscarPorCodigo(String codigo) {
            return dados.values().stream().filter(p -> p.codigo().equals(codigo)).findFirst();
        }

        @Override
        public List<Peca> listarAtivas() {
            return filtrar(Peca::ativo);
        }
    }

    static class OrdensMem extends Mem<OrdemServico> implements OrdemServicoRepository {
        @Override
        public OrdemServico salvar(OrdemServico os) {
            if (os.getId() == null) {
                os.atribuirId(proximoId());
            }
            dados.put(os.getId(), os);
            return os;
        }

        @Override
        public Optional<OrdemServico> buscarPorId(Long id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<OrdemServico> buscarPorStatus(Collection<StatusOS> status) {
            return filtrar(o -> status.contains(o.getStatus()));
        }

        @Override
        public List<OrdemServico> buscarPorCliente(String clienteId) {
            return filtrar(o -> clienteId.equals(o.getClienteId())).stream()
                    .sorted(java.util.Comparator.comparing(OrdemServico::getDataAbertura).reversed()).toList();
        }
    }

    static class GeradorFixo implements GeradorNumeroOS {
        private int n;

        @Override
        public String proximo() {
            return "OS-TESTE-" + (++n);
        }
    }

    /** Registra os comandos enviados, na ordem, para os testes conferirem. */
    static class SagaGravador implements SagaCommandPort {
        final List<String> comandos = new ArrayList<>();

        @Override
        public void criarOrcamento(Saga saga, OrdemServico os, Cliente cliente) {
            comandos.add("CRIAR_ORCAMENTO:" + os.getId() + ":" + cliente.id());
        }

        @Override
        public void cobrarOrcamento(Saga saga, OrdemServico os) {
            comandos.add("COBRAR_ORCAMENTO:" + os.getId());
        }

        @Override
        public void cancelarOrcamento(Saga saga, OrdemServico os, String orcamentoId, String motivo) {
            comandos.add("CANCELAR_ORCAMENTO:" + os.getId() + ":" + orcamentoId + ":" + motivo);
        }

        @Override
        public void reservarEstoque(Saga saga, OrdemServico os) {
            comandos.add("RESERVAR_ESTOQUE:" + os.getId());
        }

        @Override
        public void liberarEstoque(Saga saga, OrdemServico os, String motivo) {
            comandos.add("LIBERAR_ESTOQUE:" + os.getId() + ":" + motivo);
        }

        @Override
        public void estornarPagamento(Saga saga, OrdemServico os, String pagamentoId, String motivo) {
            comandos.add("ESTORNAR_PAGAMENTO:" + os.getId() + ":" + pagamentoId + ":" + motivo);
        }

        /** Só o tipo, na ordem (sem ids), para conferir a sequência de uma compensação. */
        List<String> tipos() {
            return comandos.stream().map(c -> c.substring(0, c.indexOf(':'))).toList();
        }
    }

    static class SagasMem implements SagaRepository {
        final Map<String, Saga> dados = new LinkedHashMap<>();

        @Override
        public Saga salvar(Saga saga) {
            dados.put(saga.getId(), saga);
            return saga;
        }

        @Override
        public Optional<Saga> buscarPorId(String id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<Saga> buscarPorOs(Long osId) {
            List<Saga> lista = new ArrayList<>(dados.values().stream().filter(s -> s.getOsId().equals(osId)).toList());
            Collections.reverse(lista); // inserção = ordem de criação; mais recente primeiro
            return lista;
        }

        @Override
        public List<Saga> buscarRecentes(int limite) {
            return dados.values().stream()
                    .sorted(java.util.Comparator.comparing(Saga::getAtualizadaEm).reversed()).limit(limite).toList();
        }

        @Override
        public List<Saga> buscarComPrazoVencido(java.time.Instant agora) {
            return dados.values().stream()
                    .filter(s -> s.getPrazo() != null && !s.getPrazo().isAfter(agora)).toList();
        }
    }

    static class ProcessadasMem implements MensagemProcessadaRepository {
        final Set<String> ids = new HashSet<>();

        @Override
        public boolean registrar(String messageId, java.time.Instant em) {
            return ids.add(messageId);
        }
    }

    static class IdsSequenciais implements GeradorId {
        private int n;

        @Override
        public String novo() {
            return "saga-" + (++n);
        }
    }

    static class CorrelacaoFixa implements ContextoRequisicao {
        @Override
        public String correlationId() {
            return "corr-teste";
        }
    }
}
