package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.DocumentoInvalidoException;

/** CPF ou CNPJ válido, guardado só com dígitos. */
public record Documento(String numero, TipoDocumento tipo) {

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** Aceita com ou sem máscara ("529.982.247-25" ou "52998224725"). */
    public static Documento of(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new DocumentoInvalidoException("Documento é obrigatório");
        }
        String digitos = bruto.replaceAll("\\D", "");
        if (digitos.length() == 11 && cpfValido(digitos)) {
            return new Documento(digitos, TipoDocumento.CPF);
        }
        if (digitos.length() == 14 && cnpjValido(digitos)) {
            return new Documento(digitos, TipoDocumento.CNPJ);
        }
        throw new DocumentoInvalidoException("CPF/CNPJ inválido: " + bruto);
    }

    /** Só dígitos, sem validar. Útil para comparar com o que veio na URL. */
    public static String normalizar(String bruto) {
        return bruto == null ? null : bruto.replaceAll("\\D", "");
    }

    public String formatado() {
        if (tipo == TipoDocumento.CPF) {
            return numero.substring(0, 3) + "." + numero.substring(3, 6) + "."
                    + numero.substring(6, 9) + "-" + numero.substring(9);
        }
        return numero.substring(0, 2) + "." + numero.substring(2, 5) + "."
                + numero.substring(5, 8) + "/" + numero.substring(8, 12) + "-" + numero.substring(12);
    }

    private static boolean todosIguais(String digitos) {
        return digitos.chars().distinct().count() == 1;
    }

    private static boolean cpfValido(String d) {
        if (todosIguais(d)) {
            return false;
        }
        int dv1 = digitoCpf(d, 9);
        int dv2 = digitoCpf(d, 10);
        return dv1 == d.charAt(9) - '0' && dv2 == d.charAt(10) - '0';
    }

    /** Dígito verificador do CPF considerando os primeiros {@code qtd} dígitos. */
    private static int digitoCpf(String d, int qtd) {
        int soma = 0;
        for (int i = 0; i < qtd; i++) {
            soma += (d.charAt(i) - '0') * (qtd + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean cnpjValido(String d) {
        if (todosIguais(d)) {
            return false;
        }
        int dv1 = digitoCnpj(d, PESOS_CNPJ_1);
        int dv2 = digitoCnpj(d, PESOS_CNPJ_2);
        return dv1 == d.charAt(12) - '0' && dv2 == d.charAt(13) - '0';
    }

    private static int digitoCnpj(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
