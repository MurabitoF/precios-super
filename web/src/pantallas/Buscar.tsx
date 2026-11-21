import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { api, type ProductoResumen } from "../api";
import { AvisoError, Cargando } from "../comunes";
import { useApi } from "../useApi";

export function Buscar() {
  const q = (useSearchParams()[0].get("q") ?? "").trim();
  // key: una búsqueda nueva arranca de cero.
  return <Resultados key={q} q={q} />;
}

function Resultados({ q }: { q: string }) {
  const corto = q.length < 3;
  const [primera, reintentar] = useApi(() => (corto ? Promise.resolve(null) : api.buscar(q)), [q]);
  const [mas, setMas] = useState<{ pagina: number; hayMas: boolean; productos: ProductoResumen[] } | null>(null);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [errorMas, setErrorMas] = useState<unknown>(null);

  if (corto) return <p className="aviso">Escribí al menos 3 caracteres para buscar.</p>;
  if (primera.tipo === "cargando") return <Cargando />;
  if (primera.tipo === "error") return <AvisoError error={primera.error} reintentar={reintentar} />;
  const base = primera.datos!;
  const actual = mas ?? base;
  const productos = mas?.productos ?? base.productos;

  const verMas = async () => {
    setCargandoMas(true);
    setErrorMas(null);
    try {
      const r = await api.buscar(q, actual.pagina + 1);
      setMas({ pagina: r.pagina, hayMas: r.hayMas, productos: [...productos, ...r.productos] });
    } catch (e) {
      setErrorMas(e);
    } finally {
      setCargandoMas(false);
    }
  };

  return (
    <>
      <h1>Resultados para “{q}”</h1>
      {productos.length === 0 ? (
        <p className="aviso">No hay productos que coincidan con “{q}”.</p>
      ) : (
        <ul className="resultados">
          {productos.map((p) => (
            <li key={p.ean}>
              <Link to={`/producto/${p.ean}`}>{p.descripcion}</Link>
              <div className="secundario chico">{p.marca} · EAN {p.ean}</div>
            </li>
          ))}
        </ul>
      )}
      {errorMas !== null && <AvisoError error={errorMas} reintentar={verMas} />}
      {actual.hayMas && errorMas === null && (
        <p>
          <button onClick={verMas} disabled={cargandoMas}>{cargandoMas ? "Cargando…" : "Ver más"}</button>
        </p>
      )}
    </>
  );
}
