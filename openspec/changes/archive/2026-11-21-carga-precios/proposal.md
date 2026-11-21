## Why

El archivo ya guarda un ZIP de SEPA por semana, pero un ZIP no se puede consultar: cada uno trae 14,3 millones de filas (una por sucursal y producto) repartidas en un CSV por comercio. Para calcular variaciones de precio y servir una API hace falta una base con una fila por producto, bandera y semana, que se pueda rehacer entera desde los ZIP.

## What Changes

- Cargador en Java con Spring Boot que lee un ZIP archivado y deja en la base el precio semanal de cada producto por bandera: la mediana del precio de lista entre las sucursales de esa bandera. Con los datos del 2026-11-04 son 238.998 filas por semana en lugar de 14,3 millones.
- Filtros de calidad sobre lo que se carga: solo productos con EAN real (`productos_ean = 1`), con precio mayor a 0 y cuyo EAN no empieza con 2, que es el prefijo de códigos internos de cada tienda y no se puede comparar entre comercios.
- Catálogo de productos y de banderas armado desde los mismos ZIP. Cuando un EAN trae varias descripciones, queda la más frecuente de la semana.
- La carga de una fecha se puede repetir y da el mismo resultado. Una fecha a medio cargar no es visible para quien consulta: recién cuenta cuando queda marcada como completa.
- Job diario en GitHub Actions que carga toda fecha archivada que todavía no esté completa en la base. La carga inicial, la recuperación tras una falla y la reconstrucción completa usan ese mismo camino.
- Base en Turso (libSQL, compatible con SQLite), en la región `aws-us-east-1`, creada con el CLI de Turso. El job lee el archivo con el rol de AWS que asume por OIDC, sin claves de AWS guardadas, y escribe en la base con un token limitado a esa base, guardado como secreto de GitHub Actions.
- Medición previa con datos reales: antes de programar el job se carga la semana del 2026-11-04 en Turso y se registran las filas escritas, el almacenamiento y el tiempo. Si no entra en la capa gratuita con margen razonable, se replantea antes de seguir.
- Tests de toda la funcionalidad, uno por escenario del spec, contra un servidor libSQL local (el mismo que usa Turso). La lista de tests se define en el diseño y requiere aprobación de Franco antes de escribirse.

Queda afuera: precios de promoción, corte por sucursal o provincia, elección de la canasta, cálculo de variaciones, API y dashboard (pasos 3 y 4).

## Capabilities

### New Capabilities

- `carga-precios`: carga de los ZIP archivados a la base, con qué filas entran, cómo se resume el precio por bandera, qué se guarda de productos y banderas, y qué pasa con cargas repetidas, incompletas o con archivos inválidos.

### Modified Capabilities

Ninguna. El archivo no cambia: el cargador solo lee los ZIP.

## Impact

- Repositorio: primer código Java del proyecto (aplicación Spring Boot, build y tests), una plantilla de CloudFormation nueva con el rol del cargador y un workflow nuevo de carga diaria.
- AWS: un rol de solo lectura sobre el archivo para el cargador. La base no vive en AWS.
- Turso: una base en el plan gratuito y un token con acceso solo a ella, cargado como secreto del repositorio.
- Dependencias nuevas: Jackson para la API HTTP de Turso, el cliente S3 del SDK de AWS y Testcontainers con la imagen de libsql-server para los tests. No hay driver JDBC: Turso se usa por su API HTTP.
- Límites de Turso que condicionan el diseño (plan gratuito): 10 millones de filas escritas y 500 millones leídas por mes, 5 GB de almacenamiento. Tipos de SQLite: el precio se guarda en centavos enteros y las fechas como texto.
- Verificación: libsql-server es el mismo servidor que usa Turso, así que los tests cubren el protocolo y el SQL. Lo propio de la cuenta (token y límites del plan) se verifica contra Turso, igual que el acceso público del bucket en el paso 1.
- Costo: se espera dentro de la capa gratuita de Turso. Es una estimación; la medición previa la confirma o la corrige.
