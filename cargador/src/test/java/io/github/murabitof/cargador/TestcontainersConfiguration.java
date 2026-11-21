package io.github.murabitof.cargador;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** libsql-server, el mismo servidor que usa Turso, compartido por todos los tests de integración. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final GenericContainer<?> LIBSQL = new GenericContainer<>("ghcr.io/tursodatabase/libsql-server:v0.24.33")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health"));

    static {
        LIBSQL.start();
    }

    @Bean
    DynamicPropertyRegistrar libsqlUrl() {
        return r -> r.add("carga.turso.url", () -> "http://" + LIBSQL.getHost() + ":" + LIBSQL.getMappedPort(8080));
    }
}
