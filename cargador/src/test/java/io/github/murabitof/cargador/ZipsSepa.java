package io.github.murabitof.cargador;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Arma ZIP de SEPA de prueba a partir de texto CSV, con el mismo formato que el portal. */
final class ZipsSepa {

    static final String PIE = "\r\nUltima actualizacion: 2026-11-04T01:11:09-03:00\r\n";

    /** Línea de productos.csv con indicador de EAN 1, descripción y marca fijas y sin promoción. */
    static String fila(int comercio, int bandera, int sucursal, String ean, String precio) {
        return fila(comercio, bandera, sucursal, ean, "1", "Producto", "Marca", precio, "");
    }

    static String fila(int comercio, int bandera, int sucursal, String ean, String indicador,
                       String descripcion, String marca, String precio, String promo) {
        return String.join("|", "" + comercio, "" + bandera, "" + sucursal, ean, indicador, descripcion,
                "1", "uni", marca, precio, precio, "1", "uni", promo, promo.isEmpty() ? "" : "PROMO", "", "");
    }

    static String productos(String... filas) {
        return "﻿" + Fila.CABECERA + "\r\n" + String.join("\r\n", filas) + "\r\n" + PIE;
    }

    /** comercio.csv con una línea por bandera, nombre "Bandera N" y razón social "Comercio C S.A.". */
    static String comercio(int comercio, int... banderas) {
        var sb = new StringBuilder("﻿" + LectorZip.CABECERA_COMERCIO + "\r\n");
        for (int b : banderas) {
            sb.append(comercio).append('|').append(b).append("|30000000000|Comercio ").append(comercio)
                    .append(" S.A.|Bandera ").append(b).append("|http://x|2026-11-04T01:00:00-03:00|1.0\r\n");
        }
        return sb.append(PIE).toString();
    }

    /** ZIP interno de un comercio. */
    static byte[] zipComercio(String comercioCsv, String productosCsv) {
        return zip(new String[] {"comercio.csv", "sucursales.csv", "productos.csv"},
                new byte[][] {bytes(comercioCsv), bytes("id_comercio|id_bandera|id_sucursal\r\n"), bytes(productosCsv)});
    }

    /** ZIP de la fecha, con un ZIP interno por comercio dentro de una carpeta, como el del portal. */
    static Path sepa(Path dir, byte[]... comercios) {
        var nombres = new String[comercios.length];
        for (int i = 0; i < comercios.length; i++) nombres[i] = "2026-11-04/sepa_comercio-" + i + ".zip";
        try {
            return Files.write(Files.createTempFile(dir, "sepa", ".zip"), zip(nombres, comercios));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] zip(String[] nombres, byte[][] contenidos) {
        var out = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(out)) {
            for (int i = 0; i < nombres.length; i++) {
                z.putNextEntry(new ZipEntry(nombres[i]));
                z.write(contenidos[i]);
                z.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
