package io.github.murabitof.api;

import static io.github.murabitof.api.BaseDePrueba.bandera;
import static io.github.murabitof.api.BaseDePrueba.completar;
import static io.github.murabitof.api.BaseDePrueba.fecha;
import static io.github.murabitof.api.BaseDePrueba.precio;
import static io.github.murabitof.api.BaseDePrueba.producto;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Un test por escenario del spec, contra libsql-server. La caché se renueva en cada pedido (api.refresco=0). */
@SpringBootTest(properties = "api.refresco=0s")
class ApiTest {

    static final String COCA = "7790895000997";
    static final String SAL = "7790072002080";
    static final String YERBA = "7792710000182";
    static final String F1 = "2026-11-04";
    static final String F2 = "2026-11-11";

    @DynamicPropertySource
    static void libsql(DynamicPropertyRegistry r) {
        r.add("api.turso.url", BaseDePrueba::url);
    }

    @Autowired WebApplicationContext contexto;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        BaseDePrueba.limpiar();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    ResultActions pedir(String url) throws Exception {
        return mvc.perform(get(url));
    }

    /** Dos fechas completas, dos banderas y la Coca en las dos, con un precio por fecha y bandera. */
    void dosSemanas() {
        fecha(F1, true);
        fecha(F2, true);
        bandera(10, 1, "Carrefour");
        bandera(12, 1, "Coto");
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 600000);
        precio(F2, 10, 1, COCA, 610000);
        precio(F1, 12, 1, COCA, 590000);
        precio(F2, 12, 1, COCA, 615000);
    }

    @Test
    @DisplayName("I1 Fecha a medio cargar")
    void fechaAMedioCargar() throws Exception {
        fecha(F1, true);
        fecha(F2, false);
        bandera(10, 1, "Carrefour");
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 600000);
        precio(F2, 10, 1, COCA, 610000);
        pedir("/fechas").andExpect(jsonPath("$", contains(F1)));
        pedir("/productos/" + COCA).andExpect(jsonPath("$.precios[0].fecha").value(F1));
        pedir("/productos/" + COCA + "/serie").andExpect(jsonPath("$.banderas[0].puntos[*].fecha", contains(F1)));
    }

    @Test
    @DisplayName("I2 La fecha se completa")
    void laFechaSeCompleta() throws Exception {
        fecha(F1, true);
        fecha(F2, false);
        bandera(10, 1, "Carrefour");
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 600000);
        precio(F2, 10, 1, COCA, 610000);
        pedir("/fechas").andExpect(jsonPath("$", contains(F1)));
        completar(F2);
        pedir("/fechas").andExpect(jsonPath("$", contains(F1, F2)));
        pedir("/productos/" + COCA + "/serie").andExpect(jsonPath("$.banderas[0].puntos[*].fecha", contains(F1, F2)));
    }

    @Test
    @DisplayName("I3 Varias fechas")
    void variasFechas() throws Exception {
        fecha(F2, true);
        fecha(F1, true);
        pedir("/fechas").andExpect(status().isOk()).andExpect(jsonPath("$", contains(F1, F2)));
    }

    @Test
    @DisplayName("I4 Precio con centavos")
    void precioConCentavos() throws Exception {
        fecha(F1, true);
        bandera(10, 1, "Carrefour");
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 123450);
        pedir("/productos/" + COCA).andExpect(jsonPath("$.precios[0].centavos").value(123450));
    }

    @Test
    @DisplayName("I5 Búsqueda por texto")
    void busquedaPorTexto() throws Exception {
        fecha(F1, true);
        producto(COCA, "GASEO COLA", "COCA");
        producto("7790895000998", "GASEOSA COLA ZERO", "COCA-COLA");
        producto(YERBA, "YERBA TRADICIONAL", "AMANDA");
        pedir("/productos?q=coca").andExpect(status().isOk())
                .andExpect(jsonPath("$.productos", hasSize(2)))
                .andExpect(jsonPath("$.productos[*].ean", not(hasItem(YERBA))))
                .andExpect(jsonPath("$.productos[0].descripcion").exists())
                .andExpect(jsonPath("$.productos[0].marca").exists());
    }

    @Test
    @DisplayName("I6 Búsqueda por EAN")
    void busquedaPorEan() throws Exception {
        fecha(F1, true);
        producto(COCA, "GASEO COLA", "COCA");
        producto("7790895000998", "GASEOSA COLA ZERO", "COCA-COLA");
        pedir("/productos?q=" + COCA).andExpect(jsonPath("$.productos[*].ean", contains(COCA)));
    }

    @Test
    @DisplayName("I7 Segunda página")
    void segundaPagina() throws Exception {
        fecha(F1, true);
        for (int i = 10; i < 35; i++) producto("77900000000" + i, "GALLETITA " + i, "MARCA");
        pedir("/productos?q=galletita&pagina=2")
                .andExpect(jsonPath("$.productos", hasSize(5)))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    @Test
    @DisplayName("I8 Sin resultados")
    void sinResultados() throws Exception {
        fecha(F1, true);
        producto(COCA, "GASEO COLA", "COCA");
        pedir("/productos?q=inexistente").andExpect(status().isOk()).andExpect(jsonPath("$.productos", hasSize(0)));
    }

    @Test
    @DisplayName("I9 Texto demasiado corto")
    void textoDemasiadoCorto() throws Exception {
        pedir("/productos?q=co").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I10 Producto en varias banderas")
    void productoEnVariasBanderas() throws Exception {
        dosSemanas();
        pedir("/productos/" + COCA)
                .andExpect(jsonPath("$.precios", hasSize(2)))
                .andExpect(jsonPath("$.precios[*].nombre", contains("Carrefour", "Coto")))
                .andExpect(jsonPath("$.precios[*].centavos", contains(610000, 615000)))
                .andExpect(jsonPath("$.precios[*].fecha", contains(F2, F2)))
                .andExpect(jsonPath("$.precios[*].sucursales", contains(3, 3)));
    }

    @Test
    @DisplayName("I11 Bandera que no publicó la última semana")
    void banderaQueNoPublicoLaUltimaSemana() throws Exception {
        dosSemanas();
        bandera(13, 1, "Cooperativa Obrera");
        precio(F1, 13, 1, COCA, 580000);
        pedir("/productos/" + COCA)
                .andExpect(jsonPath("$.precios[?(@.nombre == 'Cooperativa Obrera')].fecha", contains(F1)))
                .andExpect(jsonPath("$.precios[?(@.nombre == 'Cooperativa Obrera')].centavos", contains(580000)));
    }

    @Test
    @DisplayName("I12 EAN inexistente")
    void eanInexistente() throws Exception {
        dosSemanas();
        pedir("/productos/7790000000000").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("I13 EAN mal formado")
    void eanMalFormado() throws Exception {
        pedir("/productos/779089500099A").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I14 Serie de dos semanas")
    void serieDeDosSemanas() throws Exception {
        dosSemanas();
        pedir("/productos/" + COCA + "/serie")
                .andExpect(jsonPath("$.banderas[?(@.nombre == 'Carrefour')].puntos[*].fecha", contains(F1, F2)))
                .andExpect(jsonPath("$.banderas[?(@.nombre == 'Carrefour')].puntos[*].centavos", contains(600000, 610000)));
    }

    @Test
    @DisplayName("I15 Semana sin publicar")
    void semanaSinPublicar() throws Exception {
        dosSemanas();
        bandera(13, 1, "Cooperativa Obrera");
        precio(F1, 13, 1, COCA, 580000);
        pedir("/productos/" + COCA + "/serie")
                .andExpect(jsonPath("$.banderas[?(@.nombre == 'Cooperativa Obrera')].puntos[*].fecha", contains(F1)));
    }

    @Test
    @DisplayName("I16 Canasta por defecto")
    void canastaPorDefecto() throws Exception {
        pedir("/canasta").andExpect(status().isOk())
                .andExpect(jsonPath("$.productos", hasSize(24)))
                .andExpect(jsonPath("$.productos[*].ean", hasItem(COCA)))
                .andExpect(jsonPath("$.productos[0].descripcion").exists());
    }

    @Test
    @DisplayName("I17 Demasiados productos")
    void demasiadosProductos() throws Exception {
        dosSemanas();
        var url = new StringBuilder("/canasta/indice?");
        for (int i = 0; i < 51; i++) url.append("ean=").append(7790000000000L + i).append('&');
        pedir(url.toString()).andExpect(status().isBadRequest());
    }

    /** A: 100→110 en Carrefour y 200→200 en Coto; B: 50→60 en Carrefour. */
    void canastaDelEjemplo() {
        fecha(F1, true);
        fecha(F2, true);
        bandera(10, 1, "Carrefour");
        bandera(12, 1, "Coto");
        producto(COCA, "GASEO COLA", "COCA");
        producto(SAL, "SAL FINA", "CELUSAL");
        precio(F1, 10, 1, COCA, 10000);
        precio(F2, 10, 1, COCA, 11000);
        precio(F1, 12, 1, COCA, 20000);
        precio(F2, 12, 1, COCA, 20000);
        precio(F1, 10, 1, SAL, 5000);
        precio(F2, 10, 1, SAL, 6000);
    }

    @Test
    @DisplayName("I18 Cálculo con dos productos")
    void calculoConDosProductos() throws Exception {
        canastaDelEjemplo();
        pedir("/canasta/indice?ean=" + COCA + "&ean=" + SAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(F1))
                .andExpect(jsonPath("$.hasta").value(F2))
                .andExpect(jsonPath("$.variacion").value(12.19))
                .andExpect(jsonPath("$.productos[?(@.ean == '" + COCA + "')].variacion", contains(4.88)))
                .andExpect(jsonPath("$.productos[?(@.ean == '" + SAL + "')].variacion", contains(20.0)));
    }

    @Test
    @DisplayName("I19 Bandera ausente en una de las fechas")
    void banderaAusente() throws Exception {
        fecha(F1, true);
        fecha(F2, true);
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 10000);
        precio(F2, 10, 1, COCA, 11000);
        precio(F1, 12, 1, COCA, 20000);
        pedir("/canasta/indice?ean=" + COCA)
                .andExpect(jsonPath("$.productos[0].variacion").value(10.0))
                .andExpect(jsonPath("$.productos[0].banderas").value(1));
    }

    @Test
    @DisplayName("I20 Producto sin banderas comparables")
    void productoSinBanderasComparables() throws Exception {
        canastaDelEjemplo();
        producto(YERBA, "YERBA TRADICIONAL", "AMANDA");
        precio(F1, 10, 1, YERBA, 300000);
        pedir("/canasta/indice?ean=" + COCA + "&ean=" + SAL + "&ean=" + YERBA)
                .andExpect(jsonPath("$.excluidos[*].ean", contains(YERBA)))
                .andExpect(jsonPath("$.variacion").value(12.19));
    }

    @Test
    @DisplayName("I21 Ningún producto comparable")
    void ningunProductoComparable() throws Exception {
        fecha(F1, true);
        fecha(F2, true);
        producto(COCA, "GASEO COLA", "COCA");
        precio(F1, 10, 1, COCA, 10000);
        pedir("/canasta/indice?ean=" + COCA).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("I22 Fechas no disponibles o invertidas")
    void fechasNoDisponiblesOInvertidas() throws Exception {
        canastaDelEjemplo();
        pedir("/canasta/indice?ean=" + COCA + "&desde=" + F1 + "&hasta=2026-11-18").andExpect(status().isBadRequest());
        pedir("/canasta/indice?ean=" + COCA + "&desde=" + F2 + "&hasta=" + F1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I23 Canasta propia")
    void canastaPropia() throws Exception {
        canastaDelEjemplo();
        producto(YERBA, "YERBA TRADICIONAL", "AMANDA");
        precio(F1, 10, 1, YERBA, 300000);
        precio(F2, 10, 1, YERBA, 330000);
        pedir("/canasta/indice?ean=" + COCA + "&ean=" + YERBA)
                .andExpect(jsonPath("$.productos[*].ean", contains(COCA, YERBA)))
                .andExpect(jsonPath("$.excluidos", hasSize(0)));
    }

    @Test
    @DisplayName("I24 Consulta desde un navegador")
    void consultaDesdeUnNavegador() throws Exception {
        fecha(F1, true);
        mvc.perform(get("/fechas").header("Origin", "https://otro-dominio.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
