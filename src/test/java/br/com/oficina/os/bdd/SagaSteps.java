package br.com.oficina.os.bdd;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.port.in.ProcessadorEventos;
import br.com.oficina.os.infrastructure.persistence.CpfDeTeste;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoEntity;
import br.com.oficina.os.infrastructure.persistence.OutboxComandoJpa;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passos dos cenários da Saga. A API do OS Service é chamada de verdade (MockMvc, JWT, banco real). Billing e
 * Execution são representados pelos eventos que eles enviariam: cada passo "o Billing/Execution ..." entrega ao
 * orquestrador a resposta correspondente ao último comando gravado na outbox.
 */
public class SagaSteps {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired MockMvc mvc;
    @Autowired ProcessadorEventos eventos;
    @Autowired OutboxComandoJpa outbox;
    @Autowired ObjectMapper json;

    private String token;
    private Long osId;
    private String orcamentoId;
    private ContextoMensagem ultimoContexto;

    // ------------------------------------------------------------------ preparação

    @Dado("que existe uma ordem de serviço em diagnóstico com 1 serviço e 2 peças")
    public void osEmDiagnostico() throws Exception {
        int n = SEQ.incrementAndGet();
        String cpf = CpfDeTeste.gerar(n);
        String placa = String.format("BDD%dA%02d", n % 10, n % 100);
        criar("/api/clientes", Map.of("nome", "Cliente BDD " + n, "documento", cpf,
                "email", "bdd" + n + "@oficina.com"));
        criar("/api/veiculos", Map.of("placa", placa, "marca", "VW", "modelo", "Gol", "ano", 2020,
                "clienteId", cpf));
        long servicoId = criar("/api/servicos", Map.of("nome", "Alinhamento BDD " + n, "preco", 150.00,
                "tempoEstimadoMinutos", 30));
        long pecaId = criar("/api/pecas", Map.of("codigo", "BDD-" + n, "nome", "Filtro BDD " + n,
                "precoUnitario", 35.00));
        ResultActions aberta = chamar(post("/api/ordens-servico"), Map.of("documentoCliente", cpf,
                "placaVeiculo", placa, "descricaoProblema", "Revisão BDD",
                "servicos", List.of(Map.of("itemId", servicoId, "quantidade", 1)),
                "pecas", List.of(Map.of("itemId", pecaId, "quantidade", 2)))).andExpect(status().isCreated());
        osId = idDe(aberta);
        chamar(post("/api/ordens-servico/" + osId + "/diagnostico"), null).andExpect(status().isOk());
    }

    @Quando("o mecânico solicita o orçamento")
    public void solicitaOrcamento() throws Exception {
        chamar(post("/api/ordens-servico/" + osId + "/orcamento"), null).andExpect(status().isAccepted());
    }

    @Quando("o cliente aprova o orçamento pagando com {string}")
    public void aprova(String metodo) throws Exception {
        chamar(post("/api/ordens-servico/" + osId + "/orcamento/aprovacao"), Map.of("metodoPagamento", metodo))
                .andExpect(status().isAccepted());
    }

    // ------------------------------------------------------------------ respostas do Billing

    @E("o Billing cria o orçamento")
    public void billingCriaOrcamento() throws Exception {
        BigDecimal total = JsonPath.parse(payloadDe("CRIAR_ORCAMENTO")).read("$.valorTotal", BigDecimal.class);
        orcamentoId = "orc-bdd-" + osId;
        responder(new EventoSaga.OrcamentoCriado(orcamentoId, total));
    }

    @Quando("o Billing aprova o pagamento")
    public void billingAprova() {
        responder(new EventoSaga.PagamentoAprovado("pag-bdd-" + osId, orcamentoId));
    }

    @E("o Billing aprova o pagamento novamente com a mesma mensagem")
    public void billingAprovaDeNovo() {
        eventos.processar(ultimoContexto, new EventoSaga.PagamentoAprovado("pag-bdd-" + osId, orcamentoId));
    }

    @E("o Billing recusa o pagamento com o motivo {string}")
    public void billingRecusa(String motivo) {
        responder(new EventoSaga.PagamentoRecusado(orcamentoId, motivo));
    }

    @Quando("o Billing confirma o cancelamento do orçamento")
    public void billingConfirmaCancelamento() {
        responder(new EventoSaga.OrcamentoCancelado());
    }

    @Quando("o Billing confirma o estorno do pagamento")
    public void billingConfirmaEstorno() {
        responder(new EventoSaga.PagamentoEstornado("pag-bdd-" + osId));
    }

    // ------------------------------------------------------------------ respostas do Execution

    @Quando("o Execution reserva o estoque")
    public void executionReserva() {
        responder(new EventoSaga.EstoqueReservado("res-bdd-" + osId));
    }

    @E("o Execution informa que não há estoque suficiente")
    public void executionSemEstoque() {
        responder(new EventoSaga.EstoqueInsuficiente("BDD (pedido 2, disponível 0)"));
    }

    @Quando("o Execution inicia a execução")
    public void executionInicia() {
        responder(new EventoSaga.ExecucaoIniciada());
    }

    @E("o Execution finaliza a execução")
    public void executionFinaliza() {
        responder(new EventoSaga.ExecucaoFinalizada());
    }

    @E("a execução falha com o motivo {string}")
    public void execucaoFalha(String motivo) {
        responder(new EventoSaga.ExecucaoFalhou(motivo));
    }

    @Quando("o Execution confirma a devolução do estoque")
    public void executionConfirmaDevolucao() {
        responder(new EventoSaga.EstoqueLiberado());
    }

    // ------------------------------------------------------------------ verificações

    @Entao("a saga da OS está em {string}")
    public void sagaEm(String estado) throws Exception {
        String corpo = chamar(get("/api/ordens-servico/" + osId + "/saga"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(corpo, "$[0].estado")).isEqualTo(estado);
    }

    @E("a OS está com status {string}")
    public void osComStatus(String statusEsperado) throws Exception {
        String corpo = chamar(get("/api/ordens-servico/" + osId + "/status"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(corpo, "$.status")).isEqualTo(statusEsperado);
    }

    @E("os comandos enviados foram, em ordem: {string}")
    public void comandosEmOrdem(String lista) {
        assertThat(tipos()).containsExactlyElementsOf(Arrays.stream(lista.split(",")).map(String::trim).toList());
    }

    @E("nenhum comando {string} foi enviado")
    public void nenhumComando(String tipo) {
        assertThat(tipos()).doesNotContain(tipo);
    }

    @E("o comando {string} foi enviado {int} vez")
    public void comandoNVezes(String tipo, int vezes) {
        assertThat(tipos().stream().filter(tipo::equals).count()).isEqualTo(vezes);
    }

    // ------------------------------------------------------------------ apoio

    private void responder(EventoSaga evento) {
        String sagaId = sagaIdAtual();
        ultimoContexto = new ContextoMensagem(java.util.UUID.randomUUID().toString(), sagaId, osId, "bdd-" + osId);
        eventos.processar(ultimoContexto, evento);
    }

    private String sagaIdAtual() {
        try {
            String corpo = chamar(get("/api/ordens-servico/" + osId + "/saga"), null).andReturn().getResponse()
                    .getContentAsString();
            return JsonPath.read(corpo, "$[0].id");
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui ler a Saga da OS " + osId, e);
        }
    }

    private List<String> tipos() {
        return outbox.findByOsIdOrderByIdAsc(osId).stream().map(OutboxComandoEntity::getTipo).toList();
    }

    private String payloadDe(String tipo) {
        return outbox.findByOsIdOrderByIdAsc(osId).stream().filter(e -> e.getTipo().equals(tipo)).findFirst()
                .orElseThrow(() -> new AssertionError("Comando " + tipo + " não foi enviado")).getPayload();
    }

    private String token() throws Exception {
        if (token == null) {
            String corpo = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                            .content("{\"usuario\":\"admin\",\"senha\":\"admin123\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            token = JsonPath.read(corpo, "$.token");
        }
        return token;
    }

    private ResultActions chamar(MockHttpServletRequestBuilder req, Object corpo) throws Exception {
        req.header("Authorization", "Bearer " + token());
        if (corpo != null) {
            req.contentType(APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        return mvc.perform(req);
    }

    private long criar(String url, Object corpo) throws Exception {
        return idDe(chamar(post(url), corpo).andExpect(status().isCreated()));
    }

    private static long idDe(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
