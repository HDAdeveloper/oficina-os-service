package br.com.oficina.os.domain;

import br.com.oficina.os.domain.exception.PlacaInvalidaException;
import br.com.oficina.os.domain.model.Placa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacaTest {

    @Test
    void normalizaPlacaAntiga() {
        assertThat(Placa.of("abc-1234").valor()).isEqualTo("ABC1234");
    }

    @Test
    void aceitaPlacaMercosul() {
        assertThat(Placa.of("abc1d23").valor()).isEqualTo("ABC1D23");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB12345", "ABCD123", "ABC12D3", "1234567", ""})
    void rejeitaPlacasInvalidas(String bruta) {
        assertThatThrownBy(() -> Placa.of(bruta)).isInstanceOf(PlacaInvalidaException.class);
    }

    @Test
    void rejeitaPlacaNula() {
        assertThatThrownBy(() -> Placa.of(null)).isInstanceOf(PlacaInvalidaException.class);
        assertThat(Placa.normalizar(null)).isNull();
    }
}
