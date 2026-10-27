package br.com.oficina.os.infrastructure.web;

import br.com.oficina.os.infrastructure.seguranca.JwtConfig;
import br.com.oficina.os.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.os.infrastructure.seguranca.TokenService;
import br.com.oficina.os.infrastructure.web.controller.AuthController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, TokenService.class, AuthControllerTest.RelogioTeste.class})
class AuthControllerTest {

    @TestConfiguration
    static class RelogioTeste {
        @Bean
        Clock relogio() {
            return Clock.systemUTC();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtDecoder decoder;

    private String login(String usuario, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void loginValidoDevolveTokenJwt() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(3600))
                .andExpect(jsonPath("$.token").isNotEmpty());

        String token = JsonPath.read(login("admin", "admin123"), "$.token");
        assertThat(decoder.decode(token).getSubject()).isEqualTo("admin");
    }

    @Test
    void senhaErradaDa401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"outro\",\"senha\":\"admin123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void camposEmBrancoDao400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"\",\"senha\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.usuario").exists())
                .andExpect(jsonPath("$.erros.senha").exists());
    }

    @Test
    void rotaProtegidaExigeTokenValido() throws Exception {
        mvc.perform(get("/api/qualquer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/qualquer").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());

        String token = JsonPath.read(login("admin", "admin123"), "$.token");
        // Autenticado: passa pela segurança e só então "não encontra" a rota inexistente
        mvc.perform(get("/api/qualquer").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
