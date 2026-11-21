import { Link } from "react-router";
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../api";

import { fecha, variacion } from "../formato";
import { useApi } from "../useApi";
import { AvisoError, Cargando, REPO } from "../comunes";

export function Inicio() {
  const [estado, reintentar] = useApi(() => Promise.all([api.serieCanasta(), api.indiceCanasta()]), []);
  if (estado.tipo === "cargando") return <Cargando />;
  if (estado.tipo === "error") return <AvisoError error={estado.error} reintentar={reintentar} />;
  const [serie, indice] = estado.datos;
  const semanas = serie.semanas.map((s) => ({ semana: fecha(s.hasta).slice(0, 5), ...s }));
  const productos = [...indice.productos].sort((a, b) => b.variacion - a.variacion);

  return (
    <>
      <h1>¿Cuánto subió la canasta?</h1>
      <div className="tarjetas">
        <div className="tarjeta" data-testid="ultima-semana">
          <div className="secundario chico">Última semana</div>
          <div className="valor">{variacion(indice.variacion)}</div>
          <div className="secundario chico">{fecha(indice.desde)} al {fecha(indice.hasta)}</div>
        </div>
        <div className="tarjeta" data-testid="acumulada">
          <div className="secundario chico">Acumulada</div>
          <div className="valor">{serie.acumulada === null ? "—" : variacion(serie.acumulada)}</div>
          <div className="secundario chico">{fecha(serie.desde)} al {fecha(serie.hasta)}</div>
        </div>
      </div>

      <h2>Variación semana a semana</h2>
      <div className="grafico" aria-hidden="true">
        <ResponsiveContainer>
          <BarChart data={semanas} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--grilla)" />
            <XAxis dataKey="semana" tick={{ fill: "var(--tenue)", fontSize: 12 }} stroke="var(--eje)" />
            <YAxis tickFormatter={(v: number) => `${v} %`} tick={{ fill: "var(--tenue)", fontSize: 12 }} stroke="var(--eje)" width={52} />
            <ReferenceLine y={0} stroke="var(--eje)" />
            <Tooltip
              cursor={{ fill: "var(--grilla)" }}
              formatter={(v) => (typeof v === "number" ? variacion(v) : "sin datos")}
              labelFormatter={(_, p) => (p[0] ? `${fecha(p[0].payload.desde)} al ${fecha(p[0].payload.hasta)}` : "")}
            />
            <Bar dataKey="variacion" name="Variación" radius={4} maxBarSize={48}>
              {semanas.map((s) => (
                <Cell key={s.hasta} fill={(s.variacion ?? 0) >= 0 ? "var(--sube)" : "var(--baja)"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="solo-lectores" aria-label="Variación de cada semana">
        <tbody>
          {serie.semanas.map((s) => (
            <tr key={s.hasta}>
              <td>{fecha(s.desde)} al {fecha(s.hasta)}</td>
              <td>{s.variacion === null ? "sin productos comparables" : variacion(s.variacion)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Detalle de la última semana</h2>
      <div className="tabla">
        <table aria-label="Detalle por producto">
          <thead>
            <tr><th>Producto</th><th className="numero">Variación</th><th className="numero">Cadenas</th></tr>
          </thead>
          <tbody>
            {productos.map((p) => (
              <tr key={p.ean}>
                <td><Link to={`/producto/${p.ean}`}>{p.descripcion}</Link></td>
                <td className="numero">{variacion(p.variacion)}</td>
                <td className="numero">{p.banderas}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {indice.excluidos.length > 0 && (
        <>
          <h2>Sin datos comparables esta semana</h2>
          <ul aria-label="Productos sin datos comparables">
            {indice.excluidos.map((e) => (
              <li key={e.ean}><Link to={`/producto/${e.ean}`}>{e.descripcion}</Link></li>
            ))}
          </ul>
        </>
      )}

      <h2>Cómo se calcula</h2>
      <p>
        Los precios salen de la base SEPA de Precios Claros, que publican los supermercados por obligación legal. Se archiva
        el archivo de cada miércoles y, por producto y cadena, se toma la mediana del precio entre sus sucursales.
      </p>
      <p>
        La variación de la canasta es un índice de Jevons: para cada producto, la media geométrica de sus variaciones en
        las cadenas que lo publicaron las dos semanas; para la canasta, la media geométrica de los productos, todos con
        el mismo peso. La acumulada encadena las variaciones semanales.
      </p>
      <p>
        <a href={REPO}>Código y método completo en GitHub</a>
      </p>
    </>
  );
}
