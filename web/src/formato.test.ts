import { fecha, pesos, variacion } from "./formato";

test("W1 Precio en pesos", () => {
  expect(pesos(610000)).toBe("$ 6.100,00");
});

test("W2 Variación con signo", () => {
  expect([3, -1.5, 0].map(variacion)).toEqual(["+3,00 %", "-1,50 %", "0,00 %"]);
});

test("W3 Fecha dd/mm/aaaa", () => {
  expect(fecha("2026-11-04")).toBe("04/11/2026");
});
