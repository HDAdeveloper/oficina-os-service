package br.com.oficina.os.infrastructure.seguranca;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Valida o login do administrador e emite o JWT. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SegurancaProperties props;
    private final Clock relogio;

    public TokenService(JwtEncoder encoder, SegurancaProperties props, Clock relogio) {
        this.encoder = encoder;
        this.props = props;
        this.relogio = relogio;
    }

    public record Token(String valor, long expiraEmSegundos) {
    }

    public Token autenticar(String usuario, String senha) {
        // Comparação em tempo constante, para não vazar por tempo de resposta se o usuário/senha acertou
        boolean usuarioOk = iguais(props.adminUsuario(), usuario);
        boolean senhaOk = iguais(props.adminSenha(), senha);
        if (!(usuarioOk & senhaOk)) {
            throw new CredenciaisInvalidasException();
        }
        Instant agora = Instant.now(relogio);
        Duration validade = Duration.ofMinutes(props.expiracaoMinutos());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("oficina-os-service")
                .subject(usuario)
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .claim("roles", List.of("ADMIN"))
                .build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(jwt, validade.toSeconds());
    }

    private static boolean iguais(String esperado, String informado) {
        if (esperado == null || informado == null) {
            return false;
        }
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                informado.getBytes(StandardCharsets.UTF_8));
    }
}
