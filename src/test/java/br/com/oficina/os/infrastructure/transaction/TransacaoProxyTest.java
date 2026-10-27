package br.com.oficina.os.infrastructure.transaction;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransacaoProxyTest {

    interface Operacoes {
        String ok();

        void regraViolada();

        void erroGrave();

        void verificada() throws Exception;
    }

    /** Gerenciador de transação falso que só registra o que aconteceu. */
    static class TxFalsa implements PlatformTransactionManager {
        final List<String> eventos = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            eventos.add("begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            eventos.add("commit");
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            eventos.add("rollback");
        }
    }

    private TxFalsa tx;
    private Operacoes proxy;

    @BeforeEach
    void setUp() {
        tx = new TxFalsa();
        Operacoes alvo = new Operacoes() {
            @Override
            public String ok() {
                return "feito";
            }

            @Override
            public void regraViolada() {
                throw new RegraDeNegocioException("não pode");
            }

            @Override
            public void erroGrave() {
                throw new AssertionError("grave");
            }

            @Override
            public void verificada() throws Exception {
                throw new Exception("checada");
            }

            @Override
            public String toString() {
                return "alvo";
            }
        };
        proxy = TransacaoProxy.envolver(Operacoes.class, alvo, new TransactionTemplate(tx));
    }

    @Test
    void confirmaATransacaoQuandoTerminaBem() {
        assertThat(proxy.ok()).isEqualTo("feito");
        assertThat(tx.eventos).containsExactly("begin", "commit");
    }

    @Test
    void desfazAtransacaoEPropagaAExcecaoOriginal() {
        assertThatThrownBy(proxy::regraViolada)
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("não pode");
        assertThat(tx.eventos).containsExactly("begin", "rollback");
    }

    @Test
    void desfazTambemEmErroGrave() {
        assertThatThrownBy(proxy::erroGrave).isInstanceOf(AssertionError.class);
        assertThat(tx.eventos).containsExactly("begin", "rollback");
    }

    @Test
    void excecaoVerificadaViraUndeclaredEDesfaz() {
        assertThatThrownBy(proxy::verificada)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checada");
        assertThat(tx.eventos).containsExactly("begin", "rollback");
    }

    @Test
    void metodosDeObjectNaoAbremTransacao() {
        assertThat(proxy.toString()).isEqualTo("alvo");
        assertThat(tx.eventos).isEmpty();
    }
}
