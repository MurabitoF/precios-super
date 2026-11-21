package io.github.murabitof.cargador;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente mínimo de la API HTTP de Turso (Hrana, /v2/pipeline): un pedido por sentencia. No hay un
 * driver JDBC usable para libSQL (ver design.md). Al arrancar crea las tablas de schema.sql si no existen.
 */
@Component
class Turso {

    private static final ObjectMapper JSON = new ObjectMapper();

    // HTTP/1.1: sobre http:// el intento de pasar a HTTP/2 deja colgado a libsql-server.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final URI pipeline;
    private final String token;

    Turso(@Value("${carga.turso.url}") String url, @Value("${carga.turso.token}") String token) throws IOException {
        this.pipeline = URI.create(url.replaceFirst("^libsql://", "https://").replaceFirst("/$", "") + "/v2/pipeline");
        this.token = token;
        var schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        pedir(Map.of("type", "sequence", "sql", schema));
    }

    /** Ejecuta una sentencia y devuelve la cantidad de filas afectadas. */
    long ejecutar(String sql, List<?> args) {
        return resultado(sql, args).path("affected_row_count").asLong();
    }

    /** Ejecuta una consulta y devuelve sus filas; los enteros vienen como Long y el texto como String. */
    List<List<Object>> consultar(String sql, Object... args) {
        var filas = new ArrayList<List<Object>>();
        for (var fila : resultado(sql, List.of(args)).path("rows")) {
            var valores = new ArrayList<Object>();
            for (var v : fila) {
                valores.add(switch (v.path("type").asString()) {
                    case "null" -> null;
                    case "integer" -> Long.parseLong(v.path("value").asString());
                    case "float" -> v.path("value").asDouble();
                    default -> v.path("value").asString();
                });
            }
            filas.add(valores);
        }
        return filas;
    }

    private JsonNode resultado(String sql, List<?> args) {
        var valores = args.stream().map(Turso::valor).toList();
        return pedir(Map.of("type", "execute", "stmt", Map.of("sql", sql, "args", valores))).path("result");
    }

    private static Map<String, Object> valor(Object v) {
        return switch (v) {
            case null -> Map.of("type", "null");
            // Hrana manda los enteros como texto para no perder precisión en JSON.
            case Integer i -> Map.of("type", "integer", "value", i.toString());
            case Long l -> Map.of("type", "integer", "value", l.toString());
            case String s -> Map.of("type", "text", "value", s);
            default -> throw new IllegalArgumentException("tipo no soportado: " + v.getClass());
        };
    }

    private JsonNode pedir(Map<String, Object> pedido) {
        var cuerpo = JSON.writeValueAsString(Map.of("requests", List.of(pedido, Map.of("type", "close"))));
        var req = HttpRequest.newBuilder(pipeline)
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (!token.isEmpty()) req.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp;
        try {
            resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Turso respondió " + resp.statusCode() + ": " + resp.body());
        }
        var r = JSON.readTree(resp.body()).path("results").path(0);
        if (!r.path("type").asString().equals("ok")) {
            throw new IllegalStateException("Turso: " + r.path("error").path("message").asString());
        }
        return r.path("response");
    }
}
