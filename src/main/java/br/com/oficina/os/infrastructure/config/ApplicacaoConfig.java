package br.com.oficina.os.infrastructure.config;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.application.port.in.GestaoPecas;
import br.com.oficina.os.application.port.in.GestaoServicos;
import br.com.oficina.os.application.port.in.GestaoVeiculos;
import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.application.port.out.GeradorNumeroOS;
import br.com.oficina.os.application.port.out.OrdemServicoRepository;
import br.com.oficina.os.application.port.out.PecaRepository;
import br.com.oficina.os.application.port.in.ProcessadorEventos;
import br.com.oficina.os.application.port.out.ContextoRequisicao;
import br.com.oficina.os.application.port.out.GeradorId;
import br.com.oficina.os.application.port.out.MensagemProcessadaRepository;
import br.com.oficina.os.application.port.out.SagaCommandPort;
import br.com.oficina.os.application.port.out.SagaRepository;
import br.com.oficina.os.application.port.out.ServicoRepository;
import br.com.oficina.os.application.port.out.VeiculoRepository;
import br.com.oficina.os.application.service.ClienteService;
import br.com.oficina.os.application.service.OrdemServicoService;
import br.com.oficina.os.application.service.OrquestradorSaga;
import br.com.oficina.os.application.service.PecaService;
import br.com.oficina.os.application.service.ServicoService;
import br.com.oficina.os.application.service.VeiculoService;
import br.com.oficina.os.infrastructure.transaction.TransacaoProxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

/** Liga os casos de uso (Java puro) às portas de saída e os envolve em transação. */
@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class ApplicacaoConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public GeradorNumeroOS geradorNumeroOS(Clock relogio) {
        return new GeradorNumeroOSPadrao(relogio);
    }

    @Bean
    public GestaoClientes gestaoClientes(ClienteRepository clientes, TransactionTemplate tx) {
        return TransacaoProxy.envolver(GestaoClientes.class, new ClienteService(clientes), tx);
    }

    @Bean
    public GestaoVeiculos gestaoVeiculos(VeiculoRepository veiculos, ClienteRepository clientes,
                                         TransactionTemplate tx) {
        return TransacaoProxy.envolver(GestaoVeiculos.class, new VeiculoService(veiculos, clientes), tx);
    }

    @Bean
    public GestaoServicos gestaoServicos(ServicoRepository servicos, TransactionTemplate tx) {
        return TransacaoProxy.envolver(GestaoServicos.class, new ServicoService(servicos), tx);
    }

    @Bean
    public GestaoPecas gestaoPecas(PecaRepository pecas, TransactionTemplate tx) {
        return TransacaoProxy.envolver(GestaoPecas.class, new PecaService(pecas), tx);
    }

    /**
     * O orquestrador não guarda estado (tudo está nos repositórios), então o caso de uso da OS e o da mensageria
     * podem ter cada um a sua instância. Cada chamada de qualquer um dos dois roda em uma transação.
     */
    private static OrquestradorSaga orquestrador(SagaRepository sagas, MensagemProcessadaRepository processadas,
                                                 OrdemServicoRepository ordens, ClienteRepository clientes,
                                                 SagaCommandPort comandos, GeradorId ids, ContextoRequisicao requisicao,
                                                 Clock relogio, SagaProperties p) {
        return new OrquestradorSaga(sagas, processadas, ordens, clientes, comandos, ids, requisicao, relogio,
                Duration.ofSeconds(p.prazoRespostaSegundos()), p.maxReenvios());
    }

    @Bean
    public GestaoOrdensServico gestaoOrdensServico(OrdemServicoRepository ordens, ClienteRepository clientes,
                                                   VeiculoRepository veiculos, ServicoRepository servicos,
                                                   PecaRepository pecas, GeradorNumeroOS gerador,
                                                   SagaRepository sagas, MensagemProcessadaRepository processadas,
                                                   SagaCommandPort comandos, GeradorId ids,
                                                   ContextoRequisicao requisicao, SagaProperties saga,
                                                   Clock relogio, TransactionTemplate tx) {
        OrquestradorSaga orquestrador = orquestrador(sagas, processadas, ordens, clientes, comandos, ids,
                requisicao, relogio, saga);
        return TransacaoProxy.envolver(GestaoOrdensServico.class,
                new OrdemServicoService(ordens, clientes, veiculos, servicos, pecas, gerador, orquestrador, relogio),
                tx);
    }

    @Bean
    public ProcessadorEventos processadorEventos(OrdemServicoRepository ordens, ClienteRepository clientes,
                                                 SagaRepository sagas, MensagemProcessadaRepository processadas,
                                                 SagaCommandPort comandos, GeradorId ids,
                                                 ContextoRequisicao requisicao, SagaProperties saga, Clock relogio,
                                                 TransactionTemplate tx) {
        return TransacaoProxy.envolver(ProcessadorEventos.class,
                orquestrador(sagas, processadas, ordens, clientes, comandos, ids, requisicao, relogio, saga), tx);
    }
}
