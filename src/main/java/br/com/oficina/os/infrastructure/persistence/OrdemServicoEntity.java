package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.StatusOS;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A coluna {@code versao} dá o bloqueio otimista: duas pessoas alterando a mesma OS ao mesmo tempo
 * não se sobrescrevem; a segunda recebe um erro de conflito.
 */
@Entity
@Table(name = "ordem_servico")
public class OrdemServicoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    @Column(name = "versao", nullable = false)
    Long versao;

    @Column(nullable = false)
    String numero;

    @Column(name = "cliente_id", nullable = false)
    String clienteId;

    @Column(name = "veiculo_id", nullable = false)
    Long veiculoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StatusOS status;

    @Column(name = "descricao_problema", nullable = false, length = 1000)
    String descricaoProblema;

    @Column(length = 1000)
    String observacoes;

    @Column(name = "orcamento_id")
    String orcamentoId;

    @Column(name = "motivo_cancelamento", length = 500)
    String motivoCancelamento;

    @Column(name = "data_abertura", nullable = false)
    Instant dataAbertura;

    @Column(name = "data_inicio")
    Instant dataInicio;

    @Column(name = "data_finalizacao")
    Instant dataFinalizacao;

    @Column(name = "data_entrega")
    Instant dataEntrega;

    @ElementCollection
    @CollectionTable(name = "os_item_servico", joinColumns = @JoinColumn(name = "os_id"))
    @OrderColumn(name = "ordem")
    List<ItemServicoEmb> itensServico = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "os_item_peca", joinColumns = @JoinColumn(name = "os_id"))
    @OrderColumn(name = "ordem")
    List<ItemPecaEmb> itensPeca = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "os_historico", joinColumns = @JoinColumn(name = "os_id"))
    @OrderColumn(name = "ordem")
    List<HistoricoEmb> historico = new ArrayList<>();

    protected OrdemServicoEntity() {
    }
}
