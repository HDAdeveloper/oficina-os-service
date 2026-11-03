package br.com.oficina.os.infrastructure.web;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.infrastructure.seguranca.JwtConfig;
import br.com.oficina.os.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.os.infrastructure.web.controller.SagaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SagaController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class SagaControllerTest {

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean GestaoOrdensServico ordens;

    private static Saga saga() {
        Saga s = Saga.iniciar("saga-1", 7L, "corr", T0, T0.plusSeconds(60));
        s.registrarLink("https://mp/pagar/1");
        s.mover(EstadoSaga.AGUARDANDO_PAGAMENTO, "Link gerado", T0.plusSeconds(3), T0.plusSeconds(3600));
        return s;
    }

    @Test
    void semTokenOuComoClienteNaoVeAsSagas() throws Exception {
        mvc.perform(get("/api/sagas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sagas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListaAsUltimasSagasComEstadoLinkEDiario() throws Exception {
        when(ordens.listarSagasRecentes(30)).thenReturn(List.of(saga()));

        mvc.perform(get("/api/sagas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("saga-1"))
                .andExpect(jsonPath("$[0].osId").value(7))
                .andExpect(jsonPath("$[0].estado").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$[0].linkPagamento").value("https://mp/pagar/1"))
                .andExpect(jsonPath("$[0].passos[0].tipo").value("SAGA_INICIADA"));
    }

    @Test
    void limiteVaiParaOCasoDeUso() throws Exception {
        mvc.perform(get("/api/sagas?limite=5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        verify(ordens).listarSagasRecentes(5);
    }

    @Test
    void paginaDoPainelEPublicaMasOsDadosNao() throws Exception {
        // a página estática é liberada (sem JWT); os dados que ela mostra vêm de /api/sagas, que exige ADMIN
        mvc.perform(get("/saga-monitor.html")).andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
