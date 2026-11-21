## Why

La base ya tiene un precio semanal por producto y bandera, pero solo se puede consultar con credenciales de Turso. Para el dashboard del paso 4, y para que cualquiera pueda probar el proyecto, hace falta una API pública de solo lectura que responda las preguntas del proyecto: cuánto sale un producto, cómo cambió su precio y cuánto subió una canasta.

## What Changes

- API REST pública, de solo lectura, en Java con Spring Boot, desplegada en AWS Lambda con SnapStart y una Function URL.
- Fechas disponibles: solo las que la carga marcó como completas. Una fecha a medio cargar nunca aparece en ninguna respuesta.
- Búsqueda de productos por texto (descripción o marca) o por EAN, paginada.
- Ficha de un producto: descripción, marca y precio de la última fecha disponible en cada bandera que lo publica.
- Serie semanal de un producto: su precio por bandera en cada fecha disponible.
- Canasta por defecto de unos 25 productos de consumo masivo, elegidos a mano y versionados en el repo, y canastas propias armadas con una lista de EAN.
- Índice de una canasta entre dos fechas: media geométrica de las variaciones de precio (método de Jevons) sobre una muestra pareada, que solo compara un producto en una bandera si esa bandera lo publicó las dos fechas. Una bandera que falta una semana no se confunde con un cambio de precio.
- Índice por producto en la tabla de precios, que el paso 2 dejó para cuando se conocieran las consultas.
- La API lee Turso con un token de solo lectura guardado en SSM Parameter Store, que solo el rol de la Lambda puede leer.

Queda afuera: dashboard (paso 4), autenticación de usuarios, canastas guardadas por usuario, ponderaciones por gasto de los hogares y despliegue automático desde GitHub Actions.

## Capabilities

### New Capabilities

- `api-precios`: consulta pública de fechas, productos, series de precios, canastas e índice de una canasta entre dos fechas.

### Modified Capabilities

Ninguna. La carga gana un índice, pero su comportamiento no cambia.

## Impact

- Repositorio: proyecto Maven nuevo en `api/`, con su propio cliente de Turso de solo lectura; `schema.sql` del cargador suma el índice por producto y un job nuevo en el workflow de tests.
- AWS: una función Lambda (runtime `java25`, SnapStart) con su alias y su Function URL, su rol y un parámetro SecureString en SSM, definidos en una plantilla nueva (`infra/api.yaml`, CloudFormation con la transformación SAM). Se despliega a mano con el SAM CLI.
- Turso: un token nuevo de solo lectura. El índice nuevo suma escrituras a cada carga (de unas 560 mil a unas 800 mil filas por semana, dentro de los 10 millones mensuales).
- Costo: dentro de la capa gratuita de Lambda (un millón de pedidos por mes). La cuenta tiene un límite de 10 ejecuciones concurrentes, que acota el gasto ante un abuso de la URL pública.
- Herramientas: SAM CLI en la máquina de Franco para desplegar.
