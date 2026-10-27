# language: pt
Funcionalidade: Saga da ordem de serviço
  Para que a oficina só execute um serviço pago e com peças garantidas
  O OS Service orquestra Billing e Execution e desfaz o que for preciso quando algo falha

  Contexto:
    Dado que existe uma ordem de serviço em diagnóstico com 1 serviço e 2 peças
    Quando o mecânico solicita o orçamento
    E o Billing cria o orçamento
    Então a saga da OS está em "AGUARDANDO_APROVACAO"
    E a OS está com status "AGUARDANDO_APROVACAO"

  Cenário: Caminho feliz - do orçamento à finalização
    Quando o cliente aprova o orçamento pagando com "APRO"
    Então a saga da OS está em "COBRANDO"
    Quando o Billing aprova o pagamento
    Então a saga da OS está em "RESERVANDO_ESTOQUE"
    Quando o Execution reserva o estoque
    Então a saga da OS está em "EM_EXECUCAO"
    E a OS está com status "EM_EXECUCAO"
    Quando o Execution inicia a execução
    E o Execution finaliza a execução
    Então a saga da OS está em "CONCLUIDA"
    E a OS está com status "FINALIZADA"
    E os comandos enviados foram, em ordem: "CRIAR_ORCAMENTO, COBRAR_ORCAMENTO, RESERVAR_ESTOQUE"

  Cenário: Compensação A - pagamento recusado cancela o orçamento e a OS volta ao diagnóstico
    Quando o cliente aprova o orçamento pagando com "FUND"
    E o Billing recusa o pagamento com o motivo "saldo insuficiente"
    Então a saga da OS está em "CANCELANDO_ORCAMENTO"
    Quando o Billing confirma o cancelamento do orçamento
    Então a saga da OS está em "ENCERRADA"
    E a OS está com status "EM_DIAGNOSTICO"
    E os comandos enviados foram, em ordem: "CRIAR_ORCAMENTO, COBRAR_ORCAMENTO, CANCELAR_ORCAMENTO"

  Cenário: Compensação B - sem estoque, o pagamento é estornado e a OS é cancelada
    Quando o cliente aprova o orçamento pagando com "APRO"
    E o Billing aprova o pagamento
    E o Execution informa que não há estoque suficiente
    Então a saga da OS está em "ESTORNANDO"
    Quando o Billing confirma o estorno do pagamento
    Então a saga da OS está em "CANCELADA"
    E a OS está com status "CANCELADA"
    E os comandos enviados foram, em ordem: "CRIAR_ORCAMENTO, COBRAR_ORCAMENTO, RESERVAR_ESTOQUE, ESTORNAR_PAGAMENTO"
    E nenhum comando "LIBERAR_ESTOQUE" foi enviado

  Cenário: Compensação C - falha na execução devolve o estoque e estorna o pagamento
    Quando o cliente aprova o orçamento pagando com "APRO"
    E o Billing aprova o pagamento
    E o Execution reserva o estoque
    E a execução falha com o motivo "motor travou"
    Então a saga da OS está em "LIBERANDO_ESTOQUE"
    Quando o Execution confirma a devolução do estoque
    Então a saga da OS está em "ESTORNANDO"
    Quando o Billing confirma o estorno do pagamento
    Então a saga da OS está em "CANCELADA"
    E a OS está com status "CANCELADA"
    E os comandos enviados foram, em ordem: "CRIAR_ORCAMENTO, COBRAR_ORCAMENTO, RESERVAR_ESTOQUE, LIBERAR_ESTOQUE, ESTORNAR_PAGAMENTO"

  Cenário: Mensagem repetida não duplica efeitos (idempotência)
    Quando o cliente aprova o orçamento pagando com "APRO"
    E o Billing aprova o pagamento
    E o Billing aprova o pagamento novamente com a mesma mensagem
    Então a saga da OS está em "RESERVANDO_ESTOQUE"
    E o comando "RESERVAR_ESTOQUE" foi enviado 1 vez
