package br.com.oficina.os.infrastructure.web;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.infrastructure.seguranca.JwtConfig;
import br.com.oficina.os.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.os.infrastructure.web.controller.ClienteController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class ClienteControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GestaoClientes clientes;

    private static Cliente ana() {
        return new Cliente(Documento.of("52998224725"), "Ana Souza", "ana@x.com", "1199", "Rua A", true);
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void cadastraDevolve201ComLocation() throws Exception {
        when(clientes.cadastrar(any())).thenReturn(ana());

        mvc.perform(post("/api/clientes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Ana Souza\",\"documento\":\"529.982.247-25\",\"email\":\"ana@x.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/clientes/52998224725")))
                .andExpect(jsonPath("$.id").value("52998224725"))
                .andExpect(jsonPath("$.documento").value("529.982.247-25"))
                .andExpect(jsonPath("$.tipoDocumento").value("CPF"));
    }

    @Test
    void dadosInvalidosDao400ComErrosPorCampo() throws Exception {
        mvc.perform(post("/api/clientes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"documento\":\"1\",\"email\":\"sem-arroba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.email").exists());
    }

    @Test
    void jsonMalformadoDa400() throws Exception {
        mvc.perform(post("/api/clientes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON).content("{nome"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentoDuplicadoDa409() throws Exception {
        when(clientes.cadastrar(any())).thenThrow(new ConflitoException("Já existe cliente"));

        mvc.perform(post("/api/clientes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"documento\":\"52998224725\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void listaEBusca() throws Exception {
        when(clientes.listar()).thenReturn(List.of(ana()));
        when(clientes.buscarPorId("52998224725")).thenReturn(ana());
        when(clientes.buscarPorDocumento("529.982.247-25")).thenReturn(ana());
        when(clientes.buscarPorId("9")).thenThrow(new RecursoNaoEncontradoException("Cliente não encontrado: 9"));

        mvc.perform(get("/api/clientes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nome").value("Ana Souza"));
        mvc.perform(get("/api/clientes/52998224725").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@x.com"));
        mvc.perform(get("/api/clientes/documento/529.982.247-25").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("52998224725"));
        mvc.perform(get("/api/clientes/9").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void atualizaEDesativa() throws Exception {
        when(clientes.atualizar(any(), any())).thenReturn(ana());

        mvc.perform(put("/api/clientes/52998224725").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Ana Souza\",\"email\":\"ana@x.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/52998224725").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isNoContent());
        verify(clientes).desativar("52998224725");
    }

    @Test
    void reativaDevolve200ComOClienteAtivo() throws Exception {
        when(clientes.reativar("52998224725")).thenReturn(ana());

        mvc.perform(post("/api/clientes/52998224725/reativacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("52998224725"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void reativarClienteInexistenteDa404() throws Exception {
        when(clientes.reativar("9")).thenThrow(new RecursoNaoEncontradoException("Cliente não encontrado: 9"));

        mvc.perform(post("/api/clientes/9/reativacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reativarExigeAdmin() throws Exception {
        mvc.perform(post("/api/clientes/52998224725/reativacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }
}
