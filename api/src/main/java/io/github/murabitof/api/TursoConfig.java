package io.github.murabitof.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
class TursoConfig {

    /**
     * En Lambda el token de solo lectura se lee de SSM (api.turso.token-param) al iniciar, y queda en la
     * foto cifrada de SnapStart. En local y en los tests se pasa directo (api.turso.token).
     */
    @Bean
    Turso turso(@Value("${api.turso.url}") String url, @Value("${api.turso.token}") String token,
                @Value("${api.turso.token-param}") String parametro) {
        if (!parametro.isEmpty()) {
            try (var ssm = SsmClient.create()) {
                token = ssm.getParameter(r -> r.name(parametro).withDecryption(true)).parameter().value();
            }
        }
        return new Turso(url, token);
    }
}
