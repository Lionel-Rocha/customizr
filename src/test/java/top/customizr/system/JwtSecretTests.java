package top.customizr.system;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import top.customizr.system.security.JwtService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sem JWT_SECRET o servidor sobe com uma chave temporaria; token de outra chave nao vale. */
class JwtSecretTests {

    private static JwtService service(String secret) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "secretKey", secret);
        ReflectionTestUtils.setField(s, "jwtExpiration", 60_000L);
        return s;
    }

    @Test
    void withoutSecretUsesARandomKeyThatStillWorks() {
        UserDetails ana = User.withUsername("ana").password("x").authorities(List.of()).build();
        JwtService temp = service("");
        String token = temp.generateToken(ana);
        assertEquals("ana", temp.extractUsername(token));
        assertTrue(temp.isTokenValid(token, ana));

        // outro servidor (ou o mesmo depois de reiniciar) tem outra chave: o token antigo nao vale
        assertThrows(RuntimeException.class, () -> service(null).extractUsername(token));
    }

    @Test
    void configuredSecretKeepsWorking() {
        UserDetails ana = User.withUsername("ana").password("x").authorities(List.of()).build();
        String secret = "SuaChaveSecretaSuperLongaEPoderosaParaAssinarTokensJWT1234567890";
        String token = service(secret).generateToken(ana);
        assertEquals("ana", service(secret).extractUsername(token), "mesma chave: o login continua valendo");
    }
}
