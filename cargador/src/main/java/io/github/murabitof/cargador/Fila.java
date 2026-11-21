package io.github.murabitof.cargador;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Una línea de datos de productos.csv, con el precio de lista ya pasado a centavos. */
record Fila(int comercio, int bandera, String ean, String indicador, String descripcion, String marca,
            long centavos) {

    static final String CABECERA = "id_comercio|id_bandera|id_sucursal|id_producto|productos_ean|"
            + "productos_descripcion|productos_cantidad_presentacion|productos_unidad_medida_presentacion|"
            + "productos_marca|productos_precio_lista|productos_precio_referencia|productos_cantidad_referencia|"
            + "productos_unidad_medida_referencia|productos_precio_unitario_promo1|productos_leyenda_promo1|"
            + "productos_precio_unitario_promo2|productos_leyenda_promo2";
    static final int COLUMNAS = 17;
    static final long SIN_PRECIO = -1;

    enum Descarte { INDICADOR, CODIGO, PREFIJO_2, PRECIO }

    // Hasta 10 dígitos enteros y 2 decimales: ".60", "1500", "32900.5", "04480.".
    private static final Pattern PRECIO = Pattern.compile("(\\d{0,10})(?:\\.(\\d{0,2}))?");
    private static final Pattern EAN = Pattern.compile("\\d{13}");

    /** Devuelve null si la línea no es de datos (pie, línea en blanco). Falla si tiene otra cantidad de columnas. */
    static Fila parse(String linea) {
        if (linea.indexOf('|') < 0) return null;
        String[] c = linea.split("\\|", -1);
        if (c.length != COLUMNAS) {
            throw new IllegalArgumentException("fila con " + c.length + " columnas: " + linea);
        }
        return new Fila(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), c[3], c[4],
                c[5].trim(), c[8].trim(), centavos(c[9]).orElse(SIN_PRECIO));
    }

    static OptionalLong centavos(String precio) {
        var m = PRECIO.matcher(precio);
        if (!m.matches()) return OptionalLong.empty();
        String entero = m.group(1), decimales = m.group(2) == null ? "" : m.group(2);
        if (entero.isEmpty() && decimales.isEmpty()) return OptionalLong.empty();
        long e = entero.isEmpty() ? 0 : Long.parseLong(entero);
        return OptionalLong.of(e * 100 + Integer.parseInt((decimales + "00").substring(0, 2)));
    }

    /** Motivo por el que la fila no entra al cálculo, o null si entra. */
    Descarte descarte() {
        if (!indicador.equals("1")) return Descarte.INDICADOR;
        if (!EAN.matcher(ean).matches()) return Descarte.CODIGO;
        if (ean.charAt(0) == '2') return Descarte.PREFIJO_2;
        if (centavos <= 0) return Descarte.PRECIO;
        return null;
    }

    /** Compara una cabecera sin el BOM ni los espacios de relleno. */
    static boolean mismaCabecera(String leida, String esperada) {
        return leida != null && leida.replace("﻿", "").strip().equals(esperada);
    }
}
