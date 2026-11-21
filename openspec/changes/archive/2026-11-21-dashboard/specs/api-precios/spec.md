## ADDED Requirements

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
