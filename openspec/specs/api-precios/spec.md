# api-precios Specification

## Purpose

Ofrecer una consulta pública y de solo lectura de los precios semanales cargados: fechas disponibles, productos, series de precios por bandera, canastas y el índice de una canasta entre dos fechas.

## Requirements

### Requirement: Solo fechas completas
La API SHALL responder únicamente con datos de fechas que la carga marcó como completas. Una fecha incompleta MUST NOT aparecer en ninguna respuesta, ni como fecha disponible ni dentro de una ficha, una serie o un índice.

#### Scenario: Fecha a medio cargar
- **WHEN** la base tiene la fecha 2026-11-18 incompleta y precios ya guardados para esa fecha
- **THEN** ninguna respuesta incluye la fecha 2026-11-18 ni sus precios

#### Scenario: La fecha se completa
- **WHEN** la carga marca la fecha 2026-11-18 como completa
- **THEN** la fecha y sus precios aparecen en las respuestas siguientes

### Requirement: Fechas disponibles
La API SHALL listar las fechas disponibles en `GET /fechas`, ordenadas de la más antigua a la más reciente.

#### Scenario: Varias fechas
- **WHEN** las fechas completas son 2026-11-04 y 2026-11-11
- **THEN** `GET /fechas` responde `["2026-11-04", "2026-11-11"]`

### Requirement: Precios en centavos
La API SHALL expresar todo precio como un número entero de centavos de peso, para que ningún cliente tenga que lidiar con decimales binarios.

#### Scenario: Precio con centavos
- **WHEN** un producto tiene un precio de 1.234,50 pesos
- **THEN** la respuesta lo informa como `123450`

### Requirement: Búsqueda de productos
La API SHALL buscar productos en `GET /productos?q=<texto>` por descripción o marca, sin distinguir mayúsculas, minúsculas ni acentos, o por EAN exacto cuando el texto son 13 dígitos. Los resultados SHALL venir de a 20, con un parámetro `pagina` que empieza en 1. Un texto de menos de 3 caracteres MUST rechazarse.

#### Scenario: Búsqueda por texto
- **WHEN** se pide `GET /productos?q=coca` y hay productos cuya descripción o marca contiene `COCA`
- **THEN** la respuesta los lista con EAN, descripción y marca

#### Scenario: Búsqueda por EAN
- **WHEN** se pide `GET /productos?q=7790895000997`
- **THEN** la respuesta trae solo ese producto

#### Scenario: Segunda página
- **WHEN** una búsqueda tiene 25 resultados y se pide `pagina=2`
- **THEN** la respuesta trae los últimos 5

#### Scenario: Sin resultados
- **WHEN** ningún producto coincide con el texto
- **THEN** la respuesta es una lista vacía, sin error

#### Scenario: Texto demasiado corto
- **WHEN** se pide `GET /productos?q=co`
- **THEN** la respuesta es un error 400

### Requirement: Ficha de producto
La API SHALL responder en `GET /productos/{ean}` la descripción y la marca del producto y, por cada bandera que lo publicó, su precio en la fecha disponible más reciente en que esa bandera lo publicó, junto con esa fecha, la cantidad de sucursales y el nombre de la bandera.

#### Scenario: Producto en varias banderas
- **WHEN** un producto tiene precio en dos banderas en la última fecha disponible
- **THEN** la ficha trae las dos banderas, cada una con su precio, su fecha y sus sucursales

#### Scenario: Bandera que no publicó la última semana
- **WHEN** una bandera publicó el producto el 2026-11-04 pero no el 2026-11-11, la última fecha disponible
- **THEN** la ficha trae el precio de esa bandera con la fecha 2026-11-04

#### Scenario: EAN inexistente
- **WHEN** se pide la ficha de un EAN de 13 dígitos que no está en el catálogo
- **THEN** la respuesta es un error 404

#### Scenario: EAN mal formado
- **WHEN** se pide la ficha de `779089500099A`
- **THEN** la respuesta es un error 400

### Requirement: Serie de un producto
La API SHALL responder en `GET /productos/{ean}/serie` el precio del producto en cada bandera y en cada fecha disponible en que esa bandera lo publicó, ordenado por fecha. Una fecha en que una bandera no publicó el producto MUST NOT rellenarse con otro valor.

#### Scenario: Serie de dos semanas
- **WHEN** un producto tiene precio en una bandera el 2026-11-04 y el 2026-11-11
- **THEN** la serie de esa bandera trae los dos puntos, en orden de fecha

#### Scenario: Semana sin publicar
- **WHEN** una bandera publicó el producto el 2026-11-04 y no el 2026-11-11
- **THEN** la serie de esa bandera trae solo el punto del 2026-11-04

### Requirement: Canastas
La API SHALL ofrecer una canasta por defecto, cuya composición se lista en `GET /canasta`, y SHALL aceptar canastas propias pasando entre 1 y 50 EAN con el parámetro `ean` repetido. Un EAN mal formado o una lista de más de 50 MUST rechazarse.

#### Scenario: Canasta por defecto
- **WHEN** se pide `GET /canasta`
- **THEN** la respuesta lista los productos de la canasta por defecto con EAN y descripción

#### Scenario: Demasiados productos
- **WHEN** se pide el índice de una canasta propia con 51 EAN
- **THEN** la respuesta es un error 400

### Requirement: Índice de una canasta
La API SHALL calcular en `GET /canasta/indice` la variación de una canasta entre una fecha `desde` y una fecha `hasta`, por defecto las dos últimas fechas disponibles. Para cada producto SHALL tomar solo las banderas que lo publicaron en las dos fechas y calcular la media geométrica de sus variaciones; el índice de la canasta SHALL ser la media geométrica de las variaciones de los productos, con igual peso cada uno. Los productos sin ninguna bandera en las dos fechas MUST quedar fuera del cálculo y listados como excluidos. Las variaciones SHALL informarse en porcentaje, redondeadas a dos decimales.

#### Scenario: Cálculo con dos productos
- **WHEN** el producto A pasa de 100 a 110 en la bandera 1 y de 200 a 200 en la bandera 2, y el producto B pasa de 50 a 60 en su única bandera
- **THEN** la variación de A es 4,88%, la de B es 20,00% y el índice de la canasta es 12,19%

#### Scenario: Bandera ausente en una de las fechas
- **WHEN** una bandera publica un producto en `desde` pero no en `hasta`
- **THEN** esa bandera no entra en la variación del producto

#### Scenario: Producto sin banderas comparables
- **WHEN** ningún precio de un producto de la canasta se puede comparar entre las dos fechas
- **THEN** el producto figura como excluido y el índice se calcula con los demás

#### Scenario: Ningún producto comparable
- **WHEN** ningún producto de la canasta tiene una bandera en las dos fechas
- **THEN** la respuesta es un error 422

#### Scenario: Fechas no disponibles o invertidas
- **WHEN** `desde` o `hasta` no es una fecha disponible, o `desde` no es anterior a `hasta`
- **THEN** la respuesta es un error 400

#### Scenario: Canasta propia
- **WHEN** se pide el índice con `ean=7790895000997&ean=7790072002080`
- **THEN** el índice se calcula solo con esos dos productos

### Requirement: Acceso público de solo lectura
La API SHALL poder consultarse sin credenciales desde cualquier origen, incluido un navegador en otro dominio. La API MUST acceder a la base con una credencial que solo permite leer, y esa credencial MUST NOT estar en el código ni en el repositorio.

#### Scenario: Consulta desde un navegador
- **WHEN** una página de otro dominio pide `GET /fechas`
- **THEN** la respuesta permite que el navegador la lea

#### Scenario: La API no puede escribir en la base
- **WHEN** se intenta escribir en la base con la credencial que usa la API
- **THEN** la base rechaza la escritura

### Requirement: Serie del índice de una canasta
La API SHALL responder en `GET /canasta/serie` la variación de una canasta entre cada par de fechas disponibles consecutivas, calculada como el índice de una canasta para ese par, y la variación acumulada desde la primera fecha, encadenando las variaciones semanales sin redondeos intermedios. Sin el parámetro `ean` SHALL usar la canasta por defecto; con él, las mismas reglas de las canastas propias. Un par de fechas sin ningún producto comparable SHALL figurar sin variación y MUST NOT entrar en la acumulada. Con menos de dos fechas disponibles la respuesta MUST ser un error 422.

#### Scenario: Dos semanas encadenadas
- **WHEN** las fechas disponibles son tres y la canasta varía 2,00% entre la primera y la segunda y 3,00% entre la segunda y la tercera
- **THEN** la serie trae los dos pares, en orden de fecha, con 2,00% y 3,00%, y la variación acumulada es 5,06%

#### Scenario: Semana sin productos comparables
- **WHEN** entre dos fechas consecutivas ningún producto de la canasta se puede comparar
- **THEN** ese par figura sin variación y la acumulada se calcula con los demás

#### Scenario: Una sola fecha disponible
- **WHEN** hay una sola fecha disponible
- **THEN** la respuesta es un error 422

#### Scenario: Carga nueva
- **WHEN** la carga completa una fecha nueva
- **THEN** la serie siguiente incluye el par que termina en esa fecha
