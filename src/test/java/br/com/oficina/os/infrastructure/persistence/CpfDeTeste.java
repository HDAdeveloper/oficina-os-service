package br.com.oficina.os.infrastructure.persistence;

/** Gera CPFs com dígitos verificadores válidos, um diferente para cada número. */
public final class CpfDeTeste {

    private CpfDeTeste() {
    }

    public static String gerar(int n) {
        int[] d = new int[11];
        long base = 100_000_000L + n;          // 9 dígitos, não todos iguais
        for (int i = 8; i >= 0; i--) {
            d[i] = (int) (base % 10);
            base /= 10;
        }
        d[9] = digito(d, 9);
        d[10] = digito(d, 10);
        StringBuilder sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    private static int digito(int[] d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += d[i] * (tamanho + 1 - i);
        }
        int r = (soma * 10) % 11;
        return r == 10 ? 0 : r;
    }
}
