#!/usr/bin/env bash
# Archiva en S3 el ZIP SEPA del miércoles que publica el portal de Precios Claros.
# Diseño: openspec/changes/archivo-semanal-sepa/design.md
#
# Variables de entorno:
#   BUCKET     bucket de destino (obligatoria)
#   CKAN_URL   endpoint package_show de CKAN (acepta file:// para tests)
#   MIN_BYTES  tamaño mínimo aceptado del ZIP
#   AWS_ENDPOINT_URL  la usa la CLI de AWS; apunta a floci en los tests

CKAN_URL="${CKAN_URL:-https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios}"
BUCKET="${BUCKET:-}"
MIN_BYTES="${MIN_BYTES:-100000000}"

# stdin: respuesta de CKAN. stdout: URL y descripción del recurso Miércoles, una por línea.
recurso_miercoles() {
  jq -er 'first(.result.resources[] | select(.name == "Miércoles")) | .url, .description' \
    || { echo "no hay recurso Miércoles en la respuesta de CKAN" >&2; return 1; }
}

# $1: descripción del recurso. stdout: la fecha AAAA-MM-DD con la que termina.
fecha_de_descripcion() {
  local re='([0-9]{4}-[0-9]{2}-[0-9]{2})[[:space:]]*$'
  [[ $1 =~ $re ]] || { echo "descripción sin fecha: $1" >&2; return 1; }
  date -d "${BASH_REMATCH[1]}" >/dev/null 2>&1 || { echo "fecha inválida: ${BASH_REMATCH[1]}" >&2; return 1; }
  echo "${BASH_REMATCH[1]}"
}

es_miercoles() { [[ $(date -d "$1" +%u) == 3 ]]; }

nombre_objeto() { echo "sepa/$1.zip"; }

# $1: tamaño en bytes.
tamano_suficiente() { (( $1 >= MIN_BYTES )); }

main() {
  : "${BUCKET:?falta la variable BUCKET}"
  # tmp no es local: el trap de EXIT corre cuando main ya terminó.
  local recurso url desc fecha clave bytes

  recurso=$(curl -fsSL --retry 3 --max-time 60 "$CKAN_URL" | recurso_miercoles)
  url=${recurso%%$'\n'*}
  desc=${recurso#*$'\n'}
  fecha=$(fecha_de_descripcion "$desc")
  es_miercoles "$fecha" || { echo "$fecha no es miércoles" >&2; return 1; }
  clave=$(nombre_objeto "$fecha")

  if aws s3api head-object --bucket "$BUCKET" --key "$clave" >/dev/null 2>&1; then
    echo "$clave ya está archivado, nada que hacer"
    return 0
  fi

  tmp=$(mktemp)
  trap 'rm -f "$tmp"' EXIT
  echo "descargando $url"
  curl -fsSL --retry 3 -o "$tmp" "$url"

  bytes=$(stat -c %s "$tmp")
  tamano_suficiente "$bytes" || { echo "ZIP demasiado chico: $bytes bytes (mínimo $MIN_BYTES)" >&2; return 1; }
  unzip -tq "$tmp" >/dev/null || { echo "el ZIP no pasa la prueba de integridad" >&2; return 1; }

  # --if-none-match: si otro proceso lo escribió entre medio, falla en vez de pisarlo.
  aws s3api put-object --bucket "$BUCKET" --key "$clave" --body "$tmp" --if-none-match '*' >/dev/null
  echo "archivado s3://$BUCKET/$clave ($bytes bytes)"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  set -euo pipefail
  main "$@"
fi
