-- Copia de cargador/src/main/resources/schema.sql: los tests de la API prueban contra el mismo esquema. Van juntos.
-- Fechas como texto AAAA-MM-DD, precios en centavos enteros.

CREATE TABLE IF NOT EXISTS carga (
    fecha       TEXT PRIMARY KEY,
    completa_en TEXT,
    filas       INTEGER
) STRICT;

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

CREATE TABLE IF NOT EXISTS precio (
    fecha       TEXT    NOT NULL,
    id_comercio INTEGER NOT NULL,
    id_bandera  INTEGER NOT NULL,
    ean         TEXT    NOT NULL,
    centavos    INTEGER NOT NULL,
    sucursales  INTEGER NOT NULL,
    PRIMARY KEY (fecha, id_comercio, id_bandera, ean)
) STRICT;

CREATE INDEX IF NOT EXISTS precio_ean ON precio (ean, fecha);
