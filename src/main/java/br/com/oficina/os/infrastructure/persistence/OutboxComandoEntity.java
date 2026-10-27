package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Comando da Saga gravado na mesma transação do estado; o relay lê os pendentes em ordem de {@code id}. */
@Entity
@Table(name = "outbox_comando")
public class OutboxComandoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "message_id", nullable = false, length = 36)
    String messageId;

    @Column(nullable = false, length = 60)
    String tipo;

    /** {@code billing} ou {@code execution}: vira o atributo {@code target} da mensagem SNS. */
    @Column(nullable = false, length = 20)
    String destino;

    @Column(name = "os_id")
    Long osId;

    @Column(name = "saga_id")
    String sagaId;

    @Column(name = "correlation_id")
    String correlationId;

    @Column(nullable = false, length = 8000)
    String payload;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "publicado_em")
    Instant publicadoEm;

    @Column(nullable = false)
    int tentativas;

    @Column(name = "ultimo_erro", length = 500)
    String ultimoErro;

    protected OutboxComandoEntity() {
    }

    public static OutboxComandoEntity novo(String messageId, String tipo, String destino, Long osId, String sagaId,
                                           String correlationId, String payload, Instant criadoEm) {
        OutboxComandoEntity e = new OutboxComandoEntity();
        e.messageId = messageId;
        e.tipo = tipo;
        e.destino = destino;
        e.osId = osId;
        e.sagaId = sagaId;
        e.correlationId = correlationId;
        e.payload = payload;
        e.criadoEm = criadoEm;
        return e;
    }

    public void marcarPublicado(Instant quando) {
        this.publicadoEm = quando;
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getTipo() { return tipo; }
    public String getDestino() { return destino; }
    public Long getOsId() { return osId; }
    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getPublicadoEm() { return publicadoEm; }
    public int getTentativas() { return tentativas; }
    public String getUltimoErro() { return ultimoErro; }
}
