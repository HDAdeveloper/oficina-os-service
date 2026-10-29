package br.com.oficina.os.infrastructure.web;


import br.com.oficina.os.infrastructure.persistence.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Percorre a API de ponta a ponta (login, cadastros, abertura e fluxo da OS) com o contexto Spring
 * completo, Spring Security real e PostgreSQL de verdade. Evita acentos nas verificações porque o
 * MockMvc decodifica o corpo da resposta em ISO-8859-1 por padrão.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFluxoCompletoTest extends PostgresTestBase {

    @Autowired MockMvc mvc;

    private String token() throws Exception {
        String corpo = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.token");
    }

    private ResultActions chamar(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (json != null) {
            req.contentType(APPLICATION_JSON).content(json);
        }
        return mvc.perform(req);
    }

    /** Cliente: o id é o CPF/CNPJ (texto), não um número gerado. */
    private String criarComIdTexto(String url, String token, String json) throws Exception {
        String corpo = chamar(post(url), token, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    private long criar(String url, String token, String json) throws Exception {
        String corpo = chamar(post(url), token, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    @Test
    void rotasProtegidasExigemToken() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ordens-servico")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentacaoESaudeSaoPublicas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/ordens-servico")))
                .andExpect(content().string(containsString("bearerAuth")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void fluxoCompletoDeCadastrosEOrdemDeServico() throws Exception {
        String t = token();

        // Cadastros
        String clienteId = criarComIdTexto("/api/clientes", t,
                "{\"nome\":\"Fernanda Lima\",\"documento\":\"111.444.777-35\",\"email\":\"fe@x.com\"}");
        assertThat(clienteId).isEqualTo("11144477735");
        long veiculoId = criar("/api/veiculos", t, "{\"placa\":\"API1A23\",\"marca\":\"Honda\","
                + "\"modelo\":\"Civic\",\"ano\":2020,\"clienteId\":\"" + clienteId + "\"}");
        long servicoId = criar("/api/servicos", t,
                "{\"nome\":\"Alinhamento API\",\"preco\":90.00,\"tempoEstimadoMinutos\":45}");
        long pecaId = criar("/api/pecas", t,
                "{\"codigo\":\"API-PASTILHA\",\"nome\":\"Pastilha\",\"precoUnitario\":80.00}");

        // Consultas e atualizações dos cadastros
        chamar(get("/api/clientes/" + clienteId), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("111.444.777-35"));
        chamar(get("/api/clientes/documento/11144477735"), t, null).andExpect(status().isOk());
        chamar(get("/api/clientes"), t, null).andExpect(status().isOk());
        chamar(put("/api/clientes/" + clienteId), t, "{\"nome\":\"Fernanda L. Lima\",\"email\":\"fe@x.com\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Fernanda L. Lima"));
        chamar(get("/api/veiculos/placa/api1a23"), t, null).andExpect(status().isOk());
        chamar(get("/api/veiculos/" + veiculoId), t, null).andExpect(status().isOk());
        chamar(get("/api/veiculos?clienteId=" + clienteId), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("API1A23"));
        chamar(get("/api/veiculos"), t, null).andExpect(status().isOk());
        chamar(put("/api/veiculos/" + veiculoId), t, "{\"marca\":\"Honda\",\"modelo\":\"City\",\"ano\":2021}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelo").value("City"));
        chamar(get("/api/servicos/" + servicoId), t, null).andExpect(status().isOk());
        chamar(get("/api/servicos"), t, null).andExpect(status().isOk());
        chamar(put("/api/servicos/" + servicoId), t,
                "{\"nome\":\"Alinhamento API\",\"preco\":95.00,\"tempoEstimadoMinutos\":50}")
                .andExpect(status().isOk());
        chamar(get("/api/pecas/" + pecaId), t, null).andExpect(status().isOk());
        chamar(get("/api/pecas/codigo/API-PASTILHA"), t, null).andExpect(status().isOk());
        chamar(get("/api/pecas"), t, null).andExpect(status().isOk());
        chamar(put("/api/pecas/" + pecaId), t, "{\"nome\":\"Pastilha de freio\",\"precoUnitario\":85.00}")
                .andExpect(status().isOk());

        // Regras: duplicidade e inexistente
        chamar(post("/api/clientes"), t, "{\"nome\":\"Outra\",\"documento\":\"11144477735\"}")
                .andExpect(status().isConflict());
        chamar(get("/api/clientes/999999"), t, null).andExpect(status().isNotFound());
        chamar(post("/api/clientes"), t, "{\"nome\":\"Invalido\",\"documento\":\"123\"}")
                .andExpect(status().isUnprocessableEntity());

        // Ordem de serviço
        String corpoOs = chamar(post("/api/ordens-servico"), t, "{\"documentoCliente\":\"11144477735\","
                + "\"placaVeiculo\":\"api1a23\",\"descricaoProblema\":\"Freio rangendo\","
                + "\"servicos\":[{\"itemId\":" + servicoId + ",\"quantidade\":1}],"
                + "\"pecas\":[{\"itemId\":" + pecaId + ",\"quantidade\":2}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.valorTotal").value(265.0))
                .andReturn().getResponse().getContentAsString();
        long osId = ((Number) JsonPath.read(corpoOs, "$.id")).longValue();
        assertThat((String) JsonPath.read(corpoOs, "$.numero")).matches("OS-\\d{4}-[0-9A-F]{8}");

        String base = "/api/ordens-servico/" + osId;
        chamar(post(base + "/diagnostico"), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
        chamar(post(base + "/servicos"), t, "{\"itemId\":" + servicoId + ",\"quantidade\":1}")
                .andExpect(status().isCreated());
        chamar(post(base + "/pecas"), t, "{\"itemId\":" + pecaId + ",\"quantidade\":1}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.valorTotal").value(445.0));
        chamar(post(base + "/orcamento"), t, null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
        chamar(get(base + "/status"), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
        chamar(get(base + "/historico"), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        chamar(get(base), t, null).andExpect(status().isOk());
        chamar(get("/api/ordens-servico"), t, null).andExpect(status().isOk());
        chamar(get("/api/ordens-servico?status=EM_DIAGNOSTICO"), t, null).andExpect(status().isOk());

        // A Saga ainda não respondeu (Etapa 5): aprovar/recusar não são permitidos agora
        chamar(post(base + "/orcamento/aprovacao"), t, null).andExpect(status().isUnprocessableEntity());
        chamar(post(base + "/orcamento/recusa"), t, null).andExpect(status().isUnprocessableEntity());

        // Cancelamento e transição inválida depois dele
        chamar(post(base + "/cancelamento"), t, "{\"motivo\":\"cliente desistiu\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        chamar(post(base + "/diagnostico"), t, null).andExpect(status().isConflict());

        // Desativações
        chamar(delete("/api/pecas/" + pecaId), t, null).andExpect(status().isNoContent());
        chamar(delete("/api/servicos/" + servicoId), t, null).andExpect(status().isNoContent());
        chamar(delete("/api/veiculos/" + veiculoId), t, null).andExpect(status().isNoContent());
        chamar(delete("/api/clientes/" + clienteId), t, null).andExpect(status().isNoContent());
        chamar(get("/api/clientes/" + clienteId), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // Cliente desativado volta com a reativação (e o cadastro do mesmo CPF orienta a usá-la)
        chamar(post("/api/clientes"), t,
                "{\"nome\":\"Fernanda Lima\",\"documento\":\"111.444.777-35\"}")
                .andExpect(status().isConflict()).andExpect(content().string(containsString("reativa")));
        chamar(post("/api/clientes/" + clienteId + "/reativacao"), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }
}
