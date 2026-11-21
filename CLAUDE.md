# precios-super

Seguimiento de precios de supermercados argentinos: recolección diaria, series históricas, API y dashboard de inflación por producto.

## Para qué existe

Es el proyecto de portfolio público de Franco. Tiene que quedar **terminado, desplegado y fácil de probar**: eso pesa más que la cantidad de funciones.

Lo que debe demostrar: pipelines de datos, tareas programadas, diseño de API y decisiones explicadas.

## Decisiones tomadas

- **Stack:** Java con Spring Boot para el pipeline y la API (paso 2 en adelante). El archivador es un script de shell a propósito: no tiene lógica de negocio. No Python: Franco no desarrolla en Python.
- **Despliegue:** AWS, región `us-east-1`, infraestructura en CloudFormation (`infra/`). Tareas programadas en GitHub Actions, que entra a AWS por OIDC, sin claves guardadas.
- **Base:** Turso (libSQL), plan gratuito, región `aws-us-east-1` de Turso. Se descartó Aurora DSQL porque la cuenta de AWS no permite crear clusters ("Contact AWS Support") y el caso no avanzó. El token de Turso, limitado a la base, es la única credencial guardada: secreto `TURSO_TOKEN` del repo.
- **Acceso a Turso:** cliente HTTP propio sobre `/v2/pipeline` (`cargador/.../Turso.java`), sin JDBC ni Flyway. Las tablas se crean con `schema.sql` al arrancar.
- **Precio semanal:** uno por producto y bandera, la mediana del precio de lista entre sus sucursales, en centavos enteros. Entran solo filas con `productos_ean = 1`, EAN de 13 dígitos que no empiece con 2 y precio mayor a 0.
- **Medición semanal:** se archiva solo el ZIP del miércoles.
- Para el LLM, si hiciera falta, Claude Code headless antes que API paga.
- Alcance chico primero: una versión que funcione de punta a punta antes de sumar nada.

## Tests

- **No crear ni modificar tests sin autorización explícita de Franco.** Antes de escribir un test nuevo o tocar uno existente (incluidos sus datos de prueba), mostrarle qué se quiere crear o cambiar y esperar su ok. Si un test falla, se corrige el código; si el que parece estar mal es el test, se le avisa y se espera.
- Toda funcionalidad lleva tests: cada escenario de los specs tiene uno.
- Antes de desplegar a AWS se valida en local contra floci (emulador de AWS). floci no aplica los permisos como AWS: lo que dependa de IAM o del acceso público se verifica contra la cuenta real.

## Fuente de datos

Base SEPA de Precios Claros (datos oficiales, evita hacer scraping):

- Listado: `https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios` (API CKAN).
- Un recurso ZIP por día de la semana, que se pisa cada semana: **el portal solo conserva los últimos 7 días**. El histórico no se recupera después, por eso el archivo es la única fuente de verdad: todo lo posterior se reconstruye desde los ZIP.
- La fecha de cada archivo solo aparece al final de la descripción del recurso, por ejemplo `Precios SEPA Minoristas miercoles, 2026-11-11`.
- Verificado por última vez el 2026-11-11.

## Gotchas de la infraestructura

- El repo usa el formato **inmutable** del `sub` de OIDC de GitHub (`repo:MurabitoF@66337385/precios-super@1454332727:ref:refs/heads/main`). La condición de confianza del rol tiene que usar ese formato; con el clásico `repo:dueño/repo:...` el rol no se puede asumir.
- `aws cloudformation deploy` sobre un stack existente reutiliza los valores anteriores de los parámetros: para cambiar uno, pasarlo con `--parameter-overrides`.

## Gotchas del cargador

- Los ZIP internos de cada comercio traen tamaños ZIP64 en los encabezados locales: `ZipInputStream` falla. Se copian a un temporal y se leen con `ZipFile`.
- El formato varía por comercio: BOM opcional, fin de línea CRLF o LF, líneas en blanco con un espacio o un NUL, cabeceras y descripciones rellenas con espacios, precios como `04480.`. Una línea sin `|` no es de datos.
- El portal a veces omite comercios enteros: el ZIP del 2026-11-11 no trae a Cencosud (Jumbo, Vea, Disco) ni a Axion. Una semana sin datos de una bandera no es un cambio de precio.
- El SDK de Java no lee la sesión de `aws login` (`login_session`). En local: `eval "$(aws configure export-credentials --format env --region us-east-1)"`.
- No hay driver JDBC usable para libSQL: el de DBeaver no implementa `executeUpdate()` ni lotes y deja la JVM colgada.
- `HttpClient` de Java tiene que ir en HTTP/1.1: sobre `http://` el intento de pasar a HTTP/2 deja colgado a libsql-server.
- libsql-server rechaza respuestas grandes ("Response is too large"): para leer muchas filas hay que paginar (de a 20.000 anda).
- Turso cuenta como escritura también el índice de la clave primaria: cada fila de `precio` vale casi dos filas escritas (una carga semanal, unas 560 mil).

## Pendiente de decidir

- Qué productos entran en la canasta inicial.
