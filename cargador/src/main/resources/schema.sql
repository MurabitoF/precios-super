-- Se aplica al arrancar. STRICT: SQLite rechaza un tipo equivocado en vez de guardarlo.
-- Fechas como texto AAAA-MM-DD; precios en centavos enteros.

-- Una fila por fecha de datos. completa_en nulo: la fecha está a medio cargar y no se debe mostrar.
CREATE TABLE IF NOT EXISTS carga (
    fecha       TEXT PRIMARY KEY,
    completa_en TEXT,
    filas       INTEGER
) STRICT;

-- fecha: fecha de datos de la que salió la fila; el upsert solo pisa con una igual o posterior.
CREATE TABLE IF NOT EXISTS bandera (
    id_comercio  INTEGER NOT NULL,
    id_bandera   INTEGER NOT NULL,
    razon_social TEXT    NOT NULL,
    nombre       TEXT    NOT NULL,
    fecha        TEXT    NOT NULL,
    PRIMARY KEY (id_comercio, id_bandera)
) STRICT;

CREATE TABLE IF NOT EXISTS producto (
    ean         TEXT PRIMARY KEY,
    descripcion TEXT NOT NULL,
    marca       TEXT NOT NULL,
    fecha       TEXT NOT NULL
) STRICT;

-- Mediana del precio de lista entre las sucursales de la bandera. La clave empieza por fecha
-- para que borrar o leer una semana sea un recorrido por rango.
CREATE TABLE IF NOT EXISTS precio (
    fecha       TEXT    NOT NULL,
    id_comercio INTEGER NOT NULL,
    id_bandera  INTEGER NOT NULL,
    ean         TEXT    NOT NULL,
    centavos    INTEGER NOT NULL,
    sucursales  INTEGER NOT NULL,
    PRIMARY KEY (fecha, id_comercio, id_bandera, ean)
) STRICT;
