package br.com.oficina.os.infrastructure.web;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.infrastructure.seguranca.JwtConfig;
import br.com.oficina.os.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.os.infrastructure.web.controller.ClienteController;
import br.com.oficina.os.infrastructure.web.controller.MinhasOrdensController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem pode o quê: ADMIN (login), CLIENTE (Lambda de CPF) e SERVICE (consulta do cliente pela Lambda). */
@WebMvcTest({ClienteController.class, MinhasOrdensController.class})
@Import({SecurityConfig.class, JwtConfig.class})
class SegurancaPorPapelTest {

    private static final String CPF = "52998224725";

    @Autowired MockMvc mvc;
    @MockitoBean GestaoClientes clientes;
    @MockitoBean GestaoOrdensServico ordens;

    private static RequestPostProcessor papel(String papel) {
        return jwt().jwt(j -> j.subject(CPF)).authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    private static Cliente ana() {
        return new Cliente(Documento.of(CPF), "Ana Souza", "ana@x.com", "1199", "Rua A", true);
    }

    @Test
    void clienteConsultaAsPropriasOrdensPeloSubjectDoToken() throws Exception {
        OrdemServico os = OrdemServico.abrir("OS-2026-AAAA0001", "52998224725", 2L, "Barulho", null,
                Instant.parse("2026-10-22T12:00:00Z"));
        os.atribuirId(10L);
        when(ordens.listarDoCliente(CPF)).thenReturn(List.of(os));

        mvc.perform(get("/api/minhas-ordens").with(papel("CLIENTE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value("OS-2026-AAAA0001"));

        verify(ordens).listarDoCliente(CPF);
    }

    @Test
    void clienteNaoAcessaAreaAdministrativa() throws Exception {
        mvc.perform(get("/api/clientes").with(papel("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes/documento/" + CPF).with(papel("CLIENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void tokenDeServicoSoConsultaClientePorDocumento() throws Exception {
        when(clientes.buscarPorDocumento(CPF)).thenReturn(ana());

        mvc.perform(get("/api/clientes/documento/" + CPF).with(papel("SERVICE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"));
        mvc.perform(get("/api/clientes").with(papel("SERVICE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/minhas-ordens").with(papel("SERVICE"))).andExpect(status().isForbidden());
    }

    @Test
    void administradorNaoUsaAAreaDoCliente() throws Exception {
        when(clientes.listar()).thenReturn(List.of(ana()));

        mvc.perform(get("/api/clientes").with(papel("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/minhas-ordens").with(papel("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/api/minhas-ordens")).andExpect(status().isUnauthorized());
    }
}
