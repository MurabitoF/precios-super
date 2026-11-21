import { useParams } from "react-router";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, ErrorApi, type Ficha, type SerieProducto } from "../api";
import { AvisoError, Cargando } from "../comunes";
import { fecha, pesos } from "../formato";
import { useApi } from "../useApi";

// ponytail: el gráfico muestra hasta 8 cadenas (los colores categóricos que se distinguen); la tabla, todas.
const MAX_LINEAS = 8;

/** Una fila por fecha con el precio en pesos de cada cadena; null donde la cadena no publicó (hueco en la línea). */
export function filasGrafico(serie: SerieProducto, nombres: string[]) {
  const fechas = [...new Set(serie.banderas.flatMap((b) => b.puntos.map((p) => p.fecha)))].sort();
  return fechas.map((f) => {
    const fila: Record<string, string | number | null> = { fecha: f };
    for (const b of serie.banderas.filter((b) => nombres.includes(b.nombre))) {
      const p = b.puntos.find((p) => p.fecha === f);
      fila[b.nombre] = p ? p.centavos / 100 : null;
    }
    return fila;
  });
}

export function Producto() {
  const ean = useParams().ean!;
  const [estado, reintentar] = useApi(() => Promise.all([api.ficha(ean), api.serieProducto(ean), api.fechas()]), [ean]);
  if (estado.tipo === "cargando") return <Cargando />;
  if (estado.tipo === "error") {
    if (estado.error instanceof ErrorApi && [400, 404].includes(estado.error.estado))
      return <p className="aviso">Producto no encontrado: no hay ningún producto con EAN {ean}.</p>;
    return <AvisoError error={estado.error} reintentar={reintentar} />;
  }
  const [ficha, serie, fechas] = estado.datos;
  return <Contenido ficha={ficha} serie={serie} ultima={fechas.at(-1)} />;
}

function Contenido({ ficha, serie, ultima }: { ficha: Ficha; serie: SerieProducto; ultima?: string }) {
  const precios = [...ficha.precios].sort((a, b) => a.centavos - b.centavos);
  const nombres = precios.slice(0, MAX_LINEAS).map((p) => p.nombre).sort();
  const filas = filasGrafico(serie, nombres);
  return (
    <>
      <h1>{ficha.descripcion}</h1>
      <p className="secundario">{ficha.marca} · EAN {ficha.ean}</p>

      <h2>Último precio por cadena</h2>
      <div className="tabla">
        <table aria-label="Precios por cadena">
          <thead>
            <tr><th>Cadena</th><th className="numero">Precio</th><th>Fecha</th></tr>
          </thead>
          <tbody>
            {precios.map((p) => (
              <tr key={`${p.comercio}-${p.bandera}`}>
                <td>{p.nombre}</td>
                <td className="numero">{pesos(p.centavos)}</td>
                <td className="secundario chico">{p.fecha === ultima ? "" : fecha(p.fecha)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {filas.length > 1 && (
        <>
          <h2>Precio semanal</h2>
          {precios.length > MAX_LINEAS && <p className="secundario chico">Las {MAX_LINEAS} cadenas más baratas.</p>}
          <div className="grafico" aria-hidden="true">
            <ResponsiveContainer>
              <LineChart data={filas} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--grilla)" />
                <XAxis dataKey="fecha" tickFormatter={(f: string) => fecha(f).slice(0, 5)} tick={{ fill: "var(--tenue)", fontSize: 12 }} stroke="var(--eje)" />
                <YAxis tickFormatter={(v: number) => `$ ${v.toLocaleString("es-AR")}`} tick={{ fill: "var(--tenue)", fontSize: 12 }} stroke="var(--eje)" width={72} domain={["auto", "auto"]} />
                <Tooltip formatter={(v) => (typeof v === "number" ? pesos(Math.round(v * 100)) : "")} labelFormatter={(f) => fecha(String(f))} />
                <Legend wrapperStyle={{ fontSize: 12, color: "var(--texto-2)" }} />
                {nombres.map((n, i) => (
                  <Line key={n} dataKey={n} stroke={`var(--serie-${i + 1})`} strokeWidth={2} dot={{ r: 4 }} connectNulls={false} isAnimationActive={false} />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </>
      )}
    </>
  );
}
