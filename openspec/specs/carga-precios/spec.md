# carga-precios Specification

## Purpose

Cargar a una base consultable los precios de los ZIP de SEPA archivados, resumidos a un precio por producto, bandera y semana, de forma que toda la base se pueda rehacer desde el archivo.

## Requirements

### Requirement: Precio semanal por bandera
El sistema SHALL guardar, por cada fecha archivada, un precio por producto y bandera: la mediana del precio de lista entre las sucursales de esa bandera que publican el producto. Con cantidad par de sucursales, la mediana SHALL ser el promedio de los dos precios centrales, redondeado al centavo. El sistema MUST NOT usar los precios de promoción.

#### Scenario: Cantidad impar de sucursales
- **WHEN** un producto aparece en tres sucursales de una bandera con precios de lista 100,00, 300,00 y 120,00
- **THEN** queda una fila para ese producto, bandera y fecha con precio 120,00 y cantidad de sucursales 3

#### Scenario: Cantidad par de sucursales
- **WHEN** un producto aparece en dos sucursales de una bandera con precios de lista 100,00 y 100,01
- **THEN** el precio guardado es 100,01

#### Scenario: Un precio por bandera
- **WHEN** un producto aparece en dos banderas del mismo comercio con precios distintos
- **THEN** queda una fila por bandera, cada una con la mediana de sus propias sucursales

#### Scenario: Precio de promoción ignorado
- **WHEN** una fila trae precio de lista 100,00 y precio de promoción 80,00
- **THEN** el precio que entra al cálculo es 100,00

### Requirement: Filas que entran al cálculo
El sistema SHALL calcular los precios solo con filas de productos identificados por un EAN real y con un precio válido. El sistema MUST descartar las filas cuyo indicador de EAN no es 1, cuyo código no tiene exactamente 13 dígitos, cuyo código empieza con 2, o cuyo precio de lista no es un número mayor a 0. Las líneas que no son filas de datos (pie de archivo, líneas en blanco) MUST ignorarse.

#### Scenario: Código interno del comercio
- **WHEN** una fila trae el indicador de EAN en 0
- **THEN** esa fila no entra al cálculo y, si el producto no tiene otras filas válidas, no queda ningún precio para él

#### Scenario: Código con prefijo 2
- **WHEN** una fila trae un código de 13 dígitos que empieza con 2
- **THEN** esa fila no entra al cálculo

#### Scenario: Código que no tiene 13 dígitos
- **WHEN** una fila trae un código con letras o con una cantidad de dígitos distinta de 13
- **THEN** esa fila no entra al cálculo

#### Scenario: Precio en cero o negativo
- **WHEN** un producto aparece en tres sucursales con precios 0,00, 100,00 y 110,00
- **THEN** el precio guardado es 105,00 y la cantidad de sucursales es 2

#### Scenario: Precio que no es un número
- **WHEN** una fila trae un precio de lista vacío o con texto
- **THEN** esa fila no entra al cálculo y la carga continúa

#### Scenario: Pie y líneas en blanco
- **WHEN** el archivo de productos termina con líneas en blanco y una línea `Ultima actualizacion: ...`
- **THEN** esas líneas se ignoran y la carga termina bien

### Requirement: Formato inesperado detiene la carga
El sistema MUST fallar, sin marcar la fecha como completa, cuando el ZIP no se puede leer, cuando un archivo de productos no trae las columnas esperadas o cuando la carga no produce ningún precio. Un comercio cuyo ZIP interno viene vacío SHALL tolerarse.

#### Scenario: Columnas distintas a las esperadas
- **WHEN** el archivo de productos de un comercio trae una cabecera distinta a la esperada
- **THEN** la carga de esa fecha falla y la fecha no queda completa

#### Scenario: ZIP dañado
- **WHEN** el ZIP de la fecha, o uno de los ZIP internos, está truncado o corrupto
- **THEN** la carga de esa fecha falla y la fecha no queda completa

#### Scenario: Carga sin precios
- **WHEN** ninguna fila del ZIP pasa los filtros
- **THEN** la carga de esa fecha falla y la fecha no queda completa

#### Scenario: Comercio con ZIP vacío
- **WHEN** el ZIP interno de un comercio pesa 0 bytes y los demás son válidos
- **THEN** se cargan los precios de los demás comercios y la fecha queda completa

### Requirement: Catálogo de productos y banderas
El sistema SHALL mantener un catálogo con la descripción y la marca de cada producto cargado, y con el nombre y la razón social de cada bandera. Cuando un producto trae varias combinaciones de descripción y marca en una misma fecha, SHALL quedar la que aparece en más filas; ante un empate, la primera en orden alfabético. El catálogo SHALL reflejar la fecha más reciente en la que aparece cada producto o bandera, sin importar el orden en que se carguen las fechas.

#### Scenario: Producto nuevo
- **WHEN** se carga una fecha con un producto que no estaba en el catálogo
- **THEN** el producto queda en el catálogo con su EAN, descripción y marca

#### Scenario: Varias descripciones para un mismo producto
- **WHEN** un producto aparece con la descripción A en cinco filas y con la descripción B en dos
- **THEN** el catálogo guarda la descripción A

#### Scenario: Empate entre descripciones
- **WHEN** un producto aparece con las descripciones `Gaseosa cola` y `Coca cola` en la misma cantidad de filas
- **THEN** el catálogo guarda `Coca cola`

#### Scenario: Fecha vieja cargada después de una nueva
- **WHEN** el catálogo tiene la descripción de un producto tomada de 2026-11-11 y después se carga 2026-11-04 con otra descripción
- **THEN** el catálogo conserva la descripción de 2026-11-11

#### Scenario: Bandera
- **WHEN** se carga una fecha con un comercio que declara dos banderas
- **THEN** el catálogo tiene las dos banderas, cada una con su nombre y la razón social del comercio

### Requirement: Carga repetible
Cargar de nuevo una fecha SHALL dejar la base como si se la hubiera cargado una sola vez desde el ZIP actual, sin filas duplicadas ni filas sobrantes de la carga anterior.

#### Scenario: Misma fecha cargada dos veces
- **WHEN** se carga una fecha y después se la vuelve a cargar desde el mismo ZIP
- **THEN** los precios de esa fecha son idénticos a los de la primera carga

#### Scenario: Filas de la carga anterior que ya no corresponden
- **WHEN** la base tiene, para una fecha, un precio que la carga actual no produce
- **THEN** después de recargar esa fecha el precio sobrante ya no está

### Requirement: Una fecha cuenta solo cuando está completa
El sistema SHALL registrar por cada fecha si su carga terminó. Una fecha MUST figurar como completa únicamente cuando todos sus precios y su catálogo quedaron guardados, junto con la cantidad de precios cargados. Quien consulte la base SHALL poder distinguir las fechas completas de las que no lo están.

#### Scenario: Carga exitosa
- **WHEN** la carga de una fecha termina sin errores
- **THEN** la fecha figura como completa, con la cantidad de precios guardados

#### Scenario: Carga interrumpida
- **WHEN** la carga de una fecha falla después de haber guardado parte de los precios
- **THEN** la fecha no figura como completa

#### Scenario: Recarga interrumpida
- **WHEN** se recarga una fecha que estaba completa y la recarga falla
- **THEN** la fecha deja de figurar como completa

### Requirement: Puesta al día automática
Una tarea programada SHALL cargar todas las fechas archivadas que no estén completas en la base, de la más antigua a la más reciente. Una fecha que falla MUST NOT impedir la carga de las demás, y la corrida MUST terminar con error si alguna falló.

#### Scenario: Fecha archivada y sin cargar
- **WHEN** la tarea corre y el archivo tiene una fecha que la base no tiene
- **THEN** esa fecha queda cargada y completa

#### Scenario: Varias fechas pendientes
- **WHEN** la tarea corre con tres fechas archivadas y la base vacía
- **THEN** las tres quedan completas, cargadas en orden de fecha

#### Scenario: Nada pendiente
- **WHEN** la tarea corre y todas las fechas archivadas están completas
- **THEN** termina bien y la base no cambia

#### Scenario: Fecha incompleta de una corrida anterior
- **WHEN** la tarea corre y una fecha quedó a medio cargar en una corrida anterior
- **THEN** esa fecha se carga de nuevo y queda completa, sin filas duplicadas

#### Scenario: Una fecha falla y otra no
- **WHEN** la tarea corre con dos fechas pendientes y el ZIP de la más antigua está dañado
- **THEN** la más reciente queda completa, la dañada no, y la corrida termina con error

### Requirement: Recarga a pedido
El sistema SHALL permitir pedir la carga de una fecha puntual, aunque ya esté completa.

#### Scenario: Recarga de una fecha completa
- **WHEN** se pide cargar una fecha que ya está completa
- **THEN** la fecha se carga de nuevo desde su ZIP y vuelve a quedar completa

#### Scenario: Fecha que no está en el archivo
- **WHEN** se pide cargar una fecha para la que no hay ZIP archivado
- **THEN** la corrida falla y la base no cambia

### Requirement: Acceso mínimo y credenciales acotadas
La tarea de carga SHALL leer el archivo con una identidad temporal emitida para esa corrida, sin claves de AWS guardadas, y esa identidad MUST NOT poder escribir ni borrar en el archivo. El acceso a la base SHALL usar un token válido solo para esa base, guardado cifrado como secreto del repositorio y entregado a la tarea como variable de entorno; el token MUST NOT aparecer en el código ni en archivos del repositorio.

#### Scenario: Conexión con la identidad de la corrida
- **WHEN** la tarea corre desde la rama principal del repositorio
- **THEN** lee el archivo sin ninguna clave de AWS guardada, y la única credencial guardada es el token de la base, como secreto del repositorio

#### Scenario: El cargador no puede modificar el archivo
- **WHEN** se intenta escribir o borrar un objeto del archivo con la identidad de la tarea de carga
- **THEN** el pedido es rechazado
