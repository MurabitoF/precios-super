## Context

Motivación en `proposal.md`; comportamiento en `specs/carga-precios/spec.md`. Lo medido sobre el ZIP real del 2026-11-04:

- 17 ZIP internos, uno por comercio; uno pesa 0 bytes. Cada uno trae `comercio.csv`, `sucursales.csv` y `productos.csv`.
- Formato: UTF-8, separador `|`, sin comillas. Ningún campo contiene un `|`. El BOM aparece en algunos comercios y no en otros; el fin de línea es CRLF o LF según el comercio. Cada archivo termina con líneas en blanco (a veces con un espacio o un carácter NUL) y un pie `Ultima actualizacion: ...`, escrito distinto según el comercio. Ninguna de esas líneas contiene un `|`.
- `productos.csv` tiene 17 columnas, iguales en todos los comercios, y 14,3 millones de filas en total. El comercio más grande (DIA) tiene 4,2 millones. Un comercio rellena con espacios las cabeceras y las descripciones.
- Con los filtros del spec quedan 238.998 filas de precio (producto, bandera) y 75.601 productos.
- Todos los precios de lista son numéricos, con 0, 1 o 2 decimales y sin separador de miles (`.60`, `32900.00`, `04480.`). El máximo es 12.999.000.
- No hay ninguna combinación sucursal y producto repetida.

Turso (consultado el 2026-11-21): base libSQL, compatible con SQLite 3.45, accesible por HTTP en `/v2/pipeline` con un token en la cabecera `Authorization`. Soporta `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE` y tablas `STRICT`. Plan gratuito: 10 millones de filas escritas y 500 millones leídas por mes, 5 GB de almacenamiento. El servidor de Turso existe como imagen de contenedor (`ghcr.io/tursodatabase/libsql-server`), que sirve para probar en local con el mismo protocolo.

No hay un driver JDBC usable. El de DBeaver (`com.dbeaver.jdbc.driver.libsql` 1.0.9), probado el 2026-11-21 contra libsql-server, no implementa `executeUpdate()` ni lotes, que son los que usan `JdbcTemplate` y Flyway, y deja la JVM sin terminar al salir.

## Goals / Non-Goals

**Goals:**

- Que la base entera se pueda rehacer corriendo el cargador sobre los ZIP del archivo.
- Que una corrida fallida no requiera intervención: la siguiente la compensa, igual que en el archivador.
- Saber cuántas filas escribe una carga en Turso antes de programar el job.

**Non-Goals:**

- Índices o tablas pensados para la API. Se agregan en el paso 3, cuando se conozcan las consultas.
- Guardar solo los cambios de precio. Reduciría las filas escritas y el almacenamiento (el 86% de los precios no cambió en seis días), pero complica la carga; se evalúa si el límite de escrituras llega a pesar.
- Reintentos ante errores de red. Hay un solo escritor y una corrida por día: lo que falla se rehace en la siguiente.
- Detectar comercios con datos congelados. Hace falta más de una semana cargada para verlo.

## Decisions

### Referencia independiente y medición con el cargador

Antes de escribir el cargador se calculan con `awk`, desde el ZIP del 2026-11-04, las filas de precio y la cantidad de productos con los filtros del spec. Ese cálculo es la referencia contra la que se valida el cargador, en local y en Turso.

La medición en Turso se hace con el cargador ya escrito, cargando el 2026-11-04 desde la máquina de Franco antes de activar el job. Se registran las filas escritas y el almacenamiento (panel o CLI de Turso) y el tiempo. Criterio para seguir: una carga semanal escribe menos de 1,5 millones de filas, así cuatro o cinco cargas por mes y alguna recarga entran en los 10 millones. La estimación es de unas 315 mil filas por carga (precios, productos y banderas) y el doble en una recarga, que además borra. Si escribe más, se frena y se le muestran los números a Franco.

Alternativa: medir con `psql` antes de escribir el cargador, como se planeó para DSQL. Con Turso no cambia qué se construye, solo dónde se guarda, y el cargador ya existe.

### Un proyecto Maven en `cargador/`, sin servidor web

Aplicación Spring Boot de línea de comandos, con Java 25 (la versión instalada en la máquina de Franco y LTS vigente) y Maven con wrapper. Sin argumentos carga todo lo pendiente; con una fecha como argumento recarga esa fecha.

Dependencias: `spring-boot-starter`, Jackson, el cliente S3 del SDK de AWS y, para tests, Testcontainers.

Alternativa: sumar el cargador a una única aplicación junto con la API. La API del paso 3 puede terminar en Lambda con otro empaquetado; se decide entonces.

### Acceso a Turso: cliente HTTP propio

Una clase chica sobre `java.net.http` y Jackson que manda cada sentencia, con sus parámetros, a `/v2/pipeline` y devuelve las filas afectadas o el resultado. Cualquier error del servidor o de red es una excepción: corta la carga de la fecha, que queda incompleta.

Cada lote es una sola sentencia (`INSERT` de varias filas), atómica por sí misma en SQLite, así que no hace falta abrir transacciones por HTTP. La consistencia entre lotes la da la marca de fecha completa.

Alternativas: el driver JDBC de DBeaver (descartado por lo de Context), `libsql-java` (driver de un solo autor y sin uso conocido) y `tech.turso:turso` (motor embebido anterior a la 1.0, sin acceso a una base remota).

### Lectura del ZIP: a disco y con la biblioteca estándar

El ZIP se descarga a un archivo temporal y se recorre con `java.util.zip`. Cada ZIP interno se copia a otro temporal y se lee con `ZipFile`: los del portal traen tamaños ZIP64 en los encabezados locales y `ZipInputStream` falla al leerlos (encontrado con el ZIP real del 2026-11-04). Las líneas se separan con `split` por `|`: no hace falta una biblioteca de CSV porque el formato no usa comillas. Una línea sin `|` no es de datos y se ignora; una línea de datos con otra cantidad de columnas hace fallar la carga.

La cabecera de `productos.csv` y de `comercio.csv` se compara, sin el BOM ni los espacios de relleno, contra la esperada. Si difiere, la carga falla: es la defensa contra un cambio de formato del portal, que de otro modo cargaría basura en silencio.

Alternativa: leer el ZIP en flujo directo desde S3. Ahorra 320 MB de disco, que sobran en el runner, y deja la carga expuesta a un corte de red a mitad de lectura.

### Cálculo por comercio, en memoria

Las banderas pertenecen a un solo comercio, así que cada comercio se resume por separado: un mapa de (bandera, EAN) a la lista de precios, en centavos como enteros. Al terminar el comercio se calculan las medianas, se escriben y se libera el mapa. El peor caso son los 4,2 millones de precios de DIA, unos 35 MB.

La combinación más frecuente de descripción y marca se cuenta sobre todos los comercios, así que ese conteo vive durante toda la carga (75.601 productos) y el catálogo de productos se escribe al final.

Cada corrida informa por salida estándar cuántas filas descartó por cada motivo.

### Esquema

```
carga     (fecha PK, completa_en, filas)
bandera   (id_comercio, id_bandera, razon_social, nombre, fecha)      PK (id_comercio, id_bandera)
producto  (ean PK, descripcion, marca, fecha)
precio    (fecha, id_comercio, id_bandera, ean, centavos, sucursales) PK (fecha, id_comercio, id_bandera, ean)
```

- Tablas `STRICT`, para que SQLite rechace un tipo equivocado en vez de guardarlo.
- `precio.centavos` es `INTEGER`: SQLite no tiene decimales exactos. Las fechas son `TEXT` en formato `AAAA-MM-DD`, que ordena igual que la fecha; `completa_en` es `TEXT` con fecha y hora UTC en ISO 8601.
- `carga.completa_en` nulo significa fecha incompleta. Es lo que la API va a mirar para decidir qué fechas mostrar.
- `fecha` en `bandera` y `producto` es la fecha de datos de la que salió la fila. El upsert solo pisa cuando la fecha entrante es igual o posterior, y así el catálogo no depende del orden de carga.
- La clave de `precio` empieza por `fecha` porque así borrar o leer una semana entera es un recorrido por rango. El índice por producto para las series lo agrega el paso 3.
- Sin claves foráneas. El catálogo de productos se escribe después de los precios, y la consistencia entre tablas la da la marca de fecha completa.

Las tablas se crean al arrancar con `CREATE TABLE IF NOT EXISTS`, desde un `schema.sql` del cargador.

Alternativa: Flyway. No reconoce libSQL por HTTP ni funciona sin JDBC. Cuando el paso 3 cambie el esquema, se agrega una tabla de versión con migraciones numeradas.

### Secuencia de carga de una fecha

1. Descargar el ZIP a un temporal.
2. Marcar la fecha como incompleta en `carga` y borrar sus precios con una sola sentencia.
3. Por cada comercio: upsert de sus banderas, cálculo de medianas e inserción de precios en lotes.
4. Upsert del catálogo de productos en lotes.
5. Si no se guardó ningún precio, fallar. Si no, marcar la fecha como completa con la cantidad de filas.

Cada lote es un `INSERT` de hasta 1.000 filas (propiedad `carga.lote`). Con 6 parámetros por fila queda lejos del límite de 32.766 parámetros de SQLite, y cada pedido HTTP pesa unos 100 KB. Una semana son unos 240 lotes de precios y 76 de productos. Cualquier error corta la carga de esa fecha y la deja incompleta; la próxima corrida la rehace desde el paso 2.

Alternativa: upsert de precios sin borrar antes. No elimina las filas sobrantes cuando cambian las reglas de filtrado, que es justo el caso en que se recarga.

### Puesta al día

El cargador lista las claves `sepa/AAAA-MM-DD.zip` del bucket, les resta las fechas completas de `carga` y carga el resto en orden. Si una fecha falla, sigue con las demás y termina con código de error.

Corre en un workflow propio, `cargar.yml`: todos los días a las 20:00 UTC (una hora después del archivador) y por disparo manual con una fecha opcional. El workflow compila el jar y lo ejecuta. Usa `concurrency` para que no haya dos corridas a la vez.

Alternativa: un segundo job en `archivar.yml`. Mezcla las fallas de dos tareas distintas en un mismo aviso.

### Infraestructura y credenciales

`infra/cargador.yaml`, un stack aparte del archivo, con el rol `precios-super-cargador`, que el workflow de este repo en `main` asume por OIDC (mismo proveedor y mismo formato inmutable del `sub` que el rol del archivador). Permisos: listar el bucket y leer `sepa/*`. No puede escribir en el bucket.

La base se crea una vez con el CLI de Turso (`turso db create`, en `aws-us-east-1`), junto con un token sin vencimiento limitado a esa base (`turso db tokens create`). El token se carga como secreto `TURSO_TOKEN` del repositorio y la URL como variable `TURSO_URL`; el workflow los pasa al cargador como variables de entorno. Si el token se filtra, se invalida con `turso db tokens invalidate` y se genera otro.

Alternativas: guardar el token en SSM Parameter Store y leerlo con el rol de OIDC (deja a GitHub sin ninguna credencial, a cambio de un permiso y un paso más; se reconsidera si la API del paso 3 corre en Lambda); un token con vencimiento (obliga a rotarlo a mano cada tanto, en un proyecto que tiene que andar solo); reutilizar el rol del archivador (le daría al cargador permiso de escritura sobre el archivo).

### Pruebas: tres niveles, y Turso recién al final

**Regla de autorización.** Las listas de abajo son los tests que se proponen crear. Ninguno se escribe hasta que Franco apruebe esta lista. Cualquier test fuera de ella, o cualquier cambio a uno existente o a sus datos de prueba, necesita su ok. Si un test falla se corrige el código; si el que parece estar mal es el test, se le avisa y se espera.

Los ZIP de prueba no son archivos guardados: cada test arma el suyo en memoria a partir de texto CSV escrito en el propio test, con una función auxiliar. Así se lee en el test qué entra y qué se espera.

**Unitarios** (sin Docker):

| # | Caso | Resultado esperado |
|---|---|---|
| U1 | Línea de producto válida | devuelve comercio, bandera, EAN, indicador, descripción, marca y precio |
| U2 | Precios `.60`, `1500`, `32900.5`, `32900.00` | 60, 150000, 3290050 y 3290000 centavos |
| U3 | Precio vacío, `abc`, `1,5` | no es un precio válido |
| U4 | Mediana de 100,00, 300,00 y 120,00 | 120,00 |
| U5 | Mediana de 100,00 y 100,01 | 100,01 (redondeo del medio centavo hacia arriba) |
| U6 | Cabecera con BOM | se reconoce igual a la esperada |
| U7 | Combinación más frecuente, con y sin empate | la de más filas; ante empate, la primera en orden alfabético |

**Integración contra libsql-server** (Testcontainers), uno por escenario del spec:

| # | Escenario del spec |
|---|---|
| P1 | Cantidad impar de sucursales |
| P2 | Cantidad par de sucursales |
| P3 | Un precio por bandera |
| P4 | Precio de promoción ignorado |
| P5 | Código interno del comercio |
| P6 | Código con prefijo 2 |
| P7 | Código que no tiene 13 dígitos |
| P8 | Precio en cero o negativo |
| P9 | Precio que no es un número |
| P10 | Pie y líneas en blanco |
| P11 | Columnas distintas a las esperadas |
| P12 | ZIP dañado |
| P13 | Carga sin precios |
| P14 | Comercio con ZIP vacío |
| P15 | Producto nuevo |
| P16 | Varias descripciones para un mismo producto |
| P17 | Empate entre descripciones |
| P18 | Fecha vieja cargada después de una nueva |
| P19 | Bandera |
| P20 | Misma fecha cargada dos veces |
| P21 | Filas de la carga anterior que ya no corresponden |
| P22 | Carga exitosa |
| P23 | Carga interrumpida (primer comercio válido, segundo con cabecera inválida) |
| P24 | Recarga interrumpida |

Corren con `carga.lote` en 2, para que toda carga pase por varios lotes.

**Integración contra libsql-server y floci** (el archivo simulado en S3), uno por escenario:

| # | Escenario del spec |
|---|---|
| A1 | Fecha archivada y sin cargar |
| A2 | Varias fechas pendientes |
| A3 | Nada pendiente |
| A4 | Fecha incompleta de una corrida anterior |
| A5 | Una fecha falla y otra no |
| A6 | Recarga de una fecha completa |
| A7 | Fecha que no está en el archivo |

Los dos escenarios de "Acceso mínimo y credenciales acotadas" no tienen test automático: dependen de IAM, que floci no aplica, y del token de Turso. Se verifican a mano contra las cuentas reales, como el acceso anónimo en el paso 1.

libsql-server es el mismo servidor que usa Turso, con la imagen fijada en una versión. Lo único que los tests no cubren es el token y los límites del plan: eso lo cubren la medición y la validación final contra Turso.

**Validación con datos reales.** Antes de desplegar, se corre el cargador en local contra libsql-server con el ZIP real del 2026-11-04 y se compara con el cálculo independiente en shell: 238.998 filas de precio, 75.601 productos y la mediana por bandera de un producto conocido (Coca-Cola 2,25 L, EAN 7790895000997). Los números se le muestran a Franco. Después se repite la misma comparación contra Turso.

## Risks / Trade-offs

- [Una carga escribe más filas de lo esperado y no entra en el plan gratuito] → Se mide antes de activar el job. Si supera el criterio, se frena; la salida es guardar solo los cambios de precio.
- [El cliente HTTP propio depende de un protocolo que Turso puede cambiar] → La API `/v2/pipeline` es la estable que usan los clientes oficiales; los tests corren contra una versión fijada del servidor, y el job diario avisa por mail si algo se rompe.
- [El token de la base se filtra] → Solo da acceso a esta base, que se rehace desde el archivo. Se invalida y se reemplaza con el CLI de Turso.
- [La base depende de un proveedor fuera de AWS] → El archivo sigue siendo la única fuente de verdad: si Turso deja de servir, la base se rehace en otro motor corriendo el cargador sobre los ZIP.
- [El portal cambia columnas o formato] → La carga falla por la verificación de cabecera, la fecha queda incompleta y el ZIP sigue archivado: se corrige el cargador y la próxima corrida la carga.
- [Un ZIP archivado queda imposible de cargar] → Falla todos los días hasta que se resuelva, pero no bloquea a las demás fechas. Aceptado: es una señal que conviene no silenciar.
- [El almacenamiento crece unos 40 MB por semana] → Con 5 GB gratis alcanza para años; la medición da el número real.

## Migration Plan

1. Cargador y tests en verde en local y en GitHub Actions, contra libsql-server.
2. Validación local con el ZIP real.
3. Rol del cargador en AWS, base y token en Turso, y medición cargando el 2026-11-04 desde local.
4. Variables y secreto del repo, y primera carga por disparo manual del workflow.
5. Activación del cron.

Vuelta atrás: desactivar `cargar.yml`. La base se puede vaciar y rehacer desde el archivo, que este cambio no modifica.
