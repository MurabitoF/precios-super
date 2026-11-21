import { useCallback, useEffect, useState } from "react";

export type Estado<T> =
  | { tipo: "cargando" }
  | { tipo: "error"; error: unknown }
  | { tipo: "listo"; datos: T };

/** Corre el pedido al montar y cada vez que cambian sus dependencias; reintentar lo repite. */
export function useApi<T>(pedido: () => Promise<T>, deps: unknown[]): [Estado<T>, () => void] {
  const [estado, setEstado] = useState<Estado<T>>({ tipo: "cargando" });
  const [intento, setIntento] = useState(0);
  useEffect(() => {
    let vigente = true;
    setEstado({ tipo: "cargando" });
    pedido().then(
      (datos) => vigente && setEstado({ tipo: "listo", datos }),
      (error) => vigente && setEstado({ tipo: "error", error }),
    );
    return () => {
      vigente = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, intento]);
  return [estado, useCallback(() => setIntento((i) => i + 1), [])];
}
