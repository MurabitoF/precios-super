import { Link, Route, Routes, useNavigate, useSearchParams } from "react-router";
import { Inicio } from "./pantallas/Inicio";
import { Buscar } from "./pantallas/Buscar";
import { Producto } from "./pantallas/Producto";
import { REPO } from "./comunes";



function Buscador() {
  const [params] = useSearchParams();
  const navegar = useNavigate();
  return (
    <form
      className="buscador"
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        const q = new FormData(e.currentTarget).get("q")?.toString().trim() ?? "";
        navegar(`/buscar?q=${encodeURIComponent(q)}`);
      }}
    >
      <input name="q" type="search" aria-label="Buscar producto" placeholder="Producto o EAN" defaultValue={params.get("q") ?? ""} key={params.get("q")} />
      <button type="submit">Buscar</button>
    </form>
  );
}

export function App() {
  return (
    <>
      <header className="cabecera">
        <Link to="/" className="marca">Precios Super</Link>
        <Buscador />
      </header>
      <main>
        <Routes>
          <Route path="/buscar" element={<Buscar />} />
          <Route path="/producto/:ean" element={<Producto />} />
          <Route path="*" element={<Inicio />} />
        </Routes>
      </main>
      <footer className="secundario chico">
        Datos: base SEPA de Precios Claros (Secretaría de Comercio). Código: <a href={REPO}>{REPO.replace("https://", "")}</a>
      </footer>
    </>
  );
}
