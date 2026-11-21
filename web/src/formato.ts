const numero = new Intl.NumberFormat("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const conSigno = new Intl.NumberFormat("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: "exceptZero" });

/** 610000 centavos → "$ 6.100,00". */
export const pesos = (centavos: number) => `$ ${numero.format(centavos / 100)}`;

/** 3 → "+3,00 %", -1.5 → "-1,50 %". */
export const variacion = (v: number) => `${conSigno.format(v).replace("−", "-")} %`;

/** "2026-11-04" → "04/11/2026", sin pasar por Date para no correrla por la zona horaria. */
export const fecha = (iso: string) => iso.split("-").reverse().join("/");
