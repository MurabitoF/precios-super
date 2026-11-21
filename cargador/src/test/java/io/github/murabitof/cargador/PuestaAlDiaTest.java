package io.github.murabitof.cargador;

import static io.github.murabitof.cargador.ZipsSepa.comercio;
import static io.github.murabitof.cargador.ZipsSepa.fila;
import static io.github.murabitof.cargador.ZipsSepa.productos;
import static io.github.murabitof.cargador.ZipsSepa.sepa;
import static io.github.murabitof.cargador.ZipsSepa.zipComercio;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Escenarios de puesta al día y recarga, contra libsql-server y el archivo simulado en floci. */
@SpringBootTest(properties = {"carga.lote=2", "carga.bucket=archivo"})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class PuestaAlDiaTest {

    static final LocalDate F1 = LocalDate.of(2026, 10, 28);
    static final LocalDate F2 = LocalDate.of(2026, 11, 4);
    static final LocalDate F3 = LocalDate.of(2026, 11, 11);
    static final String COCA = "7790895000997";

    @Container
    static final GenericContainer<?> FLOCI = new GenericContainer<>("floci/floci:2.4.0").withExposedPorts(4566);

    static {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @DynamicPropertySource
    static void floci(DynamicPropertyRegistry r) {
        r.add("carga.s3-endpoint", PuestaAlDiaTest::endpoint);
    }

    static String endpoint() {
        return "http://" + FLOCI.getHost() + ":" + FLOCI.getMappedPort(4566);
    }

    @Autowired CargadorApplication.Cargador cargador;
    @Autowired Turso turso;
    @TempDir Path dir;
    S3Client s3;

    @BeforeEach
    void vaciar() {
        for (var t : List.of("carga", "bandera", "producto", "precio")) turso.ejecutar("DELETE FROM " + t, List.of());
        s3 = S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint()))
                .forcePathStyle(true).build();
        if (s3.listBuckets().buckets().stream().noneMatch(b -> b.name().equals("archivo"))) {
            s3.createBucket(r -> r.bucket("archivo"));
        }
        s3.listObjectsV2(r -> r.bucket("archivo")).contents()
                .forEach(o -> s3.deleteObject(r -> r.bucket("archivo").key(o.key())));
    }

    /** Archiva un ZIP válido para la fecha, con un precio de COCA. */
    void archivar(LocalDate fecha) throws Exception {
        subir(fecha, sepa(dir, zipComercio(comercio(10, 1), productos(fila(10, 1, 1, COCA, "100.00")))));
    }

    void subir(LocalDate fecha, Path zip) throws Exception {
        s3.putObject(r -> r.bucket("archivo").key("sepa/" + fecha + ".zip"), RequestBody.fromBytes(Files.readAllBytes(zip)));
    }

    List<LocalDate> completas() {
        return turso.consultar("SELECT fecha FROM carga WHERE completa_en IS NOT NULL ORDER BY completa_en").stream()
                .map(f -> LocalDate.parse((String) f.get(0))).toList();
    }

    List<List<Object>> estado() {
        return turso.consultar("SELECT 'carga' t, fecha, completa_en c FROM carga UNION ALL "
                + "SELECT 'precio', fecha, ean || ' ' || centavos FROM precio ORDER BY 1, 2, 3");
    }

    /** Fecha y hora ISO 8601 en UTC: el orden de texto es el orden temporal. */
    String completaEn(LocalDate fecha) {
        return (String) turso.consultar("SELECT completa_en FROM carga WHERE fecha = ?", fecha.toString()).get(0).get(0);
    }

    @Test
    @DisplayName("A1 Fecha archivada y sin cargar")
    void fechaSinCargar() throws Exception {
        archivar(F2);
        assertTrue(cargador.ponerAlDia());
        assertEquals(List.of(F2), completas());
    }

    @Test
    @DisplayName("A2 Varias fechas pendientes")
    void variasPendientes() throws Exception {
        archivar(F3);
        archivar(F1);
        archivar(F2);
        assertTrue(cargador.ponerAlDia());
        assertEquals(List.of(F1, F2, F3), completas(), "completas en orden de fecha");
    }

    @Test
    @DisplayName("A3 Nada pendiente")
    void nadaPendiente() throws Exception {
        archivar(F2);
        cargador.ponerAlDia();
        var antes = estado();
        assertTrue(cargador.ponerAlDia());
        assertEquals(antes, estado());
    }

    @Test
    @DisplayName("A4 Fecha incompleta de una corrida anterior")
    void fechaIncompleta() throws Exception {
        archivar(F2);
        turso.ejecutar("INSERT INTO carga (fecha) VALUES (?)", List.of(F2.toString()));
        turso.ejecutar("INSERT INTO precio VALUES (?, 10, 1, ?, 9900, 1)", List.of(F2.toString(), COCA));
        assertTrue(cargador.ponerAlDia());
        assertEquals(List.of(F2), completas());
        assertEquals(List.of(List.of(10000L)), turso.consultar("SELECT centavos FROM precio WHERE fecha = ?", F2.toString()));
    }

    @Test
    @DisplayName("A5 Una fecha falla y otra no")
    void unaFallaOtraNo() throws Exception {
        var danado = dir.resolve("danado.zip");
        Files.write(danado, new byte[] {1, 2, 3});
        subir(F1, danado);
        archivar(F2);
        assertFalse(cargador.ponerAlDia());
        assertEquals(List.of(F2), completas());
    }

    @Test
    @DisplayName("A6 Recarga de una fecha completa")
    void recargaCompleta() throws Exception {
        archivar(F2);
        cargador.ponerAlDia();
        var antes = completaEn(F2);
        assertTrue(cargador.recargar(F2));
        var despues = completaEn(F2);
        assertTrue(despues.compareTo(antes) > 0, "se volvió a cargar");
        assertEquals(List.of(F2), completas());
    }

    @Test
    @DisplayName("A7 Fecha que no está en el archivo")
    void fechaNoArchivada() throws Exception {
        archivar(F2);
        cargador.ponerAlDia();
        var antes = estado();
        assertFalse(cargador.recargar(F3));
        assertEquals(antes, estado());
    }
}
