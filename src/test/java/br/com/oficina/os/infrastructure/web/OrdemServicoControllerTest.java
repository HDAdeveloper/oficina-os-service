package br.com.oficina.os.infrastructure.web;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.exception.TransicaoStatusInvalidaException;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.StatusOS;
import br.com.oficina.os.infrastructure.seguranca.JwtConfig;
import br.com.oficina.os.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.os.infrastructure.web.controller.OrdemServicoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdemServicoController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class OrdemServicoControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean GestaoOrdensServico ordens;

    private static OrdemServico os() {
        OrdemServico os = OrdemServico.abrir("OS-2026-AAAA0001", 1L, 2L, "Barulho no motor", null, T0);
        os.atribuirId(10L);
        return os;
    }

    // ------------------------------------------------------------ abertura e consultas

    @Test
    void abreOsDevolve201() throws Exception {
        when(ordens.abrir(any())).thenReturn(os());

        mvc.perform(post("/api/ordens-servico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON).content("""
                        {"documentoCliente":"529.982.247-25","placaVeiculo":"ABC1D23",
                         "descricaoProblema":"Barulho no motor",
                         "servicos":[{"itemId":1,"quantidade":1}],"pecas":[{"itemId":2,"quantidade":2}]}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/ordens-servico/10")))
                .andExpect(jsonPath("$.numero").value("OS-2026-AAAA0001"))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.valorTotal").value(0));
    }

    @Test
    void aberturaInvalidaDa400() throws Exception {
        mvc.perform(post("/api/ordens-servico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON).content("""
                        {"documentoCliente":"","placaVeiculo":"ABC1D23","descricaoProblema":"",
                         "servicos":[{"itemId":null,"quantidade":0}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.documentoCliente").exists())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
    }

    @Test
    void consultaStatusEHistorico() throws Exception {
        OrdemServico os = os();
        when(ordens.consultarStatus(10L)).thenReturn(StatusOS.EM_DIAGNOSTICO);
        when(ordens.consultarHistorico(10L)).thenReturn(os.getHistorico());
        when(ordens.buscarPorId(10L)).thenReturn(os);

        mvc.perform(get("/api/ordens-servico/10/status").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
        mvc.perform(get("/api/ordens-servico/10/historico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].para").value("RECEBIDA"))
                .andExpect(jsonPath("$[0].de").doesNotExist());
        mvc.perform(get("/api/ordens-servico/10").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void listaAtivasPorPadraoEFiltraPorStatus() throws Exception {
        when(ordens.listarAtivas()).thenReturn(List.of(os()));
        when(ordens.listarPorStatus(StatusOS.FINALIZADA)).thenReturn(List.of());

        mvc.perform(get("/api/ordens-servico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        mvc.perform(get("/api/ordens-servico").param("status", "FINALIZADA").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/ordens-servico").param("status", "NAO_EXISTE").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ fluxo

    @Test
    void acoesDoFluxoUsamOsCodigosCertos() throws Exception {
        when(ordens.iniciarDiagnostico(10L)).thenReturn(os());
        when(ordens.adicionarServico(eq(10L), any())).thenReturn(os());
        when(ordens.adicionarPeca(eq(10L), any())).thenReturn(os());
        when(ordens.solicitarOrcamento(10L)).thenReturn(os());
        when(ordens.aprovarOrcamento(10L, null)).thenReturn(os());
        when(ordens.recusarOrcamento(eq(10L), any())).thenReturn(os());
        when(ordens.cancelar(eq(10L), any())).thenReturn(os());
        when(ordens.entregar(10L)).thenReturn(os());

        mvc.perform(post("/api/ordens-servico/10/diagnostico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
        mvc.perform(post("/api/ordens-servico/10/servicos").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                .content("{\"itemId\":1,\"quantidade\":2}")).andExpect(status().isCreated());
        mvc.perform(post("/api/ordens-servico/10/pecas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                .content("{\"itemId\":3,\"quantidade\":1}")).andExpect(status().isCreated());
        mvc.perform(post("/api/ordens-servico/10/orcamento").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isAccepted());
        mvc.perform(post("/api/ordens-servico/10/orcamento/aprovacao").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isAccepted());
        mvc.perform(post("/api/ordens-servico/10/orcamento/recusa").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                .content("{\"motivo\":\"caro\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/ordens-servico/10/cancelamento").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
        mvc.perform(post("/api/ordens-servico/10/entrega").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());

        verify(ordens).recusarOrcamento(10L, "caro");
        verify(ordens).cancelar(eq(10L), isNull());
    }

    @Test
    void aprovacaoComMetodoDePagamentoEConsultaDaSaga() throws Exception {
        when(ordens.aprovarOrcamento(eq(10L), any())).thenReturn(os());
        var saga = br.com.oficina.os.domain.model.Saga.iniciar("saga-1", 10L, "corr-1", T0, T0.plusSeconds(60));
        when(ordens.consultarSagas(10L)).thenReturn(List.of(saga));

        mvc.perform(post("/api/ordens-servico/10/orcamento/aprovacao").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                .content("{\"metodoPagamento\":\"FUND\"}")).andExpect(status().isAccepted());
        mvc.perform(get("/api/ordens-servico/10/saga").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("saga-1"))
                .andExpect(jsonPath("$[0].osId").value(10));

        verify(ordens).aprovarOrcamento(10L, "FUND");
    }

    // ------------------------------------------------------------ mapeamento de erros

    @Test
    void naoEncontradaDa404() throws Exception {
        when(ordens.buscarPorId(99L)).thenThrow(new RecursoNaoEncontradoException("OS não encontrada: 99"));
        mvc.perform(get("/api/ordens-servico/99").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void transicaoInvalidaDa409() throws Exception {
        when(ordens.iniciarDiagnostico(10L))
                .thenThrow(new TransicaoStatusInvalidaException(StatusOS.ENTREGUE, StatusOS.EM_DIAGNOSTICO));
        mvc.perform(post("/api/ordens-servico/10/diagnostico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict());
    }

    @Test
    void regraDeNegocioDa422() throws Exception {
        when(ordens.entregar(10L)).thenThrow(new RegraDeNegocioException("ainda não finalizada"));
        mvc.perform(post("/api/ordens-servico/10/entrega").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void alteracaoConcorrenteEIntegridadeDao409() throws Exception {
        when(ordens.entregar(10L)).thenThrow(new OptimisticLockingFailureException("versão"));
        when(ordens.iniciarDiagnostico(10L)).thenThrow(new DataIntegrityViolationException("duplicado"));
        mvc.perform(post("/api/ordens-servico/10/entrega").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").exists());
        mvc.perform(post("/api/ordens-servico/10/diagnostico").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void erroInesperadoDa500SemVazarDetalhes() throws Exception {
        when(ordens.buscarPorId(10L)).thenThrow(new IllegalStateException("segredo interno"));
        mvc.perform(get("/api/ordens-servico/10").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado."));
    }
}
