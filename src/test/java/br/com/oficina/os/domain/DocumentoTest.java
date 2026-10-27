package br.com.oficina.os.domain;

import br.com.oficina.os.domain.exception.DocumentoInvalidoException;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.domain.model.TipoDocumento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoTest {

    @Test
    void aceitaCpfComMascara() {
        Documento d = Documento.of("529.982.247-25");
        assertThat(d.tipo()).isEqualTo(TipoDocumento.CPF);
        assertThat(d.numero()).isEqualTo("52998224725");
    }

    @Test
    void aceitaCpfSemMascaraEFormata() {
        assertThat(Documento.of("52998224725").formatado()).isEqualTo("529.982.247-25");
    }

    @Test
    void aceitaCnpjEFormata() {
        Documento d = Documento.of("11.222.333/0001-81");
        assertThat(d.tipo()).isEqualTo(TipoDocumento.CNPJ);
        assertThat(d.formatado()).isEqualTo("11.222.333/0001-81");
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224726", "11111111111", "11222333000182", "00000000000000", "123", "abc"})
    void rejeitaDocumentosInvalidos(String bruto) {
        assertThatThrownBy(() -> Documento.of(bruto)).isInstanceOf(DocumentoInvalidoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", ""})
    void rejeitaDocumentoVazio(String bruto) {
        assertThatThrownBy(() -> Documento.of(bruto))
                .isInstanceOf(DocumentoInvalidoException.class)
                .hasMessageContaining("obrigatório");
    }

    @Test
    void rejeitaDocumentoNulo() {
        assertThatThrownBy(() -> Documento.of(null)).isInstanceOf(DocumentoInvalidoException.class);
    }

    @Test
    void normalizarRemoveMascaraENulo() {
        assertThat(Documento.normalizar("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Documento.normalizar(null)).isNull();
    }
}
