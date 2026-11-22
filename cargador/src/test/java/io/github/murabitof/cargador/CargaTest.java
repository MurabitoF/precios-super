package io.github.murabitof.cargador;

import static io.github.murabitof.cargador.ZipsSepa.comercio;
import static io.github.murabitof.cargador.ZipsSepa.fila;
import static io.github.murabitof.cargador.ZipsSepa.productos;
import static io.github.murabitof.cargador.ZipsSepa.sepa;
import static io.github.murabitof.cargador.ZipsSepa.zipComercio;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Escenarios del spec contra libsql-server, con lotes de 2 filas para que toda carga pase por varios lotes. */
@SpringBootTest(properties = "carga.lote=2")
@Import(TestcontainersConfiguration.class)
class CargaTest {

    static final LocalDate FECHA = LocalDate.of(2026, 11, 4);
    static final String DIA = "2026-11-04";
    static final String COCA = "7790895000997";
    static final String ACEITE = "7790070012050";

    @Autowired Carga carga;
    @Autowired Turso turso;
    @TempDir Path dir;

    @BeforeEach
    void vaciar() {
        for (var t : List.of("carga", "bandera", "producto", "precio")) turso.ejecutar("DELETE FROM " + t, List.of());
    }

    /** Un comercio (10) con una bandera (1) y las filas dadas. */
    Path unComercio(String... filas) {
        return sepa(dir, zipComercio(comercio(10, 1), productos(filas)));
    }

    List<List<Object>> precios() {
        return turso.consultar("SELECT fecha, id_comercio, id_bandera, ean, centavos, sucursales FROM precio "
                + "ORDER BY fecha, id_comercio, id_bandera, ean");
    }

    /** [centavos, sucursales] */
    List<Object> precio(int bandera, String ean) {
        return turso.consultar("SELECT centavos, sucursales FROM precio WHERE id_bandera = ? AND ean = ?", bandera, ean).get(0);
    }

    long cantidad(String ean) {
        return (Long) turso.consultar("SELECT count(*) FROM precio WHERE ean = ?", ean).get(0).get(0);
    }

    Object completaEn(LocalDate fecha) {
        var filas = turso.consultar("SELECT completa_en FROM carga WHERE fecha = ?", fecha.toString());
        return filas.isEmpty() ? null : filas.get(0).get(0);
    }

    /** [descripcion, marca, fecha] */
    List<Object> producto(String ean) {
        return turso.consultar("SELECT descripcion, marca, fecha FROM producto WHERE ean = ?", ean).get(0);
    }

    String descripcionCon(String... descripciones) throws Exception {
        var filas = new String[descripciones.length];
        for (int i = 0; i < descripciones.length; i++) {
            filas[i] = fila(10, 1, i + 1, COCA, "1", descripciones[i], "COCA", "100.00", "");
        }
        carga.cargar(FECHA, unComercio(filas));
        return (String) producto(COCA).get(0);
    }

    @Test
    @DisplayName("P1 Cantidad impar de sucursales")
    void cantidadImpar() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 2, COCA, "300.00"),
                fila(10, 1, 3, COCA, "120.00")));
        assertEquals(1, cantidad(COCA));
        assertEquals(List.of(12000L, 3L), precio(1, COCA));
    }

    @Test
    @DisplayName("P2 Cantidad par de sucursales")
    void cantidadPar() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 2, COCA, "100.01")));
        assertEquals(10001L, precio(1, COCA).get(0));
    }

    @Test
    @DisplayName("P3 Un precio por bandera")
    void unPrecioPorBandera() throws Exception {
        carga.cargar(FECHA, sepa(dir, zipComercio(comercio(10, 1, 2), productos(
                fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 2, COCA, "110.00"),
                fila(10, 2, 3, COCA, "200.00"), fila(10, 2, 4, COCA, "220.00")))));
        assertEquals(List.of(10500L, 2L), precio(1, COCA));
        assertEquals(List.of(21000L, 2L), precio(2, COCA));
    }

    @Test
    @DisplayName("P4 Precio de promoción ignorado")
    void promocionIgnorada() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "1", "Producto", "Marca", "100.00", "80.00")));
        assertEquals(10000L, precio(1, COCA).get(0));
    }

    @Test
    @DisplayName("P5 Código interno del comercio")
    void codigoInterno() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "0", "Producto", "Marca", "100.00", ""),
                fila(10, 1, 1, ACEITE, "100.00")));
        assertEquals(0, cantidad(COCA));
    }

    @Test
    @DisplayName("P6 Código con prefijo 2")
    void prefijo2() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, "2000000000001", "100.00"), fila(10, 1, 1, ACEITE, "100.00")));
        assertEquals(0, cantidad("2000000000001"));
    }

    @Test
    @DisplayName("P7 Código que no tiene 13 dígitos")
    void codigoSin13Digitos() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, "779089500099A", "100.00"), fila(10, 1, 1, "779089500099", "100.00"),
                fila(10, 1, 1, "77908950009970", "100.00"), fila(10, 1, 1, ACEITE, "100.00")));
        assertEquals(List.of(List.of(ACEITE)), turso.consultar("SELECT ean FROM precio"));
    }

    @Test
    @DisplayName("P8 Precio en cero o negativo")
    void precioEnCero() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "0.00"), fila(10, 1, 2, COCA, "100.00"),
                fila(10, 1, 3, COCA, "110.00")));
        assertEquals(List.of(10500L, 2L), precio(1, COCA));
    }

    @Test
    @DisplayName("P9 Precio que no es un número")
    void precioNoNumerico() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, ""), fila(10, 1, 2, COCA, "abc"),
                fila(10, 1, 3, COCA, "100.00")));
        assertEquals(List.of(10000L, 1L), precio(1, COCA));
        assertNotNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P10 Pie y líneas en blanco")
    void pieYLineasEnBlanco() throws Exception {
        var csv = "﻿" + Fila.CABECERA + "\r\n" + fila(10, 1, 1, COCA, "100.00")
                + "\r\n\r\n \r\n\u0000\r\nUltima actualizacion: 2026-11-04T01:11:09-03:00\r\n\r\n";
        assertEquals(1, carga.cargar(FECHA, sepa(dir, zipComercio(comercio(10, 1), csv))));
        assertNotNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P10b Descripción partida en dos líneas")
    void descripcionPartida() throws Exception {
        // Como en el ZIP del 2026-11-18 (comercio 12): la descripción empieza con un salto de línea.
        carga.cargar(FECHA, unComercio(
                fila(10, 1, 1, COCA, "1", "\r\nPREMEZCLA ROSCA", "MAMA COCINA", "100.00", ""),
                fila(10, 1, 2, COCA, "1", "PREMEZCLA ROSCA", "MAMA COCINA", "100.00", "")));
        assertEquals(List.of(10000L, 2L), precio(1, COCA));
        assertEquals("PREMEZCLA ROSCA", producto(COCA).get(0));
        assertNotNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P11 Columnas distintas a las esperadas")
    void columnasDistintas() {
        var csv = Fila.CABECERA.replace("productos_precio_lista", "precio") + "\r\n" + fila(10, 1, 1, COCA, "100.00");
        assertThrows(Exception.class, () -> carga.cargar(FECHA, sepa(dir, zipComercio(comercio(10, 1), csv))));
        assertNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P12 ZIP dañado")
    void zipDanado() throws Exception {
        var valido = zipComercio(comercio(10, 1), productos(fila(10, 1, 1, COCA, "100.00")));

        var externo = sepa(dir, valido);
        var bytes = Files.readAllBytes(externo);
        Files.write(externo, Arrays.copyOf(bytes, bytes.length / 2));
        assertThrows(Exception.class, () -> carga.cargar(FECHA, externo));
        assertNull(completaEn(FECHA));

        var interno = sepa(dir, Arrays.copyOf(valido, valido.length / 2));
        assertThrows(Exception.class, () -> carga.cargar(FECHA, interno));
        assertNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P13 Carga sin precios")
    void cargaSinPrecios() {
        assertThrows(Exception.class, () -> carga.cargar(FECHA, unComercio(fila(10, 1, 1, "2000000000001", "100.00"))));
        assertNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P14 Comercio con ZIP vacío")
    void comercioConZipVacio() throws Exception {
        carga.cargar(FECHA, sepa(dir, new byte[0], zipComercio(comercio(10, 1), productos(fila(10, 1, 1, COCA, "100.00")))));
        assertEquals(1, cantidad(COCA));
        assertNotNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P15 Producto nuevo")
    void productoNuevo() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "1", "GASEOSA COCA COLA 2,25 L", "COCA COLA", "100.00", "")));
        assertEquals(List.of("GASEOSA COCA COLA 2,25 L", "COCA COLA", DIA), producto(COCA));
    }

    @Test
    @DisplayName("P16 Varias descripciones para un mismo producto")
    void variasDescripciones() throws Exception {
        assertEquals("A", descripcionCon("A", "B", "A", "A", "B", "A", "A"));
    }

    @Test
    @DisplayName("P17 Empate entre descripciones")
    void empateEntreDescripciones() throws Exception {
        assertEquals("Coca cola", descripcionCon("Gaseosa cola", "Coca cola"));
    }

    @Test
    @DisplayName("P18 Fecha vieja cargada después de una nueva")
    void fechaViejaDespues() throws Exception {
        var nueva = LocalDate.of(2026, 11, 11);
        carga.cargar(nueva, unComercio(fila(10, 1, 1, COCA, "1", "Nueva", "COCA", "100.00", "")));
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "1", "Vieja", "COCA", "100.00", "")));
        assertEquals("Nueva", producto(COCA).get(0));
    }

    @Test
    @DisplayName("P19 Bandera")
    void bandera() throws Exception {
        carga.cargar(FECHA, sepa(dir, zipComercio(comercio(10, 1, 2), productos(fila(10, 1, 1, COCA, "100.00")))));
        assertEquals(List.of(
                List.of(1L, "Bandera 1", "Comercio 10 S.A."),
                List.of(2L, "Bandera 2", "Comercio 10 S.A.")),
                turso.consultar("SELECT id_bandera, nombre, razon_social FROM bandera WHERE id_comercio = 10 ORDER BY id_bandera"));
    }

    @Test
    @DisplayName("P20 Misma fecha cargada dos veces")
    void mismaFechaDosVeces() throws Exception {
        var zip = unComercio(fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 2, COCA, "120.00"),
                fila(10, 1, 1, ACEITE, "50.00"), fila(10, 1, 2, ACEITE, "60.00"), fila(10, 1, 3, ACEITE, "70.00"));
        carga.cargar(FECHA, zip);
        var primera = precios();
        carga.cargar(FECHA, zip);
        assertEquals(primera, precios());
    }

    @Test
    @DisplayName("P21 Filas de la carga anterior que ya no corresponden")
    void filasSobrantes() throws Exception {
        turso.ejecutar("INSERT INTO precio VALUES (?, 10, 1, ?, 9900, 1)", List.of(DIA, ACEITE));
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "100.00")));
        assertEquals(0, cantidad(ACEITE));
    }

    @Test
    @DisplayName("P22 Carga exitosa")
    void cargaExitosa() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 1, ACEITE, "50.00"),
                fila(10, 1, 2, ACEITE, "60.00")));
        var c = turso.consultar("SELECT completa_en, filas FROM carga WHERE fecha = ?", DIA).get(0);
        assertNotNull(c.get(0));
        assertEquals(2L, c.get(1));
    }

    @Test
    @DisplayName("P23 Carga interrumpida")
    void cargaInterrumpida() {
        var valido = zipComercio(comercio(10, 1), productos(fila(10, 1, 1, COCA, "100.00"), fila(10, 1, 1, ACEITE, "50.00")));
        var invalido = zipComercio(comercio(11, 1), "otra|cabecera\r\n" + fila(11, 1, 1, COCA, "100.00"));
        assertThrows(Exception.class, () -> carga.cargar(FECHA, sepa(dir, valido, invalido)));
        assertTrue(cantidad(COCA) > 0, "el primer comercio llegó a guardarse");
        assertNull(completaEn(FECHA));
    }

    @Test
    @DisplayName("P24 Recarga interrumpida")
    void recargaInterrumpida() throws Exception {
        carga.cargar(FECHA, unComercio(fila(10, 1, 1, COCA, "100.00")));
        assertNotNull(completaEn(FECHA));
        var invalido = sepa(dir, zipComercio(comercio(10, 1), "otra|cabecera\r\n"));
        assertThrows(Exception.class, () -> carga.cargar(FECHA, invalido));
        assertNull(completaEn(FECHA));
    }
}
