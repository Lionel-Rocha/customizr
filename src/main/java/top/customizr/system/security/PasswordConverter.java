package top.customizr.system.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Converter
public class PasswordConverter implements AttributeConverter<String, String> {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String convertToDatabaseColumn(String rawPassword) {
        if (rawPassword == null || rawPassword.startsWith("$2a$")) {
            return rawPassword;
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public String convertToEntityAttribute(String dbPassword) {
        return dbPassword; // Não precisa descriptografar (BCrypt é mão única)
    }
}