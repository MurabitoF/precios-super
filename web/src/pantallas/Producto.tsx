import { useParams } from "react-router";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, ErrorApi, type Ficha, type SerieProducto } from "../api";
import { AvisoError, Cargando } from "../comunes";
import { fecha, pesos } from "../formato";
import { useApi } from "../useApi";

// ponytail: el gráfico muestra hasta 8 cadenas para que se lea; la tabla, todas.
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

/** Dónde está más barato, el precio mediano y el más caro. */
function hallazgo(precios: Ficha["precios"]) {
  const [min, max] = [precios[0], precios.at(-1)!];
  if (precios.length === 1) return `Solo lo publica ${min.nombre}, a ${pesos(min.centavos)}.`;
  const medio = precios.length / 2;
  const mediana = precios.length % 2 ? precios[Math.floor(medio)].centavos : Math.round((precios[medio - 1].centavos + precios[medio].centavos) / 2);
  return `Más barato en ${min.nombre}, a ${pesos(min.centavos)}. Precio mediano entre ${precios.length} cadenas: ${pesos(mediana)}; el más caro, ${pesos(max.centavos)} en ${max.nombre}${max.nombre.endsWith(".") ? "" : "."}`;
}

/** Un punto por cadena sobre un mismo eje de precios; la más barata resaltada. */
function Franja({ precios }: { precios: Ficha["precios"] }) {
  const [min, max] = [precios[0].centavos, precios.at(-1)!.centavos];
  const pos = (c: number) => (max === min ? 50 : ((c - min) / (max - min)) * 100);
  return (
    <div className="franja" aria-hidden="true">
      <div className="franja-eje">
        {precios.map((p, i) => (
          <span key={`${p.comercio}-${p.bandera}`} className={i === 0 ? "punto barato" : "punto"} style={{ left: `${pos(p.centavos)}%` }} title={`${p.nombre}: ${pesos(p.centavos)}`} />
        ))}
      </div>
      <div className="franja-extremos secundario chico">
        <span>{pesos(min)}</span>
        <span>{pesos(max)}</span>
      </div>
    </div>
  );
}

function Contenido({ ficha, serie, ultima }: { ficha: Ficha; serie: SerieProducto; ultima?: string }) {
  const precios = [...ficha.precios].sort((a, b) => a.centavos - b.centavos);
  const nombres = precios.slice(0, MAX_LINEAS).map((p) => p.nombre).sort();
  const barata = precios[0]?.nombre;
  const filas = filasGrafico(serie, nombres);
  return (
    <>
      <div className="titulo">
        <h1 className="dato">{ficha.descripcion}</h1>
        <p className="secundario"><span className="dato">{ficha.marca}</span> · EAN {ficha.ean}</p>
        {precios.length > 0 && <p className="hallazgo">{hallazgo(precios).replace(/\$ /g, "$\u00a0")}</p>}
      </div>
      {precios.length > 1 && <Franja precios={precios} />}

      <div className="fila fila-producto">
      {filas.length > 1 && (
        <section className="panel area-grafico">
          <h2>Precio semanal</h2>
          <ul className="leyenda secundario chico" aria-hidden="true">
            <li><span className="muestra barata" />{barata}, la más barata</li>
            {nombres.length > 1 && <li><span className="muestra" />{precios.length > MAX_LINEAS ? `las ${MAX_LINEAS - 1} que le siguen` : "las demás cadenas"}</li>}
          </ul>
          <div className="grafico" aria-hidden="true">
            <ResponsiveContainer>
              <LineChart data={filas} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--grilla)" />
                <XAxis dataKey="fecha" tickFormatter={(f: string) => fecha(f).slice(0, 5)} tick={{ fill: "var(--tenue)", fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis tickFormatter={(v: number) => `$ ${v.toLocaleString("es-AR")}`} tick={{ fill: "var(--tenue)", fontSize: 12 }} axisLine={false} tickLine={false} width={72} domain={["auto", "auto"]} />
                <Tooltip formatter={(v) => (typeof v === "number" ? pesos(Math.round(v * 100)) : "")} labelFormatter={(f) => fecha(String(f))} />
                {[...nombres.filter((n) => n !== barata), barata].map((n) => (
                  <Line
                    key={n}
                    dataKey={n}
                    stroke={n === barata ? "var(--baja)" : "var(--linea-gris)"}
                    strokeWidth={n === barata ? 2.5 : 1.5}
                    dot={{ r: 3, fill: n === barata ? "var(--baja)" : "var(--linea-gris)", strokeWidth: 0 }}
                    connectNulls={false}
                    isAnimationActive={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}

      <section className="area-tabla">
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
      </section>
      </div>
    </>
  );
}
