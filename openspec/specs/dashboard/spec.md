# dashboard Specification

## Purpose

Mostrar en un sitio web público, a partir de la API, cuánto varió la canasta, cómo cambió el precio de cada producto y en qué cadena sale menos.

## Requirements

### Requirement: Inicio con la canasta
El inicio SHALL mostrar, para la canasta por defecto, la variación entre las dos últimas fechas disponibles, la variación acumulada desde la primera fecha disponible con el período que abarca, un gráfico con la variación de cada semana y el detalle de cada producto con su variación de la última semana y la cantidad de cadenas comparadas, ordenado de mayor a menor variación. Los productos que no se pudieron comparar SHALL listarse aparte.

#### Scenario: Canasta con varias semanas
- **WHEN** la API informa variaciones semanales de 2,00% y 3,00% y una acumulada de 5,06%
- **THEN** el inicio muestra +3,00% como última semana, +5,06% como acumulada y un gráfico con las dos semanas

#### Scenario: Detalle por producto
- **WHEN** la última semana un producto subió 4,88% en 2 cadenas y otro 20,00% en 1
- **THEN** el detalle muestra primero el de 20,00% y después el de 4,88%, cada uno con su cantidad de cadenas

#### Scenario: Producto excluido
- **WHEN** un producto de la canasta no se pudo comparar la última semana
- **THEN** figura en la lista de productos sin datos comparables

### Requirement: Búsqueda de productos
El sitio SHALL permitir buscar productos por texto o EAN y mostrar cada resultado con descripción, marca y EAN, enlazado a su página. SHALL ofrecer cargar más resultados cuando la API indique que hay más, y SHALL avisar cuando no hay resultados. Un texto de menos de 3 caracteres MUST NOT enviarse a la API.

#### Scenario: Resultados
- **WHEN** se busca `coca` y la API devuelve dos productos
- **THEN** se muestran los dos, cada uno con un enlace a su página

#### Scenario: Más resultados
- **WHEN** la API indica que hay más resultados y se pide verlos
- **THEN** se agregan los de la página siguiente debajo de los anteriores

#### Scenario: Sin resultados
- **WHEN** la búsqueda no encuentra productos
- **THEN** se muestra un aviso de que no hay resultados

#### Scenario: Texto corto
- **WHEN** se escriben 2 caracteres
- **THEN** no se consulta la API y se indica que hacen falta al menos 3

### Requirement: Página de producto
La página de un producto SHALL mostrar su descripción, marca y EAN, su último precio en cada cadena de menor a mayor, con la fecha cuando no es la última disponible, y un gráfico de su precio semanal por cadena. Una semana sin precio de una cadena MUST mostrarse como un hueco en su línea, no como un valor. Un EAN inexistente o mal formado SHALL mostrar un aviso de producto no encontrado.

#### Scenario: Producto en varias cadenas
- **WHEN** un producto cuesta $ 6.150,00 en una cadena y $ 6.100,00 en otra
- **THEN** la tabla muestra primero la de $ 6.100,00

#### Scenario: Precio de una semana anterior
- **WHEN** una cadena publicó el producto por última vez en una fecha anterior a la última disponible
- **THEN** su precio se muestra con esa fecha

#### Scenario: Semana faltante
- **WHEN** una cadena no publicó el producto en una de las semanas
- **THEN** su línea del gráfico tiene un hueco en esa semana

#### Scenario: Producto inexistente
- **WHEN** se abre la página de un EAN que la API no encuentra
- **THEN** se muestra un aviso de producto no encontrado

### Requirement: Formato de precios, variaciones y fechas
El sitio SHALL mostrar los precios en pesos argentinos con dos decimales, las variaciones en porcentaje con signo y dos decimales, y las fechas en formato `dd/mm/aaaa`.

#### Scenario: Precio
- **WHEN** la API informa `610000` centavos
- **THEN** se muestra `$ 6.100,00`

#### Scenario: Variación negativa
- **WHEN** la API informa una variación de `-1.5`
- **THEN** se muestra `-1,50 %`

### Requirement: Direcciones compartibles
Cada búsqueda y cada página de producto SHALL tener su propia dirección, que abierta directamente en el navegador muestra lo mismo.

#### Scenario: Abrir la página de un producto
- **WHEN** se abre directamente la dirección de la página de un producto
- **THEN** se muestra esa página, sin pasar por el inicio

#### Scenario: Abrir una búsqueda
- **WHEN** se abre directamente la dirección de una búsqueda de `yerba`
- **THEN** se muestran los resultados de `yerba`

### Requirement: Errores visibles
Cuando la API no responde o responde con error, el sitio SHALL mostrar un aviso con la opción de reintentar, y MUST NOT quedar en blanco.

#### Scenario: API caída
- **WHEN** la API no responde al cargar el inicio
- **THEN** se muestra un aviso de error con un botón para reintentar

#### Scenario: Reintento
- **WHEN** después de un error se reintenta y la API responde
- **THEN** se muestra el contenido normal

### Requirement: Uso en el celular
El sitio SHALL poder usarse en una pantalla de 360 px de ancho sin desplazamiento horizontal de la página.

#### Scenario: Pantalla angosta
- **WHEN** se abre cualquier pantalla en un ancho de 360 px
- **THEN** todo el contenido entra en el ancho y las tablas anchas se desplazan dentro de su propio recuadro

### Requirement: Fuente y método a la vista
El sitio SHALL indicar de dónde salen los datos (base SEPA de Precios Claros), cómo se calcula el índice de la canasta y enlazar al repositorio del proyecto.

#### Scenario: Explicación del índice
- **WHEN** se abre el inicio
- **THEN** hay una explicación breve del método y un enlace al repositorio
