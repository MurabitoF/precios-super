#!/usr/bin/env bash
# Tests de integración de archivar.sh: casos I1 a I10 de design.md, uno por escenario del spec.
# Requiere floci corriendo: docker run -d --rm -p 4566:4566 floci/floci:latest
# El portal se simula con archivos locales (file://) y ZIP de prueba chicos.
cd "$(dirname "$0")" || exit 1

export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
export AWS_DEFAULT_REGION=us-east-1 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
export MIN_BYTES=100

curl -s "$AWS_ENDPOINT_URL" >/dev/null || { echo "floci no responde en $AWS_ENDPOINT_URL"; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fallas=0
ok()    { echo "ok    $1"; }
falla() { echo "FALLA $1"; fallas=$((fallas + 1)); }

# ZIP de prueba válido con contenido aleatorio (no comprimible, así el tamaño es predecible).
nuevo_zip() {
  local dir; dir=$(mktemp -d -p "$tmp")
  head -c 20000 /dev/urandom > "$dir/comercio.zip"
  : > "$dir/comercio_vacio.zip"
  (cd "$dir" && zip -q0 "$1" comercio.zip comercio_vacio.zip)
}

# Escribe una respuesta de CKAN con un recurso Miércoles y devuelve su URL file://.
ckan() { # $1 descripción, $2 ruta del ZIP publicado
  local f; f=$(mktemp -p "$tmp" ckan.XXXX.json)
  jq -n --arg d "$1" --arg u "file://$2" \
    '{success: true, result: {resources: [{name: "Miércoles", description: $d, url: $u}]}}' > "$f"
  echo "file://$f"
}

nuevo_bucket() { local b="t-$RANDOM$RANDOM"; aws s3api create-bucket --bucket "$b" >/dev/null; echo "$b"; }
objetos()      { aws s3 ls "s3://$1" --recursive | awk '{print $4}'; }
sha_objeto()   { aws s3 cp "s3://$1/$2" - | sha256sum | cut -d' ' -f1; }
sha()          { sha256sum "$1" | cut -d' ' -f1; }
archivar()     { BUCKET="$1" CKAN_URL="$2" ./archivar.sh >/dev/null 2>&1; }

falla_y_vacio() { # $1 nombre, $2 bucket, $3 url ckan
  if archivar "$2" "$3"; then falla "$1: la corrida debía fallar"
  elif [[ -n $(objetos "$2") ]]; then falla "$1: se guardó algo"
  else ok "$1"; fi
}

MIERCOLES="Precios SEPA Minoristas miercoles, 2026-11-04"

# I1
zip1="$tmp/i1.zip"; nuevo_zip "$zip1"; b=$(nuevo_bucket)
if archivar "$b" "$(ckan "$MIERCOLES" "$zip1")" && [[ $(sha_objeto "$b" sepa/2026-11-04.zip) == "$(sha "$zip1")" ]]
then ok "I1 miércoles publicado y no archivado: guardado byte a byte"
else falla "I1 miércoles publicado y no archivado"; fi

# I2: fecha distinta del día de la corrida
zip2="$tmp/i2.zip"; nuevo_zip "$zip2"; b=$(nuevo_bucket)
archivar "$b" "$(ckan "Precios SEPA Minoristas miercoles, 2026-10-28" "$zip2")"
if [[ $(objetos "$b") == "sepa/2026-10-28.zip" ]]
then ok "I2 nombre por fecha de los datos"
else falla "I2 nombre por fecha de los datos: objetos='$(objetos "$b")'"; fi

# I3
zip3="$tmp/i3.zip"; nuevo_zip "$zip3"
falla_y_vacio "I3 descripción sin fecha" "$(nuevo_bucket)" "$(ckan "Precios SEPA Minoristas miercoles" "$zip3")"

# I4: 2026-11-10 es martes
falla_y_vacio "I4 fecha que no es miércoles" "$(nuevo_bucket)" \
  "$(ckan "Precios SEPA Minoristas miercoles, 2026-11-10" "$zip3")"

# I5: el ZIP publicado no existe, así que si intentara descargarlo la corrida fallaría.
zip5="$tmp/i5.zip"; nuevo_zip "$zip5"; b=$(nuevo_bucket)
aws s3api put-object --bucket "$b" --key sepa/2026-11-04.zip --body "$zip5" >/dev/null
if archivar "$b" "$(ckan "$MIERCOLES" "$tmp/no-existe.zip")" && [[ $(sha_objeto "$b" sepa/2026-11-04.zip) == "$(sha "$zip5")" ]]
then ok "I5 fecha ya archivada: termina bien sin descargar ni escribir"
else falla "I5 fecha ya archivada"; fi

# I6
zip6a="$tmp/i6a.zip"; zip6b="$tmp/i6b.zip"; nuevo_zip "$zip6a"; nuevo_zip "$zip6b"; b=$(nuevo_bucket)
aws s3api put-object --bucket "$b" --key sepa/2026-11-04.zip --body "$zip6a" >/dev/null
archivar "$b" "$(ckan "$MIERCOLES" "$zip6b")"
if [[ $(sha_objeto "$b" sepa/2026-11-04.zip) == "$(sha "$zip6a")" ]]
then ok "I6 un archivo guardado no se pisa"
else falla "I6 un archivo guardado no se pisa"; fi

# I7: el miércoles el ZIP no está disponible; el jueves sí.
zip7="$tmp/i7.zip"; b=$(nuevo_bucket); url7=$(ckan "$MIERCOLES" "$zip7")
if archivar "$b" "$url7"; then falla "I7 falla el día de publicación: la primera corrida debía fallar"
else
  nuevo_zip "$zip7"
  if archivar "$b" "$url7" && [[ $(sha_objeto "$b" sepa/2026-11-04.zip) == "$(sha "$zip7")" ]]
  then ok "I7 falla el día de publicación: la siguiente corrida archiva"
  else falla "I7 falla el día de publicación: la segunda corrida no archivó"; fi
fi

# I8: nada escucha en el puerto 1
falla_y_vacio "I8 portal caído" "$(nuevo_bucket)" "http://127.0.0.1:1/api/3/action/package_show"

# I9
zip9="$tmp/i9.zip"; nuevo_zip "$tmp/i9-completo.zip"
head -c 15000 "$tmp/i9-completo.zip" > "$zip9"
falla_y_vacio "I9 descarga truncada" "$(nuevo_bucket)" "$(ckan "$MIERCOLES" "$zip9")"

# I10
zip10="$tmp/i10.zip"; nuevo_zip "$zip10"
MIN_BYTES=$(( $(stat -c %s "$zip10") + 1 )) \
  falla_y_vacio "I10 archivo demasiado chico" "$(nuevo_bucket)" "$(ckan "$MIERCOLES" "$zip10")"

echo
if (( fallas )); then echo "$fallas fallas"; exit 1; fi
echo "todos los de integración pasan"
