// Una función tipada por endpoint de la API. La URL base se fija al compilar (VITE_API_URL).
const BASE = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");

export class ErrorApi extends Error {
  constructor(readonly estado: number, mensaje: string) {
    super(mensaje);
  }
}

async function pedir<T>(ruta: string): Promise<T> {
  const r = await fetch(BASE + ruta);
  if (!r.ok) {
    const problema = await r.json().catch(() => null);
    throw new ErrorApi(r.status, problema?.detail ?? `La API respondió ${r.status}`);
  }
  return r.json();
}

export type ProductoResumen = { ean: string; descripcion: string; marca: string };
export type Busqueda = { pagina: number; hayMas: boolean; productos: ProductoResumen[] };
export type Precio = { comercio: number; bandera: number; nombre: string; fecha: string; centavos: number; sucursales: number };
export type Ficha = ProductoResumen & { precios: Precio[] };
export type SerieBandera = { comercio: number; bandera: number; nombre: string; puntos: { fecha: string; centavos: number; sucursales: number }[] };
export type SerieProducto = { ean: string; descripcion: string; banderas: SerieBandera[] };
export type IndiceCanasta = {
  desde: string;
  hasta: string;
  variacion: number;
  productos: { ean: string; descripcion: string; variacion: number; banderas: number }[];
  excluidos: { ean: string; descripcion: string }[];
};
export type Semana = { desde: string; hasta: string; variacion: number | null };
export type SerieCanasta = { acumulada: number | null; desde: string; hasta: string; semanas: Semana[] };

export const api = {
  fechas: () => pedir<string[]>("/fechas"),
  buscar: (q: string, pagina = 1) => pedir<Busqueda>(`/productos?q=${encodeURIComponent(q)}&pagina=${pagina}`),
  ficha: (ean: string) => pedir<Ficha>(`/productos/${encodeURIComponent(ean)}`),
  serieProducto: (ean: string) => pedir<SerieProducto>(`/productos/${encodeURIComponent(ean)}/serie`),
  indiceCanasta: () => pedir<IndiceCanasta>("/canasta/indice"),
  serieCanasta: () => pedir<SerieCanasta>("/canasta/serie"),
};
