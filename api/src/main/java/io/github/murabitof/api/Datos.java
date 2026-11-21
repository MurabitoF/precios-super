package io.github.murabitof.api;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fechas completas, catálogo y banderas en memoria, más las consultas por producto. El catálogo se busca
 * acá y no en Turso: un LIKE recorrería los 77 mil productos en cada pedido. Todo se renueva cuando
 * cambia la última fecha completa, que se consulta como mucho una vez por api.refresco.
 */
@Component
class Datos {

    record Producto(String ean, String descripcion, String marca, String normalizado) {}

    record Punto(String fecha, long comercio, long bandera, long centavos, long sucursales) {}

    /** version: el último completa_en, que cambia con una fecha nueva y también con una recarga. */
    record Estado(String version, List<String> fechas, Set<String> completas, List<Producto> catalogo,
                  Map<String, Producto> porEan, Map<List<Long>, String> banderas) {}

    private static final String VERSION = "SELECT max(completa_en) FROM carga WHERE completa_en IS NOT NULL";

    private static final int PAGINA_CATALOGO = 10_000;

    private final Turso turso;
    private final Duration refresco;
    private Estado estado;
    private Instant verificado;

    Datos(Turso turso, @Value("${api.refresco}") Duration refresco) {
        this.turso = turso;
        this.refresco = refresco;
        this.estado = cargar();
        this.verificado = Instant.now();
    }

    synchronized Estado estado() {
        if (Instant.now().isAfter(verificado.plus(refresco))) {
            var version = (String) turso.consultar(VERSION, List.of()).getFirst().getFirst();
            if (!java.util.Objects.equals(version, estado.version())) estado = cargar();
            verificado = Instant.now();
        }
        return estado;
    }

    private Estado cargar() {
        var version = (String) turso.consultar(VERSION, List.of()).getFirst().getFirst();
        var fechas = turso.consultar("SELECT fecha FROM carga WHERE completa_en IS NOT NULL ORDER BY fecha", List.of())
                .stream().map(f -> (String) f.getFirst()).toList();
        var banderas = new HashMap<List<Long>, String>();
        turso.consultar("SELECT id_comercio, id_bandera, nombre FROM bandera", List.of())
                .forEach(f -> banderas.put(List.of((Long) f.get(0), (Long) f.get(1)), (String) f.get(2)));
        // De a páginas: libsql-server rechaza respuestas grandes.
        var catalogo = new ArrayList<Producto>();
        var ultimo = "";
        while (true) {
            var pagina = turso.consultar("SELECT ean, descripcion, marca FROM producto WHERE ean > ? ORDER BY ean LIMIT ?",
                    List.of(ultimo, PAGINA_CATALOGO));
            for (var f : pagina) {
                var d = (String) f.get(1);
                var m = (String) f.get(2);
                catalogo.add(new Producto((String) f.get(0), d, m, normalizar(d + " " + m)));
            }
            if (pagina.size() < PAGINA_CATALOGO) break;
            ultimo = (String) pagina.getLast().getFirst();
        }
        var porEan = new HashMap<String, Producto>();
        catalogo.forEach(p -> porEan.put(p.ean(), p));
        catalogo.sort(Comparator.comparing(Producto::descripcion).thenComparing(Producto::ean));
        return new Estado(version, fechas, Set.copyOf(fechas), Collections.unmodifiableList(catalogo), porEan, banderas);
    }

    /** Mayúsculas y sin acentos: "Cañuelas" y "CANUELAS" coinciden. */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    /** Precios del producto en las fechas completas, por el índice precio_ean. */
    List<Punto> serie(String ean) {
        var completas = estado().completas();
        return turso.consultar("SELECT fecha, id_comercio, id_bandera, centavos, sucursales FROM precio WHERE ean = ? ORDER BY fecha",
                        List.of(ean)).stream()
                .filter(f -> completas.contains((String) f.get(0)))
                .map(f -> new Punto((String) f.get(0), (Long) f.get(1), (Long) f.get(2), (Long) f.get(3), (Long) f.get(4)))
                .toList();
    }

    /** Por EAN (en el orden pedido), los precios de cada bandera que lo publicó en las dos fechas. */
    Map<String, List<Indice.Par>> pares(List<String> eans, String desde, String hasta) {
        var marcas = String.join(", ", Collections.nCopies(eans.size(), "?"));
        var args = new ArrayList<Object>(List.of(desde, hasta));
        args.addAll(eans);
        var precios = new HashMap<List<Object>, long[]>();
        for (var f : turso.consultar("SELECT ean, id_comercio, id_bandera, fecha, centavos FROM precio "
                + "WHERE fecha IN (?, ?) AND ean IN (" + marcas + ")", args)) {
            var p = precios.computeIfAbsent(List.of(f.get(0), f.get(1), f.get(2)), k -> new long[] {-1, -1});
            p[f.get(3).equals(desde) ? 0 : 1] = (Long) f.get(4);
        }
        var r = new LinkedHashMap<String, List<Indice.Par>>();
        eans.forEach(e -> r.put(e, new ArrayList<>()));
        precios.forEach((k, p) -> {
            if (p[0] > 0 && p[1] > 0) r.get((String) k.getFirst()).add(new Indice.Par(p[0], p[1]));
        });
        return r;
    }
}
