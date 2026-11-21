import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { App } from "./App";
import { filasGrafico } from "./pantallas/Producto";

const COCA = "7790895000997";

type Respuesta = { estado?: number; cuerpo: unknown } | "caida";

/** Simula la API: cada ruta (con su query) responde lo indicado; devuelve el espía de fetch. */
function api(rutas: Record<string, Respuesta | Respuesta[]>) {
  const espia = vi.fn(async (url: string) => {
    const ruta = url.replace(/^https?:\/\/[^/]+/, "");
    const r = rutas[ruta];
    if (r === undefined) throw new Error(`ruta no simulada: ${ruta}`);
    const resp = Array.isArray(r) ? (r.length > 1 ? r.shift()! : r[0]) : r;
    if (resp === "caida") throw new TypeError("Failed to fetch");
    return new Response(JSON.stringify(resp.cuerpo), { status: resp.estado ?? 200 });
  });
  vi.stubGlobal("fetch", espia);
  return espia;
}

const abrir = (url: string) =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <App />
    </MemoryRouter>,
  );

const serieCanasta = {
  acumulada: 5.06,
  desde: "2026-11-04",
  hasta: "2026-11-18",
  semanas: [
    { desde: "2026-11-04", hasta: "2026-11-11", variacion: 2 },
    { desde: "2026-11-11", hasta: "2026-11-18", variacion: 3 },
  ],
};
const indiceCanasta = {
  desde: "2026-11-11",
  hasta: "2026-11-18",
  variacion: 3,
  productos: [
    { ean: COCA, descripcion: "Gaseosa Coca-Cola 2,25 l", variacion: 4.88, banderas: 2 },
    { ean: "7790072002080", descripcion: "Sal fina Celusal 500 g", variacion: 20, banderas: 1 },
  ],
  excluidos: [{ ean: "7792710000182", descripcion: "Yerba Amanda 1 kg" }],
};
const inicio = { "/canasta/serie": { cuerpo: serieCanasta }, "/canasta/indice": { cuerpo: indiceCanasta } };

const ficha = {
  ean: COCA,
  descripcion: "GASEO COLA 2,25 L",
  marca: "COCA COLA",
  precios: [
    { comercio: 10, bandera: 1, nombre: "Carrefour", fecha: "2026-11-18", centavos: 615000, sucursales: 3 },
    { comercio: 12, bandera: 1, nombre: "Coto", fecha: "2026-11-18", centavos: 610000, sucursales: 3 },
    { comercio: 15, bandera: 1, nombre: "Día", fecha: "2026-11-11", centavos: 620000, sucursales: 3 },
  ],
};
const serieProducto = {
  ean: COCA,
  descripcion: "GASEO COLA 2,25 L",
  banderas: [
    { comercio: 10, bandera: 1, nombre: "Carrefour", puntos: [{ fecha: "2026-11-04", centavos: 600000, sucursales: 3 }, { fecha: "2026-11-18", centavos: 615000, sucursales: 3 }] },
    { comercio: 12, bandera: 1, nombre: "Coto", puntos: [{ fecha: "2026-11-04", centavos: 590000, sucursales: 3 }, { fecha: "2026-11-11", centavos: 600000, sucursales: 3 }, { fecha: "2026-11-18", centavos: 610000, sucursales: 3 }] },
  ],
};
const producto = {
  [`/productos/${COCA}`]: { cuerpo: ficha },
  [`/productos/${COCA}/serie`]: { cuerpo: serieProducto },
  "/fechas": { cuerpo: ["2026-11-04", "2026-11-11", "2026-11-18"] },
};
const resumen = (n: number) => ({ ean: String(7790000000000 + n), descripcion: `Producto ${n}`, marca: "MARCA" });

test("W4 Canasta con varias semanas", async () => {
  api(inicio);
  abrir("/");
  expect(within(await screen.findByTestId("ultima-semana")).getByText("+3,00 %")).toBeInTheDocument();
  expect(within(screen.getByTestId("acumulada")).getByText("+5,06 %")).toBeInTheDocument();
  expect(within(screen.getByTestId("acumulada")).getByText("04/11/2026 al 18/11/2026")).toBeInTheDocument();
  expect(within(screen.getByRole("table", { name: "Variación de cada semana" })).getAllByRole("row")).toHaveLength(2);
});

test("W5 Detalle por producto", async () => {
  api(inicio);
  abrir("/");
  const filas = within(await screen.findByRole("table", { name: "Detalle por producto" })).getAllByRole("row").slice(1);
  expect(filas.map((f) => within(f).getAllByRole("cell").map((c) => c.textContent))).toEqual([
    ["Sal fina Celusal 500 g", "+20,00 %", "1"],
    ["Gaseosa Coca-Cola 2,25 l", "+4,88 %", "2"],
  ]);
});

test("W6 Producto excluido", async () => {
  api(inicio);
  abrir("/");
  expect(within(await screen.findByRole("list", { name: "Productos sin datos comparables" })).getByText("Yerba Amanda 1 kg")).toBeInTheDocument();
});

test("W7 Resultados", async () => {
  api({ "/productos?q=coca&pagina=1": { cuerpo: { pagina: 1, hayMas: false, productos: [resumen(1), resumen(2)] } } });
  abrir("/");
  await userEvent.type(screen.getByRole("searchbox"), "coca{Enter}");
  expect(await screen.findByRole("link", { name: "Producto 1" })).toHaveAttribute("href", "/producto/7790000000001");
  expect(screen.getByRole("link", { name: "Producto 2" })).toHaveAttribute("href", "/producto/7790000000002");
});

test("W8 Más resultados", async () => {
  api({
    "/productos?q=coca&pagina=1": { cuerpo: { pagina: 1, hayMas: true, productos: [resumen(1)] } },
    "/productos?q=coca&pagina=2": { cuerpo: { pagina: 2, hayMas: false, productos: [resumen(2)] } },
  });
  abrir("/buscar?q=coca");
  await userEvent.click(await screen.findByRole("button", { name: "Ver más" }));
  await screen.findByText("Producto 2");
  expect(screen.getAllByRole("listitem").map((l) => l.querySelector("a")!.textContent)).toEqual(["Producto 1", "Producto 2"]);
  expect(screen.queryByRole("button", { name: "Ver más" })).not.toBeInTheDocument();
});

test("W9 Sin resultados", async () => {
  api({ "/productos?q=zzzz&pagina=1": { cuerpo: { pagina: 1, hayMas: false, productos: [] } } });
  abrir("/buscar?q=zzzz");
  expect(await screen.findByText(/No hay productos que coincidan/)).toBeInTheDocument();
});

test("W10 Texto corto", async () => {
  const espia = api(inicio);
  abrir("/");
  await screen.findByTestId("ultima-semana");
  espia.mockClear();
  await userEvent.type(screen.getByRole("searchbox"), "co{Enter}");
  expect(await screen.findByText(/al menos 3 caracteres/)).toBeInTheDocument();
  expect(espia).not.toHaveBeenCalled();
});

test("W11 Producto en varias cadenas", async () => {
  api(producto);
  abrir(`/producto/${COCA}`);
  const filas = within(await screen.findByRole("table", { name: "Precios por cadena" })).getAllByRole("row").slice(1);
  expect(filas.map((f) => within(f).getAllByRole("cell")[0].textContent)).toEqual(["Coto", "Carrefour", "Día"]);
  expect(within(filas[0]).getByText("$ 6.100,00")).toBeInTheDocument();
});

test("W12 Precio de una semana anterior", async () => {
  api(producto);
  abrir(`/producto/${COCA}`);
  const filas = within(await screen.findByRole("table", { name: "Precios por cadena" })).getAllByRole("row").slice(1);
  expect(within(filas[2]).getByText("11/11/2026")).toBeInTheDocument();
  expect(within(filas[0]).getAllByRole("cell")[2]).toHaveTextContent(/^$/);
});

test("W13 Semana faltante", () => {
  const filas = filasGrafico(serieProducto, ["Carrefour", "Coto"]);
  expect(filas).toEqual([
    { fecha: "2026-11-04", Carrefour: 6000, Coto: 5900 },
    { fecha: "2026-11-11", Carrefour: null, Coto: 6000 },
    { fecha: "2026-11-18", Carrefour: 6150, Coto: 6100 },
  ]);
});

test("W14 Producto inexistente", async () => {
  const noEsta = { estado: 404, cuerpo: { detail: "no hay ningún producto con EAN 7790000000000" } };
  api({ "/productos/7790000000000": noEsta, "/productos/7790000000000/serie": noEsta, "/fechas": { cuerpo: [] } });
  abrir("/producto/7790000000000");
  expect(await screen.findByText(/Producto no encontrado/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Reintentar" })).not.toBeInTheDocument();
});

test("W15 Abrir la página de un producto", async () => {
  const espia = api(producto);
  abrir(`/producto/${COCA}`);
  expect(await screen.findByRole("heading", { name: "GASEO COLA 2,25 L" })).toBeInTheDocument();
  expect(espia.mock.calls.map((c) => c[0])).not.toContain("/canasta/serie");
});

test("W16 Abrir una búsqueda", async () => {
  api({ "/productos?q=yerba&pagina=1": { cuerpo: { pagina: 1, hayMas: false, productos: [{ ean: "7792710000182", descripcion: "YERBA TRADICIONAL", marca: "AMANDA" }] } } });
  abrir("/buscar?q=yerba");
  expect(await screen.findByRole("link", { name: "YERBA TRADICIONAL" })).toBeInTheDocument();
  expect(screen.getByRole("searchbox")).toHaveValue("yerba");
});

test("W17 API caída", async () => {
  api({ "/canasta/serie": "caida", "/canasta/indice": "caida" });
  abrir("/");
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Reintentar" })).toBeInTheDocument();
});

test("W18 Reintento", async () => {
  api({ "/canasta/serie": ["caida", { cuerpo: serieCanasta }], "/canasta/indice": { cuerpo: indiceCanasta } });
  abrir("/");
  await userEvent.click(await screen.findByRole("button", { name: "Reintentar" }));
  expect(within(await screen.findByTestId("ultima-semana")).getByText("+3,00 %")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

test("W19 Explicación del índice", async () => {
  api(inicio);
  abrir("/");
  expect(await screen.findByRole("heading", { name: "Cómo se calcula" })).toBeInTheDocument();
  expect(screen.getByText(/índice de Jevons/)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /Código y método completo/ })).toHaveAttribute("href", "https://github.com/MurabitoF/precios-super");
});
