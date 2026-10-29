# OS Service — documentação do serviço

## Responsabilidade

Contexto **Ordens de Serviço** (subdomínio **core**). Cuida de clientes, veículos, catálogo de serviços e peças e do ciclo de vida da
Ordem de Serviço. É também o **orquestrador da Saga**: guarda o estado de cada Saga, envia comandos aos outros serviços e reage aos
eventos, disparando as compensações quando um passo falha ([ADR-002](adr/002-saga-orquestrada.md)).

- **Banco:** PostgreSQL 16 (`oficina_os`), migrações Flyway `V1__criar_schema.sql` e `V2__saga_e_outbox.sql`.
- **Tabelas:** `cliente`, `veiculo`, `servico`, `peca`, `ordem_servico`, `os_item_servico`, `os_item_peca`, `os_historico`,
  `saga_instance`, `saga_passo`, `outbox_comando`, `mensagem_processada`.
- **Porta:** 8080. Swagger UI em `/swagger-ui.html`; coleção Postman em [`postman/`](postman/).

## API REST

Autenticação por JWT (HS256). Papéis: `ADMIN` (login de atendente), `CLIENTE` (token emitido pela Lambda de CPF), `SERVICE` (chamada entre serviços).

| Recurso | Rotas | Papel |
|---|---|---|
| Login | `POST /api/auth/login` | público |
| Clientes | `POST/GET /api/clientes`, `GET/PUT/DELETE /api/clientes/{id}` (o `{id}` é o CPF/CNPJ, com ou sem pontuação), `GET /api/clientes/documento/{documento}` | `ADMIN`; a consulta por documento também aceita `SERVICE` |
| Veículos | `POST/GET /api/veiculos`, `GET/PUT/DELETE /api/veiculos/{id}`, `GET /api/veiculos/placa/{placa}` | `ADMIN` |
| Serviços | `POST/GET /api/servicos`, `GET/PUT/DELETE /api/servicos/{id}` | `ADMIN` |
| Peças | `POST/GET /api/pecas`, `GET/PUT/DELETE /api/pecas/{id}`, `GET /api/pecas/codigo/{codigo}` | `ADMIN` |
| Ordens de serviço | `POST/GET /api/ordens-servico`, `GET /{id}`, `GET /{id}/status`, `GET /{id}/historico`, `GET /{id}/saga` | `ADMIN` |
| Fluxo da OS | `POST /{id}/diagnostico`, `/{id}/servicos`, `/{id}/pecas`, `/{id}/orcamento`, `/{id}/orcamento/aprovacao`, `/{id}/orcamento/recusa`, `/{id}/cancelamento`, `/{id}/entrega` (todas sob `/api/ordens-servico`) | `ADMIN` |
| Área do cliente | `GET /api/minhas-ordens` | `CLIENTE` |
| Saúde | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | público |

Erros no formato RFC 7807 (`application/problem+json`). O cabeçalho `X-Correlation-Id` atravessa REST, mensagens e logs.

## Mensageria

| Direção | Mensagem | Detalhe |
|---|---|---|
| Publica (tópico `oficina-saga-commands`, atributo `target`) | `CRIAR_ORCAMENTO`, `COBRAR_ORCAMENTO`, `CANCELAR_ORCAMENTO`, `ESTORNAR_PAGAMENTO` | `target=billing` |
| Publica | `RESERVAR_ESTOQUE`, `LIBERAR_ESTOQUE` | `target=execution` |
| Consome (fila `os-events`) | `ORCAMENTO_CRIADO`, `ORCAMENTO_FALHOU`, `ORCAMENTO_CANCELADO`, `PAGAMENTO_APROVADO`, `PAGAMENTO_RECUSADO`, `PAGAMENTO_ESTORNADO`, `ESTOQUE_RESERVADO`, `ESTOQUE_INSUFICIENTE`, `ESTOQUE_LIBERADO`, `EXECUCAO_INICIADA`, `EXECUCAO_FINALIZADA`, `EXECUCAO_FALHOU` | idempotente por `messageId`; 3 falhas vão para a DLQ |

Os comandos saem pela **outbox transacional** (`outbox_comando`): o estado da Saga e o comando são gravados juntos e um *relay* os envia depois.
Prazos: se não houver resposta em `SAGA_PRAZO_RESPOSTA_SEGUNDOS` (60), o comando é reenviado até `SAGA_MAX_REENVIOS` (3); depois disso a Saga compensa
ou fica em `ESTORNO_PENDENTE` / `LIBERACAO_PENDENTE` (ação manual). Detalhes em [`eventos.md`](eventos.md).

## Modelo e regras

Agregados: `OrdemServico`, `Saga`, `Cliente`, `Veiculo`, `Servico`, `Peca` (ver [`ddd.md`](ddd.md)). Status da OS:
`RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → EM_EXECUCAO → FINALIZADA → ENTREGUE`, com `CANCELADA` nas compensações. Transições
inválidas são rejeitadas pelo próprio agregado. CPF/CNPJ e placa são validados nos objetos de valor.

## Estrutura do código

`domain/` (modelo e regras, Java puro) · `application/` (casos de uso, `OrquestradorSaga`, portas) · `infrastructure/` (web, persistência JPA,
mensageria SNS/SQS, segurança). Arquitetura hexagonal: a dependência aponta sempre para dentro.

## Testes e qualidade

- Testes unitários e de integração por camada (JUnit 5, Mockito); `domain` e `application` testam-se sem Spring.
- Cobertura mínima de **80% das linhas** aplicada no build (JaCoCo em `mvnw verify`) e em cada Pull Request.
- Análise estática no SonarQube Cloud pela pipeline. Evidências de cobertura: [`evidencias/`](evidencias/).
- Como rodar: `.\mvnw.cmd clean verify` (relatório em `target\site\jacoco\index.html`).

Testes de comportamento (BDD, Cucumber): `src/test/resources/features/saga_ordem_servico.feature` cobre o fluxo completo da Saga, incluindo compensação.

## Documentação compartilhada do projeto

| Documento | Conteúdo |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Visão geral, hexagonal, dados, Saga, mensageria, segurança, CI/CD |
| [`ddd.md`](ddd.md) | Contextos, agregados, estados e linguagem ubíqua (imagens em [`diagramas/ddd/`](diagramas/ddd/)) |
| [`eventos.md`](eventos.md) | Comandos, eventos, envelope, fluxo feliz e compensações da Saga |
| [`adr/`](adr/) | Decisões de arquitetura e justificativas |
