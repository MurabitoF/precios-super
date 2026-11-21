import { Link } from "react-router";
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type IndiceCanasta } from "../api";

import { fecha, variacion } from "../formato";
import { useApi } from "../useApi";
import { AvisoError, Cargando, REPO } from "../comunes";

/** Marcas redondas del eje Y que cubren los valores, con el tope por encima del máximo. */
export function marcasEje(valores: number[]) {
  const max = Math.max(0, ...valores);
  const min = Math.min(0, ...valores);
  const paso = [0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10].find((p) => (max - min) / p <= 4) ?? 20;
  const marcas = [];
  for (let v = Math.floor(min / paso) * paso; v <= max + paso / 2; v += paso) marcas.push(Math.round(v * 100) / 100);
  if (marcas.at(-1)! <= max) marcas.push(Math.round((marcas.at(-1)! + paso) * 100) / 100);
  return marcas;
}

/** La frase que resume la semana: cuántos subieron y bajaron, y los extremos. */
function hallazgo(indice: IndiceCanasta) {
  const ps = [...indice.productos].sort((a, b) => b.variacion - a.variacion);
  const suben = ps.filter((p) => p.variacion > 0).length;
  const bajan = ps.filter((p) => p.variacion < 0).length;
  const partes = [`Subieron ${suben} de ${ps.length} productos y bajaron ${bajan}.`];
  if (suben > 0) partes.push(`La mayor suba: ${ps[0].descripcion}, ${variacion(ps[0].variacion)}.`);
  if (bajan > 0) partes.push(`La mayor baja: ${ps.at(-1)!.descripcion}, ${variacion(ps.at(-1)!.variacion)}.`);
  // Espacio duro: que el % no quede solo en otra línea.
  return partes.join(" ").replace(/ %/g, "\u00a0%");
}

export function Inicio() {
  const [estado, reintentar] = useApi(() => Promise.all([api.serieCanasta(), api.indiceCanasta()]), []);
  if (estado.tipo === "cargando") return <Cargando />;
  if (estado.tipo === "error") return <AvisoError error={estado.error} reintentar={reintentar} />;
  const [serie, indice] = estado.datos;
  const semanas = serie.semanas.map((s) => ({ semana: fecha(s.hasta).slice(0, 5), ...s }));
  const marcas = marcasEje(semanas.map((s) => s.variacion ?? 0));
  const productos = [...indice.productos].sort((a, b) => b.variacion - a.variacion);
  const escala = Math.max(...productos.map((p) => Math.abs(p.variacion)), 0.01);

  return (
    <>
      <div className="titulo">
        <h1>¿Cuánto subió la canasta?</h1>
        <p className="hallazgo">{hallazgo(indice)}</p>
        <p className="secundario chico">
          Datos al {fecha(indice.hasta)} · canasta de {indice.productos.length + indice.excluidos.length} productos
        </p>
      </div>

      <div className="fila fila-resumen">
        <div className="cifras">
          <div className="cifra" data-testid="ultima-semana">
            <div className="secundario chico">Última semana</div>
            <div className="valor principal">{variacion(indice.variacion)}</div>
            <div className="secundario chico">{fecha(indice.desde)} al {fecha(indice.hasta)}</div>
          </div>
          <div className="cifra" data-testid="acumulada">
            <div className="secundario chico">Acumulada</div>
            <div className="valor">{serie.acumulada === null ? "—" : variacion(serie.acumulada)}</div>
            <div className="secundario chico">{fecha(serie.desde)} al {fecha(serie.hasta)}</div>
          </div>
        </div>

        <section className="panel">
          <h2>Variación semana a semana</h2>
          <div className="grafico chato" aria-hidden="true">
            <ResponsiveContainer>
              <BarChart data={semanas} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--grilla)" />
                <XAxis dataKey="semana" tick={{ fill: "var(--tenue)", fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis
                  ticks={marcas}
                  domain={[marcas[0], marcas.at(-1)!]}
                  tickFormatter={(v: number) => `${v.toLocaleString("es-AR", { maximumFractionDigits: 2 })} %`}
                  tick={{ fill: "var(--tenue)", fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                  width={56}
                />
                <ReferenceLine y={0} stroke="var(--eje)" strokeWidth={2} />
                <Tooltip
                  cursor={{ fill: "var(--grilla)" }}
                  formatter={(v) => (typeof v === "number" ? variacion(v) : "sin datos")}
                  labelFormatter={(_, p) => (p[0] ? `${fecha(p[0].payload.desde)} al ${fecha(p[0].payload.hasta)}` : "")}
                />
                <Bar dataKey="variacion" name="Variación" maxBarSize={40} isAnimationActive={false}>
                  {semanas.map((s) => (
                    <Cell key={s.hasta} className={(s.variacion ?? 0) >= 0 ? "barra-sube" : "barra-baja"} />
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
        </section>
      </div>

      <div className="fila fila-detalle">
        <section>
          <h2>Detalle de la última semana</h2>
          <div className="tabla">
            <table aria-label="Detalle por producto">
              <thead>
                <tr><th>Producto</th><th className="numero">Variación</th><th className="numero">Cadenas</th></tr>
              </thead>
              <tbody>
                {productos.map((p) => {
                  const ancho = `${(Math.abs(p.variacion) / escala) * 50}%`;
                  return (
                    <tr key={p.ean}>
                      <td><Link to={`/producto/${p.ean}`}>{p.descripcion}</Link></td>
                      <td className="numero"><span className="celda-barra">
                        <span className="pista" aria-hidden="true">
                          {p.variacion !== 0 && (
                            <span
                              className={p.variacion > 0 ? "relleno sube" : "relleno baja"}
                              style={p.variacion > 0 ? { left: "50%", width: ancho } : { right: "50%", width: ancho }}
                            />
                          )}
                        </span>
                        {variacion(p.variacion)}</span>
                      </td>
                      <td className="numero">{p.banderas}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        <aside>
          {indice.excluidos.length > 0 && (
            <>
              <h2>Sin datos comparables esta semana</h2>
              <ul className="lista-simple" aria-label="Productos sin datos comparables">
                {indice.excluidos.map((e) => (
                  <li key={e.ean}><Link to={`/producto/${e.ean}`}>{e.descripcion}</Link></li>
                ))}
              </ul>
            </>
          )}

          <h2>Cómo se calcula</h2>
          <p>
            Los precios salen de la base SEPA de Precios Claros, que publican los supermercados por obligación legal. Se
            archiva el archivo de cada miércoles y, por producto y cadena, se toma la mediana del precio entre sus sucursales.
          </p>
          <p>
            La variación de la canasta es un índice de Jevons: para cada producto, la media geométrica de sus variaciones en
            las cadenas que lo publicaron las dos semanas; para la canasta, la media geométrica de los productos, todos con
            el mismo peso. La acumulada encadena las variaciones semanales.
          </p>
          <p>
            <a href={REPO}>Código y método completo en GitHub</a>
          </p>
        </aside>
      </div>
    </>
  );
}
