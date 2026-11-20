package top.customizr.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signInKey;

    /**
     * Chave dos tokens. Sem jwt.secret (variavel JWT_SECRET) usa uma chave aleatoria criada agora: ninguem consegue
     * forjar login, so que os logins abertos caem quando o servidor reinicia.
     */
    private synchronized SecretKey getSignInKey() {
        if (signInKey == null) {
            if (secretKey == null || secretKey.isBlank()) {
                byte[] random = new byte[64];
                new java.security.SecureRandom().nextBytes(random);
                org.slf4j.LoggerFactory.getLogger(JwtService.class).warn("JWT_SECRET nao configurado: usando uma chave "
                        + "temporaria (os logins caem quando o servidor reiniciar). Crie a variavel JWT_SECRET.");
                signInKey = Keys.hmacShaKeyFor(random);
            } else {
                signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
            }
        }
        return signInKey;
    }
}