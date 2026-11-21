#!/usr/bin/env bash
# Tests unitarios de archivar.sh: casos U1 a U8 de design.md. Sin red ni Docker.
cd "$(dirname "$0")" || exit 1
source ./archivar.sh

fallas=0
ok()      { echo "ok    $1"; }
falla()   { echo "FALLA $1"; fallas=$((fallas + 1)); }
igual()   { if [[ $2 == "$3" ]]; then ok "$1"; else falla "$1: esperaba '$3', obtuvo '$2'"; fi; }
exito()   { if "${@:2}" 2>/dev/null; then ok "$1"; else falla "$1: esperaba éxito"; fi; }
error()   { if "${@:2}" >/dev/null 2>&1; then falla "$1: esperaba error"; else ok "$1"; fi; }

igual "U1 fecha de la descripción" \
  "$(fecha_de_descripcion 'Precios SEPA Minoristas miercoles, 2026-11-04')" "2026-11-04"

error "U2 descripción sin fecha" fecha_de_descripcion 'Precios SEPA Minoristas miercoles'

error "U3 fecha DD-MM-AAAA" fecha_de_descripcion 'Precios SEPA Minoristas miercoles, 04-11-2026'
error "U3 fecha inexistente" fecha_de_descripcion 'Precios SEPA Minoristas miercoles, 2026-13-45'

exito "U4 2026-11-04 es miércoles" es_miercoles 2026-11-04
error "U4 2026-11-10 no es miércoles" es_miercoles 2026-11-10

igual "U5 nombre del objeto" "$(nombre_objeto 2026-11-04)" "sepa/2026-11-04.zip"

igual "U6 recurso Miércoles de CKAN" "$(recurso_miercoles < testdata/ckan_con_miercoles.json)" \
"https://datos.produccion.gob.ar/dataset/6f47ec76-d1ce-4e34-a7e1-621fe9b1d0b5/resource/1e92cd42-4f94-4071-a165-62c4cb2ce23c/download/sepa_miercoles.zip
Precios SEPA Minoristas miercoles, 2026-11-11"

error "U7 CKAN sin recurso Miércoles" recurso_miercoles < testdata/ckan_sin_miercoles.json

MIN_BYTES=100
error "U8 por debajo del mínimo" tamano_suficiente 99
exito "U8 igual al mínimo" tamano_suficiente 100
exito "U8 por encima del mínimo" tamano_suficiente 101

echo
if (( fallas )); then echo "$fallas fallas"; exit 1; fi
echo "todos los unitarios pasan"
