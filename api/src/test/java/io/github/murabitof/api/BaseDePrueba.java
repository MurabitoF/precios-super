package io.github.murabitof.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.ObjectMapper;

/**
 * libsql-server con el esquema del cargador, y funciones para sembrar datos. La API solo lee: los tests
 * escriben directo por HTTP, sin token, como lo haría la carga.
 */
final class BaseDePrueba {

    static final GenericContainer<?> LIBSQL = new GenericContainer<>("ghcr.io/tursodatabase/libsql-server:v0.24.33")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health"));

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    // completa_en distinto en cada siembra, para que la API note el cambio y renueve su caché.
    private static final AtomicInteger RELOJ = new AtomicInteger();

    static {
        LIBSQL.start();
        try {
            enviar(Map.of("type", "sequence", "sql", new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BaseDePrueba() {}

    static String url() {
        return "http://" + LIBSQL.getHost() + ":" + LIBSQL.getMappedPort(8080);
    }

    static void limpiar() {
        for (var t : List.of("carga", "bandera", "producto", "precio")) sql("DELETE FROM " + t);
    }

    static void fecha(String fecha, boolean completa) {
        var completaEn = completa ? String.format("2026-11-21T00:00:%02d.%03dZ", 0, RELOJ.incrementAndGet()) : null;
        sql("INSERT INTO carga (fecha, completa_en, filas) VALUES (?, ?, 0)", fecha, completaEn);
    }

    static void completar(String fecha) {
        sql("UPDATE carga SET completa_en = ? WHERE fecha = ?", String.format("2026-11-21T00:01:00.%03dZ", RELOJ.incrementAndGet()), fecha);
    }

    static void bandera(int comercio, int bandera, String nombre) {
        sql("INSERT INTO bandera VALUES (?, ?, 'Comercio S.A.', ?, '2026-11-04')", comercio, bandera, nombre);
    }

    static void producto(String ean, String descripcion, String marca) {
        sql("INSERT INTO producto VALUES (?, ?, ?, '2026-11-04')", ean, descripcion, marca);
    }

    static void precio(String fecha, int comercio, int bandera, String ean, long centavos) {
        sql("INSERT INTO precio VALUES (?, ?, ?, ?, ?, 3)", fecha, comercio, bandera, ean, centavos);
    }

    static void sql(String sql, Object... args) {
        var valores = java.util.Arrays.stream(args).map(v -> switch (v) {
            case null -> Map.of("type", "null");
            case String s -> Map.of("type", "text", "value", s);
            default -> Map.of("type", "integer", "value", v.toString());
        }).toList();
        enviar(Map.of("type", "execute", "stmt", Map.of("sql", sql, "args", valores)));
    }

    private static void enviar(Map<String, Object> pedido) {
        var cuerpo = JSON.writeValueAsString(Map.of("requests", List.of(pedido, Map.of("type", "close"))));
        try {
            var resp = HTTP.send(HttpRequest.newBuilder(URI.create(url() + "/v2/pipeline"))
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
            var r = JSON.readTree(resp.body()).path("results").path(0);
            if (!r.path("type").asString().equals("ok")) throw new IllegalStateException(resp.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
