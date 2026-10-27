package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.infrastructure.seguranca.TokenService;
import br.com.oficina.os.infrastructure.web.dto.LoginRequest;
import br.com.oficina.os.infrastructure.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final TokenService tokens;

    public AuthController(TokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica o administrador e devolve um token JWT (Bearer)")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        TokenService.Token t = tokens.autenticar(req.usuario(), req.senha());
        return new TokenResponse(t.valor(), "Bearer", t.expiraEmSegundos());
    }
}
