Regla para todo el cambio: los tests a crear son los listados en `design.md` (U1 a U7, P1 a P24, A1 a A7) y ninguno se escribe antes de que Franco apruebe esa lista. Crear otro test, o modificar uno existente o sus datos de prueba, requiere su ok.

## 1. Aprobaciones previas (las hace Franco)

- [x] 1.1 Aprobar la lista de tests de `design.md`, o pedir cambios
- [x] 1.2 Aprobar el paso de los tests P1 a P24 y A1 a A7 de Postgres a libsql-server: mismos escenarios, con las consultas adaptadas a SQLite (precio en centavos enteros, fechas como texto)
- [x] 1.3 Crear la cuenta de Turso e iniciar sesión con el CLI (`turso auth login`)

## 2. Referencia independiente (sin Java)

- [x] 2.1 Generar con `awk`, desde el ZIP del 2026-11-04, las filas de precio y de producto con los filtros del spec; confirmar que dan 238.998 y 75.601

## 3. Proyecto y esquema

- [x] 3.1 Crear el proyecto Maven en `cargador/` (Spring Boot sin web, Java 25, wrapper)
- [x] 3.2 Cambiar las dependencias: quitar JDBC, Flyway, el driver de Postgres y los artefactos de DSQL; sumar Jackson y usar la imagen de libsql-server en Testcontainers
- [x] 3.3 Escribir `schema.sql` con las cuatro tablas de `design.md` (`STRICT`, `CREATE TABLE IF NOT EXISTS`), aplicarlo al arrancar y borrar las migraciones de Flyway
- [x] 3.4 Configurar la conexión con `carga.turso.url` y `carga.turso.token` (vacío para libsql-server local) y borrar el perfil `dsql`

## 4. Lectura y cálculo

- [x] 4.1 Escribir el lector del ZIP: recorre los ZIP internos, tolera el vacío, verifica cabeceras y entrega las filas de `comercio.csv` y `productos.csv`
- [x] 4.2 Escribir el análisis de una línea de producto y los filtros del spec, con el conteo de descartes por motivo
- [x] 4.3 Escribir el resumen por comercio: mediana en centavos por bandera y EAN, con cantidad de sucursales
- [x] 4.4 Escribir el conteo de la combinación más frecuente de descripción y marca por producto
- [x] 4.5 Escribir los tests unitarios U1 a U7 y dejarlos en verde corrigiendo el código, no los tests

## 5. Escritura en la base

- [x] 5.1 Escribir el cliente de Turso sobre `/v2/pipeline`: ejecuta una sentencia con parámetros, devuelve las filas afectadas o el resultado y falla ante cualquier error del servidor o de red
- [x] 5.2 Adaptar la carga de una fecha a la secuencia de `design.md`: marca de incompleta, borrado de la fecha en una sentencia, banderas, precios en `INSERT` de varias filas, productos, verificación de que hay precios y marca de completa
- [x] 5.3 Dejar `carga.lote` en 1.000 por defecto
- [x] 5.4 Adaptar los tests P1 a P24 y la configuración de Testcontainers a libsql-server
- [x] 5.5 Correrlos y dejarlos en verde corrigiendo el código, no los tests

## 6. Puesta al día y recarga

- [x] 6.1 Escribir la descarga del ZIP de una fecha desde S3 a un archivo temporal
- [x] 6.2 Adaptar la puesta al día al cliente de Turso: listar fechas archivadas, restar las completas, cargar en orden, seguir ante una falla y terminar con error si hubo alguna
- [x] 6.3 Escribir la entrada por línea de comandos: sin argumentos pone al día; con una fecha, recarga esa fecha
- [x] 6.4 Adaptar los tests A1 a A7 a libsql-server y floci
- [x] 6.5 Correrlos y dejarlos en verde corrigiendo el código, no los tests

## 7. Tests en GitHub Actions

- [x] 7.1 Agregar a `.github/workflows/tests.yml` un job que corra `./mvnw verify` en `cargador/` con Java 25 y caché de Maven
- [x] 7.2 Confirmar que el job pasa en un push, con los tests contra libsql-server

## 8. Validación local con datos reales

- [x] 8.1 Correr el cargador contra un libsql-server local con el ZIP real del 2026-11-04
- [x] 8.2 Comparar con el cálculo independiente de la tarea 2.1: cantidad de precios, cantidad de productos y mediana por bandera del EAN 7790895000997. Mostrarle los números a Franco
- [x] 8.3 Correr la carga por segunda vez y confirmar que el resultado es idéntico

## 9. Infraestructura y medición en Turso

- [x] 9.1 Borrar el stack `precios-super-base`, que quedó en `ROLLBACK_COMPLETE` sin recursos
- [x] 9.2 Reemplazar `infra/base.yaml` por `infra/cargador.yaml` con solo el rol `precios-super-cargador` (listar el bucket y leer `sepa/*`), desplegarlo y anotar el ARN; borrar `infra/rol-base.sql`
- [x] 9.3 Crear la base `precios-super` en Turso, en `aws-us-east-1`, y un token sin vencimiento limitado a ella
- [x] 9.4 Cargar el 2026-11-04 en Turso desde local y medir el tiempo
- [x] 9.5 Leer de Turso las filas escritas y el almacenamiento
- [x] 9.6 Mostrarle los números a Franco. Si la carga escribe más de 1,5 millones de filas, frenar acá y esperar su decisión

## 10. Despliegue (solo con los grupos 4 a 9 en verde)

- [x] 10.1 Cargar en el repo la variable `TURSO_URL`, el secreto `TURSO_TOKEN` y la variable `CARGADOR_ROLE_ARN`
- [x] 10.2 Adaptar `.github/workflows/cargar.yml` a Turso: URL y token por variables de entorno, sin el perfil `dsql`
- [x] 10.3 Disparar el workflow a mano y confirmar que crea las tablas y que las fechas archivadas quedan completas
- [x] 10.4 Repetir contra Turso la comparación de la tarea 8.2 para el 2026-11-04
- [x] 10.5 Verificar a mano los dos escenarios de acceso: no hay claves de AWS guardadas, la única credencial guardada es el token como secreto del repo, y el rol del cargador no puede escribir ni borrar en el bucket
- [ ] 10.6 Confirmar que la primera corrida programada termina bien

## 11. Cierre

- [x] 11.1 Actualizar `README.md`: cómo funciona la carga, el esquema, cómo correr los tests y cómo crear la base y el token de Turso
- [x] 11.2 Actualizar `CLAUDE.md` con las decisiones tomadas (Turso y por qué no DSQL, cliente HTTP propio, grano por bandera con mediana, filtros) y los gotchas encontrados (ZIP64 en los ZIP internos, `login_session` del SDK de Java, driver JDBC de libSQL)
