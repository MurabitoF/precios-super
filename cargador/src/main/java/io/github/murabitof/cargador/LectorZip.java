package io.github.murabitof.cargador;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/**
 * Lee un ZIP de SEPA: un ZIP interno por comercio, cada uno con comercio.csv y productos.csv.
 * Entrega, comercio por comercio, sus banderas y sus medianas; las descripciones y los descartes
 * se acumulan sobre toda la fecha.
 */
final class LectorZip {

    static final String CABECERA_COMERCIO = "id_comercio|id_bandera|comercio_cuit|comercio_razon_social|"
            + "comercio_bandera_nombre|comercio_bandera_url|comercio_ultima_actualizacion|comercio_version_sepa";

    record Bandera(int comercio, int bandera, String razonSocial, String nombre) {}

    record Comercio(String archivo, List<Bandera> banderas, List<Resumen.Precio> precios) {}

    final Descripciones descripciones = new Descripciones();
    final Map<Fila.Descarte, Long> descartes = new EnumMap<>(Fila.Descarte.class);

    void leer(Path zip, Consumer<Comercio> porComercio) throws IOException {
        try (var externo = new ZipFile(zip.toFile())) {
            var entradas = Collections.list(externo.entries());
            for (var e : entradas) {
                if (e.isDirectory() || !e.getName().endsWith(".zip")) continue;
                if (e.getSize() == 0) {
                    System.out.println("ZIP vacío, se saltea: " + e.getName());
                    continue;
                }
                // A disco y con ZipFile: los ZIP internos traen tamaños ZIP64 en los encabezados
                // locales, que ZipInputStream no sabe leer.
                var tmp = Files.createTempFile("comercio", ".zip");
                try {
                    try (var in = externo.getInputStream(e)) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    porComercio.accept(leerComercio(e.getName(), tmp));
                } finally {
                    Files.delete(tmp);
                }
            }
        }
    }

    private Comercio leerComercio(String nombre, Path archivo) throws IOException {
        try (var zip = new ZipFile(archivo.toFile())) {
            var comercio = zip.getEntry("comercio.csv");
            var productos = zip.getEntry("productos.csv");
            if (comercio == null || productos == null) {
                throw new IOException(nombre + ": falta comercio.csv o productos.csv");
            }
            List<Bandera> banderas;
            try (var r = lector(zip, comercio)) {
                banderas = leerBanderas(nombre, r);
            }
            try (var r = lector(zip, productos)) {
                return new Comercio(nombre, banderas, leerProductos(nombre, r).medianas());
            }
        }
    }

    private static BufferedReader lector(ZipFile zip, ZipEntry e) throws IOException {
        return new BufferedReader(new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8), 1 << 16);
    }

    private static List<Bandera> leerBanderas(String nombre, BufferedReader r) throws IOException {
        verificarCabecera(nombre + "/comercio.csv", r.readLine(), CABECERA_COMERCIO);
        var banderas = new ArrayList<Bandera>();
        for (var linea = r.readLine(); linea != null; linea = r.readLine()) {
            if (linea.indexOf('|') < 0) continue;
            String[] c = linea.split("\\|", -1);
            banderas.add(new Bandera(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()),
                    c[3].strip(), c[4].strip()));
        }
        return banderas;
    }

    private Resumen leerProductos(String nombre, BufferedReader r) throws IOException {
        verificarCabecera(nombre + "/productos.csv", r.readLine(), Fila.CABECERA);
        var resumen = new Resumen();
        for (var linea = r.readLine(); linea != null; linea = r.readLine()) {
            var f = Fila.parse(linea);
            if (f == null) continue;
            var d = f.descarte();
            if (d != null) {
                descartes.merge(d, 1L, Long::sum);
                continue;
            }
            resumen.agregar(f);
            descripciones.contar(f);
        }
        return resumen;
    }

    private static void verificarCabecera(String archivo, String leida, String esperada) throws IOException {
        if (!Fila.mismaCabecera(leida, esperada)) {
            throw new IOException(archivo + ": cabecera inesperada: " + leida);
        }
    }
}
