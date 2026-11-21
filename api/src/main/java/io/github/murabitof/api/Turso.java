package io.github.murabitof.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente de solo lectura de la API HTTP de Turso (/v2/pipeline). No hay un driver JDBC usable para
 * libSQL. Después de una restauración de SnapStart recrea el cliente HTTP: las conexiones abiertas al
 * tomar la foto ya no sirven.
 */
class Turso implements Resource {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI pipeline;
    private final String token;
    private volatile HttpClient http = nuevoCliente();

    Turso(String url, String token) {
        this.pipeline = URI.create(url.replaceFirst("^libsql://", "https://").replaceFirst("/$", "") + "/v2/pipeline");
        this.token = token;
        Core.getGlobalContext().register(this);
    }

    // HTTP/1.1: sobre http:// el intento de pasar a HTTP/2 deja colgado a libsql-server.
    private static HttpClient nuevoCliente() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {}

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        http = nuevoCliente();
    }

    /** Filas de la consulta; los enteros vienen como Long y el texto como String. */
    List<List<Object>> consultar(String sql, List<?> args) {
        var valores = args.stream().map(Turso::valor).toList();
        var pedido = Map.of("type", "execute", "stmt", Map.of("sql", sql, "args", valores));
        var cuerpo = JSON.writeValueAsString(Map.of("requests", List.of(pedido, Map.of("type", "close"))));
        var req = HttpRequest.newBuilder(pipeline).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(cuerpo));
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
        if (resp.statusCode() != 200) throw new IllegalStateException("Turso respondió " + resp.statusCode());
        var r = JSON.readTree(resp.body()).path("results").path(0);
        if (!r.path("type").asString().equals("ok")) {
            throw new IllegalStateException("Turso: " + r.path("error").path("message").asString());
        }
        var filas = new ArrayList<List<Object>>();
        for (var fila : r.path("response").path("result").path("rows")) {
            var fila2 = new ArrayList<Object>();
            for (var v : fila) {
                fila2.add(switch (v.path("type").asString()) {
                    case "null" -> null;
                    case "integer" -> Long.parseLong(v.path("value").asString());
                    case "float" -> v.path("value").asDouble();
                    default -> v.path("value").asString();
                });
            }
            filas.add(fila2);
        }
        return filas;
    }

    private static Map<String, Object> valor(Object v) {
        return switch (v) {
            case Integer i -> Map.of("type", "integer", "value", i.toString());
            case Long l -> Map.of("type", "integer", "value", l.toString());
            case String s -> Map.of("type", "text", "value", s);
            default -> throw new IllegalArgumentException("tipo no soportado: " + v.getClass());
        };
    }
}
