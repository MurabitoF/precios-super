package io.github.murabitof.cargador;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carga una fecha desde su ZIP. Cada lote es un INSERT de hasta carga.lote filas, atómico por sí
 * mismo. La fecha queda incompleta hasta el último paso, así que una falla en el medio no se ve y la
 * próxima corrida la rehace entera.
 */
@Component
class Carga {

    private final Turso turso;
    private final int lote;

    Carga(Turso turso, @Value("${carga.lote}") int lote) {
        this.turso = turso;
        this.lote = lote;
    }

    /** Devuelve la cantidad de precios guardados. */
    int cargar(LocalDate fecha, Path zip) throws IOException {
        var f = fecha.toString();
        turso.ejecutar("""
                INSERT INTO carga (fecha, completa_en, filas) VALUES (?, NULL, NULL)
                ON CONFLICT (fecha) DO UPDATE SET completa_en = NULL, filas = NULL""", List.of(f));
        turso.ejecutar("DELETE FROM precio WHERE fecha = ?", List.of(f));

        var lector = new LectorZip();
        int[] filas = {0};
        lector.leer(zip, c -> {
            enLotes(c.banderas(), "INSERT INTO bandera (id_comercio, id_bandera, razon_social, nombre, fecha) VALUES ", 5, """
                     ON CONFLICT (id_comercio, id_bandera) DO UPDATE
                    SET razon_social = excluded.razon_social, nombre = excluded.nombre, fecha = excluded.fecha
                    WHERE bandera.fecha <= excluded.fecha""",
                    b -> List.of(b.comercio(), b.bandera(), b.razonSocial(), b.nombre(), f));
            enLotes(c.precios(), "INSERT INTO precio (fecha, id_comercio, id_bandera, ean, centavos, sucursales) VALUES ", 6, "",
                    p -> List.of(f, p.comercio(), p.bandera(), p.ean(), p.centavos(), p.sucursales()));
            filas[0] += c.precios().size();
        });

        var productos = lector.descripciones.masFrecuentes();
        enLotes(new ArrayList<>(productos.entrySet()), "INSERT INTO producto (ean, descripcion, marca, fecha) VALUES ", 4, """
                 ON CONFLICT (ean) DO UPDATE
                SET descripcion = excluded.descripcion, marca = excluded.marca, fecha = excluded.fecha
                WHERE producto.fecha <= excluded.fecha""",
                e -> List.of(e.getKey(), e.getValue().descripcion(), e.getValue().marca(), f));

        System.out.printf("%s: %d precios, %d productos, descartes %s%n", fecha, filas[0], productos.size(), lector.descartes);
        if (filas[0] == 0) throw new IOException(fecha + ": ninguna fila pasó los filtros");
        turso.ejecutar("UPDATE carga SET completa_en = strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), filas = ? WHERE fecha = ?",
                List.of(filas[0], f));
        return filas[0];
    }

    /** Un INSERT por lote: "inicio (?, ...), (?, ...) fin". */
    private <T> void enLotes(List<T> filas, String inicio, int columnas, String fin, Function<T, List<?>> args) {
        var fila = "(" + "?, ".repeat(columnas - 1) + "?)";
        for (int i = 0; i < filas.size(); i += lote) {
            var parte = filas.subList(i, Math.min(i + lote, filas.size()));
            var valores = new ArrayList<Object>(parte.size() * columnas);
            parte.forEach(x -> valores.addAll(args.apply(x)));
            turso.ejecutar(inicio + String.join(", ", Collections.nCopies(parte.size(), fila)) + fin, valores);
        }
    }
}
