## Context

Motivación en `proposal.md`; comportamiento en `specs/api-precios/spec.md`. Estado de partida, al 2026-11-21:

- Turso (`precios-super`, `aws-us-east-1`): tablas `carga`, `bandera`, `producto` y `precio` (`schema.sql` del cargador). `precio` tiene clave `(fecha, id_comercio, id_bandera, ean)` y ningún índice por producto: buscar un EAN recorre toda la tabla.
- Volumen: unas 240 mil filas de precio por semana, 77 mil productos y 28 banderas. Dos fechas completas y una tercera en camino.
- Plan gratuito de Turso: 500 millones de filas leídas por mes. libsql-server rechaza respuestas grandes: más de unas 20 mil filas hay que pedirlas por páginas.
- El cliente de Turso del cargador (`Turso.java`) crea las tablas al arrancar; con un token de solo lectura eso falla.
- AWS Lambda tiene runtime `java25` y SnapStart para Java. Existe el adaptador oficial `aws-serverless-java-container-springboot4` (3.0.5) para correr Spring Boot 4 detrás de Lambda.
- La cuenta de AWS tiene un límite de 10 ejecuciones concurrentes de Lambda, sin margen para reservar concurrencia.
- El SAM CLI no está instalado en la máquina de Franco.

## Goals / Non-Goals

**Goals:**

- Que cualquiera pueda probar el proyecto con una URL, sin cuenta ni credenciales.
- Respuestas rápidas: menos de un segundo en frío gracias a SnapStart, y unas decenas de milisegundos en caliente.
- Que ningún pedido lea más de unos miles de filas de Turso, para que el plan gratuito no dependa de cuánto se use la API.

**Non-Goals:**

- Despliegue automático desde GitHub Actions: se despliega a mano con el SAM CLI, como las demás plantillas. Se agrega cuando cambie seguido.
- Caché en CDN (CloudFront): los datos cambian una vez por semana; alcanza con la caché del navegador.
- Límite de pedidos por cliente: el límite de concurrencia de la cuenta ya acota el costo.
- Canastas guardadas, ponderaciones por gasto, autenticación.

## Decisions

### Spring Boot en Lambda con SnapStart y Function URL

Proyecto Maven nuevo en `api/`: Spring Boot 4 con Spring MVC, detrás de Lambda con `aws-serverless-java-container-springboot4`. Los controladores son los de una aplicación web común, así que se prueban con MockMvc sin nada de Lambda.

SnapStart guarda una foto de la función ya inicializada al publicar una versión y la restaura en cada arranque en frío, que pasa de varios segundos a menos de uno. Se publica con un alias (`vivo`) que apunta a la última versión. La URL pública es una Function URL sobre ese alias, sin autenticación.

Alternativas: API Gateway HTTP API (agrega límites por ruta y dominio propio, que hoy no hacen falta); App Runner o Fargate (sin arranques en frío, pero con un costo fijo mensual); Spring Cloud Function (otro modelo de programación, menos parecido a una API Spring común).

### Cliente de Turso de solo lectura, propio de la API

`api/` tiene su propia clase `Turso` con un único método `consultar`, sobre la misma API HTTP (`/v2/pipeline`) que usa el cargador. No crea tablas ni escribe, y el token que recibe no lo permitiría.

Después de restaurar la foto de SnapStart, el cliente HTTP se crea de nuevo (gancho `afterRestore` de CRaC): las conexiones abiertas al tomar la foto ya no sirven.

Alternativa: un módulo Maven compartido con el cargador. Obliga a reestructurar el build del cargador, su CI y `cargar.yml` por unas 60 líneas; se hace si aparece un tercer consumidor.

### Catálogo en memoria para la búsqueda

Buscar con `LIKE` en Turso recorre los 77 mil productos en cada pedido: unas 6.500 búsquedas agotarían las lecturas del mes. En cambio, la API carga el catálogo entero en memoria al iniciar (unos 10 MB, de a 10 mil filas por página) y busca ahí, sin leer Turso. El texto de búsqueda se normaliza una sola vez: mayúsculas y sin acentos.

El catálogo y la lista de fechas completas se renuevan cuando cambia el último `completa_en` de `carga`, que cambia con una fecha nueva y también con la recarga de una fecha existente. La API lo consulta como mucho una vez cada 10 minutos (una lectura de pocas filas), así que una carga nueva se ve en la API en ese plazo.

Con SnapStart, la carga del catálogo queda dentro de la foto y no se repite en cada arranque en frío.

Alternativa: tabla FTS5 de libSQL. Cada carga semanal reescribe el catálogo (el upsert actualiza la fecha de cada producto), así que el índice de texto sumaría unas 75 mil escrituras por semana, y la búsqueda seguiría leyendo de Turso en cada pedido.

### Índice por producto en `precio`

`CREATE INDEX IF NOT EXISTS precio_ean ON precio (ean, fecha)`, en el `schema.sql` del cargador, que es quien crea el esquema. Con él, la ficha y la serie de un producto leen unas pocas filas por fecha (una por bandera) en lugar de la tabla entera. Suma una escritura por precio en cada carga: de unas 560 mil a unas 800 mil filas escritas por semana.

### Consultas acotadas

- Fechas: `carga` con `completa_en` no nulo.
- Ficha y serie: una sola consulta por EAN sobre `precio` (por el índice), con un join a `carga` completa y a `bandera`. La ficha es el último punto de cada bandera en la serie, así que comparten la consulta.
- Índice de canasta: una consulta con los EAN de la canasta y las dos fechas, como mucho 50 × 2 × 28 filas.
- Ninguna consulta de la API devuelve más de unos miles de filas. La única que se pagina es la carga del catálogo.

### Contrato de la API

JSON, con precios en centavos enteros y fechas `AAAA-MM-DD`:

```
GET /fechas                         ["2026-11-04", "2026-11-11"]
GET /productos?q=coca&pagina=1      {pagina, hayMas, productos: [{ean, descripcion, marca}]}
GET /productos/{ean}                {ean, descripcion, marca, precios: [{comercio, bandera, nombre, fecha, centavos, sucursales}]}
GET /productos/{ean}/serie          {ean, descripcion, banderas: [{comercio, bandera, nombre, puntos: [{fecha, centavos, sucursales}]}]}
GET /canasta                        {productos: [{ean, descripcion}]}
GET /canasta/indice?desde&hasta&ean {desde, hasta, variacion, productos: [{ean, descripcion, variacion, banderas}], excluidos: [{ean, descripcion}]}
```

Errores con `application/problem+json` (RFC 9457), que Spring arma solo (`ProblemDetail`). Las respuestas exitosas llevan `Cache-Control: public, max-age=3600`.

CORS se configura en Spring, que permite `GET` desde cualquier origen, y no en la Function URL: así se prueba con los tests y no se duplican los encabezados.

### Índice de la canasta

Dos niveles de media geométrica, ambos con igual peso:

1. Por producto: la media geométrica de `precio_hasta / precio_desde` en cada bandera que publicó el producto en las dos fechas.
2. Por canasta: la media geométrica de las variaciones de los productos que tienen al menos una bandera comparable.

Las variaciones se informan como porcentaje (`(relativo - 1) × 100`), redondeadas a dos decimales al final, sin redondeos intermedios. Igual peso por producto evita que uno que venden muchas banderas pese más que otro.

Alternativas: costo total de la canasta (los productos caros dominan y una bandera que falta mueve el total); ponderaciones por gasto (hay que definirlas y justificarlas).

### Canasta por defecto

`api/src/main/resources/canasta.json`, con EAN y un nombre legible por producto (el catálogo usa descripciones abreviadas como `GASEO COLA`). Se elige así: productos que publican al menos 15 banderas en las fechas cargadas, repartidos entre almacén, lácteos, bebidas, limpieza e higiene, y sin dos variantes del mismo producto. La lista propuesta se le muestra a Franco antes de fijarla.

### Token de solo lectura en SSM

`turso db tokens create precios-super --read-only --expiration none`, guardado como `SecureString` en `/precios-super/turso-lectura`. El rol de la Lambda solo puede leer ese parámetro. La función lo lee al iniciar con el SDK de AWS, así que queda dentro de la foto de SnapStart, que AWS guarda cifrada. Rotar el token es: crear otro, actualizar el parámetro y volver a desplegar (publica una versión nueva con una foto nueva).

Alternativas: variable de entorno de la Lambda (visible en la consola para quien tenga acceso a la cuenta); leer el token en cada restauración (más lento en frío, a cambio de rotar sin desplegar).

### Infraestructura y despliegue

`infra/api.yaml` con la transformación SAM, que resume en pocas líneas la función, SnapStart, el alias publicado automáticamente y la Function URL:

- Función `precios-super-api`: runtime `java25`, arquitectura `arm64`, 1024 MB, 15 s de límite, SnapStart sobre versiones publicadas, alias `vivo`.
- Function URL sin autenticación, sobre el alias.
- Rol con permiso de leer solo `/precios-super/turso-lectura`.
- Variables de entorno: URL de Turso y nombre del parámetro.

Se despliega a mano con `sam build && sam deploy` (el SAM CLI crea y administra su propio bucket para el código). El parámetro de SSM se crea a mano una vez, fuera de la plantilla, para que el token nunca pase por CloudFormation.

Alternativas: CloudFormation sin SAM (hace falta un bucket de artefactos propio y declarar la versión, el alias y la URL a mano); despliegue desde GitHub Actions (un rol de despliegue con permisos sobre IAM, CloudFormation y Lambda, que todavía no se justifica).

### Pruebas

**Regla de autorización.** Las listas de abajo son los tests que se proponen crear. Ninguno se escribe hasta que Franco apruebe esta lista. Cualquier test fuera de ella, o cualquier cambio a uno existente o a sus datos de prueba, necesita su ok.

**Unitarios** (sin Docker):

| # | Caso | Resultado esperado |
|---|---|---|
| U1 | Índice del ejemplo del spec (A: 100→110 y 200→200; B: 50→60) | A 4,88%, B 20,00%, canasta 12,19% |
| U2 | Normalización de búsqueda: `Cañuelas`, `CAÑUELAS`, `canuelas` | las tres coinciden |
| U3 | Validación de EAN: 13 dígitos, con letra, de 12 y de 14 | solo el primero es válido |

**Integración** (MockMvc contra libsql-server en Testcontainers, con datos sembrados en cada test), uno por escenario del spec:

| # | Escenario del spec |
|---|---|
| I1 | Fecha a medio cargar |
| I2 | La fecha se completa |
| I3 | Varias fechas |
| I4 | Precio con centavos |
| I5 | Búsqueda por texto |
| I6 | Búsqueda por EAN |
| I7 | Segunda página |
| I8 | Sin resultados |
| I9 | Texto demasiado corto |
| I10 | Producto en varias banderas |
| I11 | Bandera que no publicó la última semana |
| I12 | EAN inexistente |
| I13 | EAN mal formado |
| I14 | Serie de dos semanas |
| I15 | Semana sin publicar |
| I16 | Canasta por defecto |
| I17 | Demasiados productos |
| I18 | Cálculo con dos productos |
| I19 | Bandera ausente en una de las fechas |
| I20 | Producto sin banderas comparables |
| I21 | Ningún producto comparable |
| I22 | Fechas no disponibles o invertidas |
| I23 | Canasta propia |
| I24 | Consulta desde un navegador |

"La API no puede escribir en la base" no tiene test automático: depende del token real de Turso. Se verifica a mano contra Turso, igual que el acceso de los roles de IAM.

Los tests siembran la base con el `schema.sql` del cargador, copiado como recurso de prueba, para probar contra el mismo esquema.

**Validación con datos reales.** Después de desplegar: tiempo de respuesta en frío y en caliente, filas leídas por tipo de pedido (panel de Turso) y la serie de la Coca-Cola de 2,25 L comparada contra la base.

## Risks / Trade-offs

- [SnapStart no funciona bien con el adaptador de Spring Boot 4 o con el runtime `java25`] → Es lo primero que se prueba al desplegar. Si falla, se despliega sin SnapStart y se mide el arranque en frío; si no es aceptable, se evalúa una alternativa con Franco.
- [La URL pública recibe un abuso] → El límite de 10 ejecuciones concurrentes de la cuenta acota el gasto en Lambda, y el plan de Turso no tiene excedentes habilitados: en el peor caso la API deja de responder hasta fin de mes, sin costo.
- [El catálogo en memoria queda desactualizado] → Se renueva cuando cambia la última fecha completa, con una consulta cada 10 minutos como mucho.
- [La copia del `schema.sql` en los tests de la API diverge del cargador] → Un test que lo compare sería un test nuevo; por ahora se copia y se anota en `CLAUDE.md` que van juntos.
- [La búsqueda en memoria no escala a millones de productos] → Son 77 mil y crecen poco; si cambia, se pasa a FTS5.
- [La descripción abreviada del catálogo (`GASEO COLA`) confunde en la canasta] → La canasta por defecto lleva un nombre legible propio.

## Migration Plan

1. Índice por producto en el `schema.sql` del cargador; la próxima carga lo crea.
2. API y tests en verde en local y en GitHub Actions.
3. Token de solo lectura en SSM, SAM CLI instalado y primer `sam deploy`.
4. Validación con datos reales y prueba del token contra escrituras.
5. README con la URL y ejemplos de uso.

Vuelta atrás: borrar el stack `precios-super-api`. El índice se puede quitar con `DROP INDEX` sin tocar los datos.
