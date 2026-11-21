import { ErrorApi } from "./api";

export const REPO = "https://github.com/MurabitoF/precios-super";

export function Cargando() {
  return <p className="secundario">Cargando…</p>;
}

/** Aviso común de error de la API, con reintento. */
export function AvisoError({ error, reintentar }: { error: unknown; reintentar: () => void }) {
  const detalle = error instanceof ErrorApi ? error.message : "No se pudo conectar con la API.";
  return (
    <div className="aviso" role="alert">
      <p>Hubo un problema al traer los datos. {detalle}</p>
      <button onClick={reintentar}>Reintentar</button>
    </div>
  );
}
