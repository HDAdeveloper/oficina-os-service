package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.PlacaInvalidaException;

import java.util.regex.Pattern;

/** Placa no padrão antigo (ABC1234) ou Mercosul (ABC1D23), guardada em maiúsculas e sem hífen. */
public record Placa(String valor) {

    private static final Pattern ANTIGA = Pattern.compile("^[A-Z]{3}[0-9]{4}$");
    private static final Pattern MERCOSUL = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");

    public static Placa of(String bruta) {
        String normalizada = normalizar(bruta);
        if (normalizada == null || !(ANTIGA.matcher(normalizada).matches()
                || MERCOSUL.matcher(normalizada).matches())) {
            throw new PlacaInvalidaException("Placa inválida: " + bruta);
        }
        return new Placa(normalizada);
    }

    public static String normalizar(String bruta) {
        return bruta == null ? null : bruta.replaceAll("[\\s-]", "").toUpperCase();
    }
}
