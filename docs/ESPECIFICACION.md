# Especificación de Producto y Desarrollo v2.4

Aplicación Android de gestión para el emprendimiento familiar de huevos

Nov 16, 2026 · @Miguel

## 0. Cómo usar este documento con la IA

Este documento es la única fuente de verdad del proyecto: si algo no está escrito aquí, la IA debe preguntar en lugar de suponer.

**Contexto en una línea:** app Android (Kotlin + Jetpack Compose) con Firebase Authentication y Cloud Firestore, para que hasta tres familiares registren recolección, ventas, pagos, alimento, gastos y cierres de un emprendimiento de huevos, con funcionamiento sin internet y exportación CSV.

**Orden de lectura:** secciones 4, 5, 8 y 9 (reglas, decisiones, datos y cálculos) antes de escribir código; secciones 6 y 13 para saber qué construir y cómo se valida.

### Convenciones fijas

- Código, nombres de colecciones, campos y valores de enumeraciones en inglés (camelCase); textos de la interfaz en español de Colombia.
- Dinero: pesos colombianos (COP) como entero (`Long`); nunca `Float` ni `Double`. En pantalla: `$ 1.234.567`.
- Fechas: `fechaOperativa` es un `String` `YYYY-MM-DD` en zona `America/Bogota`; `createdAt` es el `Timestamp` del dispositivo; `syncedAt` es `serverTimestamp`. `startDate` (negocio) y `validFrom` (precios) también son `String` `YYYY-MM-DD`; `deliveredAt` es `Timestamp`.
- Periodos: todo periodo cuenta ambos extremos (inclusivo). Los días de calendario se cuentan en zona `America/Bogota`.
- Constantes en código (no configurables): 30 huevos por cubeta, 40 kg por bulto, clasificaciones `B`, `A`, `AA`, `AAA`, `JUMBO`, ingredientes `concentrate` y `corn_bran`.
- Los IDs se generan en el dispositivo (`collection.document().id`) antes de guardar.
- Hay un solo negocio: su `businessId` es fijo en la configuración de compilación (`BuildConfig`), sección 10.1.

### Reglas de trabajo para la IA

1. Construir por rebanadas verticales (pantalla, ViewModel, caso de uso, repositorio y pruebas), una por cada requisito RF.
2. Escribir primero las pruebas de la sección 13 para la lógica de negocio. El módulo `domain` es Kotlin puro, sin Android ni Firebase.
3. Los movimientos confirmados son inmutables: solo se crean. Una corrección es un documento en `reversals`.
4. Nunca guardar un saldo como fuente de verdad; se calcula según la sección 9.
5. No esperar (`await`) el resultado de una escritura de Firestore en la interfaz: sin internet esa tarea no termina. La UI lee el estado local y muestra «pendiente de sincronizar». Única excepción: la apertura (§5.1). Tampoco se espera su tarea, pero la app observa en los documentos de la apertura que `hasPendingWrites` sea falso y, hasta entonces, muestra un indicador de progreso y bloquea los demás movimientos.
6. Las operaciones de varios documentos usan `WriteBatch` (funciona sin conexión). No usar transacciones.
7. Sin Cloud Functions, sin servidor propio, sin plan de pago de Firebase y sin SMS.
8. Ninguna credencial de administrador ni clave de servicio dentro de la app ni del repositorio.
9. Todo cambio de reglas de Firestore se prueba con Firebase Emulator Suite antes de desplegarlo.
10. Ante ambigüedad o contradicción entre secciones, detenerse y preguntar; no inventar campos ni reglas.

### Definición de terminado (por cada RF)

Código y pruebas escritos, criterios AC relacionados en verde, reglas de seguridad probadas si toca datos, y funcionamiento verificado en modo avión.

## 1. Resumen ejecutivo y decisiones confirmadas

La app registra cada movimiento del emprendimiento una sola vez (recolección, venta, pago, compra, gasto, agotamiento, conteo) y a partir de ese historial calcula existencias, saldos de clientes y caja. No es un sistema contable ni calcula utilidad contable: su resultado es un historial claro y las diferencias entre lo calculado y lo contado físicamente.

La operación cría gallinas, recoge y clasifica huevos, vende cubetas completas y hace domicilios sin cobro adicional. La app reemplaza gradualmente los registros en papel.

| Control del documento | Detalle |
| --- | --- |
| Versión | 2.4 (variante B en reglas, presupuesto de accesos y abono en lotes de hasta 10 pagos) |
| Fecha | 19 de noviembre de 2026 |
| Estado | En desarrollo (Fase 1 en ejecución). Las decisiones «Propuesta» de la sección 5 se usan tal como están; no quedan decisiones de producto pendientes (§16.2) |
| Plataforma | Android |
| Usuarios | Hasta tres integrantes de la familia, un dispositivo cada uno |
| Criterio rector | Registrar cada movimiento una sola vez, con formularios sencillos, historial trazable y sincronización posterior cuando el teléfono recupere internet |

### Decisiones operativas confirmadas

- La única presentación de venta es la cubeta completa de 30 huevos, de una sola clasificación (B, A, AA, AAA o Jumbo). Una venta puede tener varias líneas de distinta clasificación, pero no existen cubetas mixtas, docenas ni medias cubetas.
- El precio cambia ocasionalmente (normalmente por el costo del concentrado). Cada venta conserva el precio realmente aplicado.
- El alimento controlado es concentrado y salvado de maíz; cada bulto de cualquiera de los dos equivale a 40 kg.
- El consumo de alimento no se registra a diario: se marca cada bulto cuando se agota y se hacen conteos de ajuste.
- La usan principalmente la madre, el desarrollador y su hermano, en máximo tres dispositivos Android.
- Se busca una app propia, con costo mensual de mantenimiento nulo o mínimo; el desarrollador mantiene el código.

### Resultado esperado

La app permite registrar recolección, ventas, pagos parciales, devoluciones, compras, gastos, retiros, agotamientos y cierres con conteos físicos. Los tres familiares consultan datos sincronizados y exportan archivos CSV para analizar en Google Sheets.

## 2. Objetivos y alcance

El MVP se entrega en tres rebanadas funcionales para poder usarlo con datos reales desde la primera, en lugar de esperar a tener todo terminado.

### 2.1 Objetivos

- Conservar el historial de recolecciones, ventas, pagos, devoluciones, compras, agotamientos, gastos, retiros y conteos.
- Calcular existencias esperadas de huevos por clasificación, alimento por ingrediente y caja por forma de pago.
- Comparar saldos calculados con conteos reales y dejar visibles las diferencias.
- Mostrar ventas pendientes de pago, entregas pendientes y saldo por cliente.
- Separar el flujo de caja de una estimación aproximada de resultado operativo.
- Permitir captura sin conexión y sincronización posterior, con estado visible de lo pendiente.
- Exportar información en CSV para Google Sheets y servir de respaldo de consulta.

### 2.2 Alcance incluido

- Inicio de sesión individual para hasta tres familiares, con dos roles (Administrador y Operador).
- Saldos iniciales (apertura), recolección, descartes, ventas, clientes, pagos y abonos, devoluciones, consumo propio, obsequios y pérdidas de huevos.
- Compras y agotamientos de alimento, pérdidas de alimento, gastos, retiros personales, caja, conteos y cierres.
- Historial con reversos, alertas, reportes básicos por periodo y exportación CSV.

### 2.3 Fuera de alcance de la versión 1

- Contabilidad de doble partida, declaraciones tributarias, facturación electrónica o certificación contable.
- Optimización de rutas, geolocalización o cobro de domicilio.
- Inventario individual de gallinas, genética o postura por ave.
- Sincronización automática con Google Sheets; se comparte CSV.
- Servidor propio (Spring Boot u otro) y cualquier servicio de pago.
- Compras de alimento a crédito con proveedor (toda compra se paga al registrarla).
- Saldo a favor del cliente: no existe como saldo persistente. Un pago nunca excede el saldo de la venta y, si el crédito de una devolución supera lo pendiente de esa venta, el excedente se reintegra obligatoriamente en el mismo momento (R-09).
- Fotos de recibos (RF-27) y rol de solo consulta (RF-29), que quedan como requisitos posteriores, igual que RF-28 y RF-30.
- Notificaciones push: fuera de alcance y sin requisito asociado; los avisos se muestran dentro de la app (RF-23, RF-31).

### 2.4 MVP por rebanadas

| Rebanada | Contenido | Resultado utilizable |
| --- | --- | --- |
| A. Huevos y cobros | Acceso, catálogos, apertura, recolección, venta, entrega, pagos, clientes, saldo de huevos, conteo de huevos | La familia registra producción y ventas del día y sabe cuánto le deben |
| B. Insumos y caja | Compras y agotamientos de alimento, gastos, retiros, devoluciones, consumo propio, conteos de alimento y caja, cierre | Alimento y dinero se concilian |
| C. Confiabilidad y análisis | Offline probado a fondo, aviso de escrituras rechazadas (RF-31), alertas, reportes, CSV, respaldo, piloto | Se revisa la semana y el mes en Sheets con datos confiables |

## 3. Usuarios y permisos

La versión 1 usa solo dos roles, `admin` y `operator`, porque con tres personas un tercer rol añade reglas y pantallas sin beneficio claro. El rol de solo consulta queda como RF-29 posterior.

Cada persona inicia sesión con su propia cuenta (correo y contraseña) para que cada movimiento quede asociado a quien lo registró. No se usa una cuenta compartida.

| Acción | Administrador | Operador |
| --- | --- | --- |
| Consultar movimientos, saldos, reportes y alertas | Sí | Sí |
| Registrar recolección, ventas, pagos, devoluciones, egresos de huevos | Sí | Sí |
| Registrar compras, agotamientos, pérdidas de alimento, gastos y retiros | Sí | Sí |
| Hacer conteos físicos y cierres | Sí | Sí |
| Crear y editar clientes | Sí | Sí |
| Ver teléfono de clientes | Sí | Sí |
| Exportar CSV por archivo y periodo | Sí | Sí |
| Exportación completa de respaldo y aviso A-09 (RF-26) | Sí | No |
| Anular movimientos (reversos con motivo). Conteos y cierres no se anulan; la apertura solo antes del primer movimiento posterior (R-18, §5.1) | Sí | No |
| Registrar la apertura (saldos iniciales) | Sí | No |
| Cambiar precios, categorías de gasto y formas de pago | Sí | No |
| Desactivar clientes | Sí | No |

### Alta y baja de usuarios

En la versión 1 el desarrollador crea las tres cuentas en Firebase Authentication y el documento de cada miembro en `businesses/{businessId}/members/{uid}` desde la consola de Firebase. No hay pantalla de gestión de usuarios (se deja como RF-30). Para dar de baja a alguien se pone `active: false`.

Los perfiles se asignan según las tareas reales de la familia (D-09): el desarrollador es Administrador; la madre y el hermano son Operadores. Solo el administrador anula movimientos hasta el final del piloto (D-10).

## 4. Reglas de negocio

Estas reglas son obligatorias y deben quedar cubiertas por pruebas, salvo las marcadas como «acuerdo no automatizable», que se cumplen por diseño o por acuerdo familiar y quedan fuera de la tabla de pruebas de la sección 13 (ver 13.0). Cada una tiene un código (R-xx) para citarla en tareas y pruebas.

### Huevos y ventas

| Código | Regla |
| --- | --- |
| R-01 | Una cubeta equivale exactamente a 30 huevos de una sola clasificación. Solo se venden cubetas enteras. |
| R-02 | Clasificaciones permitidas: B, A, AA, AAA y JUMBO. |
| R-03 | Una venta tiene una o más líneas. Cada línea tiene clasificación, cantidad entera de cubetas (mínimo 1), precio de lista, precio aplicado (mayor que 0) y subtotal. El total es la suma de los subtotales. |
| R-04 | El precio de lista sale del catálogo vigente de esa clasificación en la `fechaOperativa` de la venta: el de mayor `validFrom` menor o igual a esa fecha y, si hay dos con el mismo `validFrom`, el de mayor `createdAt`. Si la clasificación no tiene precio vigente, no se permite vender esa clasificación. El operador puede cambiar el precio aplicado en la venta; la línea guarda ambos valores. Cambiar el catálogo nunca modifica ventas anteriores. |
| R-05 | La venta descuenta los huevos del saldo disponible al confirmarse, aunque la entrega esté pendiente. Los huevos de ventas pendientes de entrega se muestran como «por entregar» y el conteo físico se compara con disponible más por entregar (sección 9). Una venta creada como `delivered` lleva `deliveredAt` igual a su `createdAt` y `deliveredBy` igual a su `createdBy`. |
| R-06 | El domicilio es gratuito para el cliente. El combustible se registra como gasto operativo y no genera ningún cargo. *Acuerdo no automatizable* (no hay campo de cargo de domicilio que probar). |

### Pagos, clientes y devoluciones

| Código | Regla |
| --- | --- |
| R-07 | Todo pago se asocia a una venta o a una deuda de apertura. Su monto es mayor que 0 y no supera el saldo pendiente de esa venta o deuda (no existe saldo a favor). Un abono general de un cliente se reparte de la deuda más antigua a la más reciente: primero las deudas de apertura y luego las ventas, ordenadas por `fechaOperativa` y, a igual fecha, por `createdAt`. El rechazo por exceder la deuda total del cliente se comprueba antes de enviar el primer lote. Se crea un pago por cada deuda cubierta, repartido en uno o varios `WriteBatch` atómicos de hasta 10 pagos cada uno (§10.2); si un lote posterior falla, los anteriores permanecen, se muestra el saldo restante y el usuario puede repetir el abono por el resto. La app garantiza que `PendienteVenta` no quede negativo al confirmar en un mismo dispositivo; si por concurrencia sin conexión queda negativo, se dispara A-10 y el administrador lo corrige con un reintegro por sobrepago (`type: overpayment_refund`), con motivo obligatorio y sin `saleReturnId` (§8.3). |
| R-08 | Una venta sin cliente registrado debe quedar pagada por completo al confirmarla. Una venta con saldo pendiente exige cliente. |
| R-09 | Una devolución solo aplica a cubetas de una venta ya entregada, indicando clasificación y cantidad de cubetas (sin superar lo vendido menos lo ya devuelto). Genera un crédito igual a cubetas por precio aplicado. El usuario indica si los huevos reingresan al inventario o se pierden. Si el crédito supera lo pendiente de esa venta, el excedente se reintegra obligatoriamente con un pago `type: refund` sobre esa venta (`saleId`) y con `saleReturnId` igual al ID de la devolución, en el mismo `WriteBatch` que la devolución. El tipo `refund` solo nace de una devolución; los sobrepagos se corrigen con `overpayment_refund` (R-07). El crédito nunca se aplica a otras ventas del cliente y `PendienteVenta` nunca queda negativo al confirmar (§9.4). |

### Recolección, egresos y conteos de huevos

| Código | Regla |
| --- | --- |
| R-10 | Una recolección registra huevos aptos por clasificación y huevos descartados como un total general (rotos o no aptos, sin clasificar). Los descartados de la recolección son solo informativos: nunca entran al inventario ni lo restan. |
| R-11 | Los huevos que salen sin venta (consumo propio, obsequio o pérdida posterior, como roturas en almacenamiento) se registran por clasificación en `eggOutflows`. La pérdida exige motivo. |
| R-12 | Un conteo físico es el punto de partida del saldo de cada elemento contado. Se permiten conteos parciales: una clave ausente en `items` significa «no contada» y su punto de partida no cambia. Los huevos se cuentan siempre en huevos (la interfaz acepta cubetas y sueltos y los convierte). Cada ítem con diferencia distinta de cero exige su propio motivo (`reason`). Un conteo no se edita ni se anula; se corrige con otro conteo. |

### Alimento, gastos y caja

| Código | Regla |
| --- | --- |
| R-13 | Ingredientes controlados: concentrado (`concentrate`) y salvado de maíz (`corn_bran`). Cada bulto son 40 kg. La compra suma bultos por 40 kg; el agotamiento resta bultos por 40 kg. |
| R-14 | Las compras de alimento se registran únicamente en `feedPurchases`, nunca como gasto, para no contarlas dos veces. Las categorías de gasto son combustible, empaques, medicamentos, mantenimiento, otros y las que el administrador agregue. |
| R-15 | Toda compra y todo gasto se paga al registrarlo y exige forma de pago. La caja se calcula por forma de pago. |
| R-16 | Los retiros personales se registran en `withdrawals`, restan caja y no cuentan como gasto operativo ni afectan el resultado aproximado. |
| R-17 | El consumo diario de alimento es una estimación: kg agotados del periodo entre los días del periodo. Mientras haya un bulto abierto sin agotar, el saldo en kg es aproximado hasta el siguiente conteo. *Acuerdo no automatizable* en su parte informativa (saldo aproximado); la fórmula se prueba en AC-16. |

### Integridad y trazabilidad

| Código | Regla |
| --- | --- |
| R-18 | Un movimiento confirmado no se edita ni se elimina. Solo se corrige con un documento en `reversals` (con motivo, solo administrador). La única excepción es el paso de entrega de una venta de `pending` a `delivered`; ese paso no se deshace en la v1: si se marcó por error, el siguiente conteo lo corrige. Conteos (`counts`) y cierres (`closings`) no se anulan: se corrigen con otro conteo. La apertura solo se anula antes del primer movimiento posterior (§5.1). Anular significa que el movimiento nunca ocurrió (error de digitación). |
| R-19 | No se puede anular una venta que tenga pagos o devoluciones activos; primero se anulan estos (el reverso de un pago reduce la caja y debe tener motivo). Una devolución con reintegro activo (`type: refund`) se anula junto con su reintegro: los dos reversos van en un solo `WriteBatch`. No se puede anular un `refund` por separado mientras su devolución esté activa. Un reintegro por sobrepago (`type: overpayment_refund`) se anula por sí solo. No se puede anular una deuda de apertura con pagos activos hasta anular los pagos. No se puede anular un reverso. Anular una venta entregada hace que sus huevos dejen de contarse como entregados; si los huevos sí salieron, no se anula: se usa devolución (R-09) o egreso de huevos (R-11). |
| R-20 | El estado «borrador» existe solo en el formulario local. Todo documento guardado es confirmado; está anulado si existe un reverso que lo referencia. «Pendiente de sincronizar» es un estado derivado de Firestore, no un campo guardado. |
| R-21 | Un movimiento entra al saldo de una clave solo si su `createdAt` es posterior al del punto de partida y, además, su `fechaOperativa` es mayor que la del punto de partida si este es un cierre (`isClosing: true`), o mayor o igual si es un conteo intermedio o la apertura. Única excepción: el efecto de una entrega sobre Físico se decide solo con `deliveredAt` posterior al `createdAt` del punto de partida, sin comparar fechas, porque la entrega es un hecho físico con hora real; una entrega nunca es registro tardío. Si el `createdAt` es posterior pero la fecha no cumple, es un registro tardío: se excluye del saldo, porque el conteo ya lo refleja, y genera la alerta A-08. Si la fecha estaba mal puesta, el administrador lo anula y lo registra de nuevo. Cuando la `fechaOperativa` del día ya tiene cierre, la app propone por defecto el día siguiente para los movimientos nuevos y avisa (también para una venta creada como `delivered`, cuya entrega cuenta igualmente por `deliveredAt`); si el usuario fuerza la fecha cerrada, el movimiento queda como registro tardío. |
| R-22 | `createdAt` sale del reloj del dispositivo y es el que ordena los movimientos contra los conteos. Las reglas de Firestore rechazan un `createdAt` mayor que `request.time` más 5 minutos. |

## 5. Decisiones cerradas y saldos iniciales

Esta sección resuelve los puntos que la versión 1.0 dejaba abiertos. Las decisiones «Propuesta» son valores razonables por defecto: la IA debe construir con ellas, y la familia puede ajustarlas durante el piloto actualizando primero este documento.

| ID | Tema | Decisión | Estado |
| --- | --- | --- | --- |
| D-01 | Moneda | Pesos colombianos (COP), enteros, sin decimales | Cerrada |
| D-02 | Precios | Un precio por clasificación con fecha de vigencia. Solo el administrador cambia el catálogo; el operador puede ajustar el precio aplicado en una venta concreta | Propuesta |
| D-03 | Categorías de gasto | Combustible, empaques, medicamentos, mantenimiento y otros. El administrador puede añadir. Concentrado y salvado no son categorías de gasto (R-14) | Propuesta |
| D-04 | Formas de pago | Efectivo, Nequi, Daviplata y transferencia bancaria. El administrador puede añadir o desactivar. Desactivar solo oculta la forma de pago en registros nuevos: su caja se sigue calculando y aparece en el cierre mientras su saldo sea distinto de cero | Propuesta |
| D-05 | Descartes | Total general en la recolección, sin clasificar. Pérdidas posteriores se registran por clasificación (R-10, R-11) | Cerrada |
| D-06 | Ventas simultáneas | Con internet pueden vender varios a la vez. Sin internet, solo un dispositivo designado registra ventas de huevos. Es un acuerdo operativo de la familia, sin campo ni control en la app | Cerrada (acuerdo no automatizable) |
| D-07 | Frecuencia de conteos | Huevos y caja al cierre de cada día con ventas (mínimo dos veces por semana); alimento cada semana | Propuesta |
| D-08 | Teléfono del cliente | Opcional; lo ven administrador y operador; no se incluye en los CSV por defecto | Propuesta |
| D-09 | Asignación de roles | Desarrollador: administrador. Madre y hermano: operadores | Cerrada |
| D-10 | Anulaciones por operadores | Solo el administrador anula hasta el final del piloto; al terminar el piloto se reevalúa si el operador puede anular sus propios registros del mismo día | Cerrada (reevaluar al final del piloto) |
| D-11 | Entrega e inventario | La venta descuenta al confirmarse; lo pendiente de entrega se muestra como «por entregar» (R-05) | Cerrada |
| D-12 | Alerta de cartera | Venta con saldo pendiente por más de 15 días | Propuesta |
| D-13 | Android mínimo | API 26 (Android 8.0), ajustable al teléfono más antiguo de la familia | Propuesta |
| D-14 | Inyección de dependencias | Hilt | Cerrada |
| D-15 | Identificador del negocio | Un solo negocio; `businessId` fijo en `BuildConfig` (sección 10.1) | Cerrada |

### 5.1 Apertura: saldos iniciales

Antes de usar la app hay que registrar desde dónde se parte; sin esto los saldos esperados no coinciden con la realidad desde el primer día. Lo hace el administrador una sola vez, en una pantalla guiada en Ajustes, el mismo día en que empieza el uso y antes de registrar cualquier otro movimiento.

| Qué se cuenta | Cómo | Se guarda en `openingBalances` como |
| --- | --- | --- |
| Huevos por clasificación | Conteo físico en huevos (la pantalla acepta cubetas y sueltos y los convierte) | `kind: eggs`, `key`: clasificación, `quantity`: huevos |
| Alimento por ingrediente | Kilos estimados, incluyendo el bulto abierto | `kind: feed`, `key`: ingrediente, `quantity`: kg |
| Dinero por forma de pago | Efectivo contado y saldos de billeteras o bancos | `kind: cash`, `key`: id de forma de pago, `quantity`: pesos |
| Deudas de clientes | Una deuda por cliente, tomada del cuaderno | `kind: customerDebt`, `key`: id de cliente, `quantity`: pesos |

Reglas de la apertura:

- Cada elemento de la apertura funciona como punto de partida del saldo (sección 9), con la regla de inclusión de un conteo intermedio (R-21).
- Los pagos de una deuda de apertura se asocian a ella (`openingDebtId`) con las mismas reglas que un pago de venta (R-07).
- La apertura se registra con internet. Es la única excepción a la regla de no esperar escrituras (§0, regla 5; §10.2.3): no se espera la tarea de Firestore, sino que la app observa con un listener de metadatos los documentos de `openingBalances`.
- Cada teléfono, incluido el que la registra, bloquea el registro de movimientos con el aviso «Esperando apertura» mientras no vea una apertura confirmada por el servidor: un documento de `openingBalances` activo (sin reverso) recibido con `metadata.isFromCache == false` y `hasPendingWrites == false`. En el teléfono que la registra se muestra además un indicador de progreso.
- Si la escritura de la apertura se rechaza al sincronizar, el bloqueo deja de esperar: muestra el error de RF-31 y permite reintentar o corregir la apertura.
- Una apertura confirmada no se edita. Solo se puede anular (reverso, solo administrador) antes del primer movimiento posterior; después, los errores de apertura se corrigen con un conteo (R-12). Crear precios o catálogos no cuenta como movimiento posterior. Una deuda de apertura con pagos activos no se anula hasta anular esos pagos (R-19).
- La fecha de inicio de uso se guarda en el documento del negocio (`startDate`, `String` `YYYY-MM-DD`); no se aceptan movimientos con `fechaOperativa` anterior. Lo valida la app y, si la medición de la Fase 1 lo permite, también las reglas (§11.1). Los precios no tienen `fechaOperativa` y no están sujetos a esta restricción.

## 6. Requisitos funcionales

Cada requisito tiene un ID para citarlo en tareas, pruebas y mensajes de commit. «Obligatorio» significa incluido en el MVP; «Posterior» se añade después del piloto. La columna Rebanada indica en cuál de las tres entregas de la sección 2.4 se construye.

| ID | Función | Prioridad | Rebanada | Descripción |
| --- | --- | --- | --- | --- |
| RF-01 | Acceso individual | Obligatorio | A | Inicio de sesión por persona; solo entran miembros activos del negocio |
| RF-02 | Estado de sincronización | Obligatorio | A (básico), C (completo) | Mostrar conexión, el aviso «hay cambios pendientes» (sí/no, no una cantidad exacta) y la última sincronización confirmada, guardada localmente en el teléfono (§10.2); avisar antes de cerrar sesión con pendientes |
| RF-03 | Apertura de saldos | Obligatorio | A | Registrar saldos iniciales de huevos, alimento, caja y deudas de clientes (sección 5.1) |
| RF-04 | Catálogos | Obligatorio | A | El administrador gestiona precios con vigencia (solo se crean, nunca se editan; R-04), categorías de gasto y formas de pago |
| RF-05 | Alta de usuarios | Obligatorio | A | Manual desde la consola de Firebase, documentada; sin pantalla en el MVP |
| RF-06 | Recolección | Obligatorio | A | Fecha, huevos aptos por clasificación y total de descartados (R-10). El responsable es quien registra (`createdBy`) |
| RF-07 | Venta | Obligatorio | A | Cliente opcional y líneas de cubetas por clasificación, con precio de lista y precio aplicado (R-03, R-04, R-08) |
| RF-08 | Entrega | Obligatorio | A | Marcar la venta como pendiente o entregada; el paso a entregada se puede hacer después |
| RF-09 | Pagos y abonos | Obligatorio | A | Pago total o parcial por venta o abono general por cliente, con forma de pago y fecha (R-07) |
| RF-10 | Clientes | Obligatorio | A | Crear, consultar y desactivar clientes; mostrar ventas, pagos, devoluciones y saldo |
| RF-11 | Saldo de huevos | Obligatorio | A | Disponible, por entregar y físico esperado por clasificación (sección 9) |
| RF-12 | Conteo de huevos | Obligatorio | A | Comparar conteo real con físico esperado; la diferencia exige motivo (R-12) |
| RF-13 | Devolución de cubetas | Obligatorio | B | Devolución sobre venta entregada, con crédito y, si el crédito supera lo pendiente de esa venta, reintegro obligatorio del excedente en el mismo `WriteBatch` (R-09) |
| RF-14 | Egresos de huevos | Obligatorio | B | Consumo propio, obsequio y pérdida por clasificación (R-11) |
| RF-15 | Compra de alimento | Obligatorio | B | Ingrediente, bultos de 40 kg, costo, fecha y forma de pago |
| RF-16 | Agotamiento de bultos | Obligatorio | B | Marcar bultos agotados; descuenta 40 kg por bulto |
| RF-17 | Pérdida y conteo de alimento | Obligatorio | B | Registrar desperdicio o pérdida en kg y conteos de alimento sin borrar movimientos anteriores |
| RF-18 | Gastos | Obligatorio | B | Valor, categoría, fecha, forma de pago y descripción opcional. El responsable es quien registra (`createdBy`) |
| RF-19 | Retiros personales | Obligatorio | B | Registro aparte de los gastos operativos (R-16) |
| RF-20 | Conteo de caja | Obligatorio | B | Contar dinero por forma de pago y comparar con el saldo esperado |
| RF-21 | Cierre de jornada | Obligatorio | B | Resumen del día con ventas, cobros, gastos, conteos y diferencias |
| RF-22 | Historial y reversos | Obligatorio | B | Consultar por fecha y tipo; el administrador anula con motivo (R-18, R-19) |
| RF-23 | Alertas | Obligatorio | C | Definidas en la sección 9.5 |
| RF-24 | Reportes | Obligatorio | C | Resúmenes por fecha, clasificación, ingrediente, categoría y cliente (sección 9.6) |
| RF-25 | Exportación CSV | Obligatorio | C | Archivos definidos en la sección 9.7 |
| RF-26 | Recordatorio de respaldo | Obligatorio | C | Aviso en Inicio (A-09), solo para el administrador, si pasaron más de 30 días desde la última exportación completa hecha en su teléfono. La exportación completa incluye todos los CSV de la sección 9.7 (movimientos, catálogos, apertura y cierres); su fecha se guarda localmente en el teléfono |
| RF-27 | Recibos con imagen | Posterior |  | Adjuntar fotos de recibos si el piloto demuestra su valor |
| RF-28 | Gestión por lote o ave | Posterior |  | Postura, mortalidad o datos de lotes |
| RF-29 | Rol de solo consulta | Posterior |  | Tercer rol que no modifica datos |
| RF-30 | Gestión de usuarios en la app | Posterior |  | Solicitud de acceso y aprobación por el administrador |
| RF-31 | Aviso de escritura rechazada | Obligatorio | C (el caso de la apertura, en A) | Cuando una escritura pendiente es rechazada al sincronizar (por ejemplo, miembro desactivado o regla de seguridad incumplida), avisar al usuario indicando qué movimiento no se guardó, sin bloquear la interfaz (§10.2). Si lo rechazado es la apertura, el bloqueo «Esperando apertura» se levanta, se muestra el error y se permite reintentar o corregir (§5.1); esta parte se construye con la apertura, en la rebanada A |

## 7. Pantallas y flujos de uso

Las acciones frecuentes deben quedar a uno o dos toques desde Inicio y los formularios deben poder llenarse con una mano. Principios de interfaz: botones y tipografía grandes, valores por defecto inteligentes (fecha de hoy o, si hoy ya tiene cierre, el día siguiente con aviso, R-21; usuario actual; último método de pago activo), teclado numérico para cantidades y confirmación con resumen antes de guardar.

### 7.1 Inicio (Hoy)

- Fecha y resumen del día: ventas, cobros, gastos y huevos recogidos.
- Acciones principales: Registrar recolección, Registrar venta, Registrar gasto, Marcar bulto agotado, Hacer cierre.
- Estado de sincronización: conectado o sin conexión, aviso «hay cambios pendientes» y última sincronización confirmada (RF-02).
- Avisos con el código de la sección 9.5 que se muestran en Inicio: A-01 y A-02 (inventario negativo), A-04 (conteo atrasado), A-05 (cartera vencida), A-06 (entregas pendientes), A-07 (pendientes sin sincronizar), A-09 (recordatorio de respaldo, solo administrador) y A-10 (pago que excede el saldo, pago sobre venta anulada o entrega sobre venta anulada). Los registros tardíos (A-08) se ven en Historial.
- Aviso de escrituras rechazadas al sincronizar (RF-31).
- Bloqueo «Esperando apertura» mientras no haya una apertura confirmada por el servidor (§5.1).
- Aviso cuando un reporte se muestra desde caché y puede estar incompleto.

### 7.2 Recolección

1. Confirmar la fecha (por defecto, hoy; si hoy ya tiene cierre, el día siguiente con aviso, R-21).
2. Ingresar huevos aptos por cada clasificación (B, A, AA, AAA, JUMBO) y el total de huevos rotos o no aptos.
3. Revisar el resumen; los descartados quedan como pérdida informativa y no como inventario.
4. Guardar: se crea el documento con ID, usuario, fecha y estado de sincronización.

### 7.3 Venta

1. Seleccionar o crear cliente, o dejarlo vacío (la venta debe quedar pagada completa, R-08).
2. Agregar líneas: clasificación, cubetas enteras (mínimo 1) y precio por cubeta, precargado del precio vigente en la `fechaOperativa` de la venta (R-04) y editable. Una clasificación sin precio vigente no se puede agregar.
3. Ver total y equivalencia en huevos (cubetas por 30).
4. Marcar la entrega como completada o pendiente.
5. Registrar lo recibido y la forma de pago; se muestra el saldo pendiente.
6. Confirmar: el saldo de huevos y el saldo del cliente se actualizan.

Validaciones: cubetas enteras mayores a 0, precio mayor a 0, un pago nunca mayor al total, aviso si la venta deja el disponible de una clasificación en negativo (se permite guardar y queda alerta).

### 7.4 Cobrar y clientes

- Lista de clientes con saldo; al abrir uno se ven sus ventas, pagos, devoluciones y saldo.
- Registrar pago de una venta o de una deuda de apertura, o abono general que se reparte automáticamente (R-07), mostrando el reparto antes de confirmar; si el abono excede la deuda total, no se permite confirmar (se rechaza antes de enviar el primer lote). Si el abono supera 10 deudas se envía en lotes de hasta 10 pagos; si un lote posterior falla, los anteriores permanecen, la pantalla muestra el saldo restante y el usuario puede repetir el abono por el resto.
- Crear, editar y (administrador) desactivar clientes; teléfono opcional.
- Lista de entregas pendientes con botón para marcar como entregada.
- (Administrador) Reintegro por sobrepago sobre una venta o deuda de apertura con A-10: monto, forma de pago y motivo obligatorio (R-07).

### 7.5 Devolución y otros egresos de huevos

- Devolución: elegir la venta entregada, clasificación y cubetas; indicar si reingresan al inventario o se pierden; mostrar el crédito y, si supera lo pendiente de la venta, el reintegro obligatorio del excedente con su forma de pago, que se guarda en el mismo lote (R-09).
- Consumo propio, obsequio o pérdida: elegir clasificación y cantidad de huevos; la pérdida exige motivo.

### 7.6 Alimento

- Compra: ingrediente, bultos, costo total, forma de pago y fecha; muestra los kg que suma (bultos por 40).
- Marcar bulto agotado: ingrediente y cantidad de bultos; muestra el saldo esperado y el consumo diario estimado (con un decimal, §9.3).
- Pérdida o desperdicio: ingrediente, kg y motivo.
- Conteo de alimento: kilos contados por ingrediente, incluyendo el bulto abierto.

### 7.7 Gastos y retiros

- Gasto: valor, categoría, forma de pago, fecha, descripción opcional.
- Retiro personal: pantalla separada (valor, forma de pago, nota) para que no se mezcle con gastos operativos.

### 7.8 Conteo y cierre

1. El cierre muestra, por elemento, el valor esperado y pide el contado: huevos por clasificación (en huevos; acepta cubetas y sueltos), dinero por forma de pago (incluidas las desactivadas mientras su saldo sea distinto de cero, D-04) y, si corresponde, alimento por ingrediente. Un elemento que no se cuenta queda fuera del conteo y conserva su punto de partida (R-12).
2. Cada elemento con diferencia distinta de cero exige su propio motivo antes de confirmar.
3. Se guarda el conteo como nuevo punto de partida y un resumen del día (ventas, cobros, gastos, retiros, diferencias). Un cierre no se anula; si tuvo un error, se corrige con otro conteo.
4. Se permite más de un cierre en el mismo día; el último conteo que incluye cada clave (por `createdAt`) es su punto de partida (§9.1).

### 7.9 Historial y reversos

Lista filtrable por fecha, tipo, usuario y estado, con cada movimiento marcado como activo o anulado y si está pendiente de sincronizar. Aquí se ven A-03, A-08 y A-10. El administrador puede anular un movimiento con motivo obligatorio; la pantalla explica qué otros movimientos deben anularse antes (R-19) y, al anular una devolución con reintegro, anula ambos en un solo lote. Conteos y cierres no muestran la opción de anular (R-18).

### 7.10 Reportes y exportación

Reportes de la sección 9.6 con filtros por periodo; botón Exportar CSV para cada archivo de la sección 9.7, compartido mediante el selector de archivos del sistema (sin permisos de almacenamiento).

### 7.11 Ajustes (administrador)

Apertura de saldos (5.1), precios con vigencia (solo crear), categorías de gasto, formas de pago, lista de miembros (solo lectura), exportación completa de respaldo (RF-26).

## 8. Modelo de datos y esquema de Firestore

Los movimientos son la evidencia; los saldos se calculan a partir de ellos (sección 9). Todo vive bajo `businesses/{businessId}`. Los nombres de este esquema son definitivos: la IA no debe renombrar colecciones ni campos sin que se actualice este documento.

### 8.1 Campos comunes de todo movimiento

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `fechaOperativa` | String `YYYY-MM-DD` | Día al que pertenece el movimiento, en zona `America/Bogota` |
| `createdAt` | Timestamp | Hora del dispositivo al crear el movimiento |
| `syncedAt` | Timestamp | `serverTimestamp()`; la fija el servidor al confirmar la escritura |
| `createdBy` | String | UID del usuario que lo registró |
| `note` | String, opcional | Observación libre |

El ID del documento se genera en el dispositivo. Los movimientos son inmutables: las reglas solo permiten crear (R-18).

Los precios (`prices`) no son movimientos: llevan `createdAt`, `createdBy`, `syncedAt` y `note` opcional, pero no `fechaOperativa`; su fecha es `validFrom` (§8.2). Por eso no pasan por `validMovement` ni por la restricción de `startDate` (§11.1).

### 8.2 Documentos de configuración

| Ruta | Campos | Escribe |
| --- | --- | --- |
| `businesses/{businessId}` | `name`, `currency` (`COP`), `timezone` (`America/Bogota`), `startDate` (`String` `YYYY-MM-DD`), `active`. El `businessId` es fijo en `BuildConfig` (D-15) | Consola de Firebase |
| `members/{uid}` | `name`, `email`, `role` (`admin` o `operator`), `active`, `createdAt` | Consola de Firebase |
| `prices/{id}` | `classification`, `pricePerBucket` (entero mayor que 0), `validFrom` (`String` `YYYY-MM-DD`), `createdAt`, `createdBy`, `syncedAt`, `note` (opcional); sin `fechaOperativa` (§8.1). Cada cambio de precio es un documento nuevo; nunca se edita ni se anula: un precio mal creado se corrige creando otro con el mismo `validFrom`. El vigente para una venta es el de mayor `validFrom` menor o igual a la `fechaOperativa` de la venta; a igual `validFrom`, el de mayor `createdAt` (R-04) | Administrador (solo crea) |
| `expenseCategories/{id}` | `name`, `active` | Administrador |
| `paymentMethods/{id}` | `name`, `active`. Una forma de pago desactivada no se ofrece en registros nuevos, pero su caja se sigue calculando (D-04) | Administrador |
| `customers/{id}` | `name`, `phone` (opcional), `notes` (opcional), `active`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`. `createdAt` y `createdBy` son inmutables | Operador (crea y edita), administrador (desactiva) |

### 8.3 Colecciones de movimientos

| Colección | Campos propios (además de los comunes) |
| --- | --- |
| `openingBalances` | `kind` (`eggs`, `feed`, `cash`, `customerDebt`), `key` (clasificación, ingrediente, id de forma de pago o id de cliente), `quantity` (huevos, kg o pesos) |
| `harvests` | `graded` (mapa `B`, `A`, `AA`, `AAA`, `JUMBO` con huevos aptos), `discarded` (total de huevos rotos o no aptos) |
| `sales` | `customerId` (opcional), `lines` (lista, ver 8.4), `customerName` (copia del nombre del cliente al vender), `total`, `deliveryStatus` (`pending` o `delivered`), `deliveredAt` (`Timestamp`) y `deliveredBy` (al entregar; si la venta se crea como `delivered`, iguales a `createdAt` y `createdBy`, R-05) |
| `payments` | `saleId` u `openingDebtId` (exactamente uno), `amount` (mayor que 0), `methodId`, `type` (exactamente `payment`, `refund` u `overpayment_refund`). Campos propios por tipo: `payment` no lleva `saleReturnId` ni `reason`; `refund` nace de una devolución, va sobre una venta (`saleId`), exige `saleReturnId` y solo se crea en el mismo lote que la devolución (R-09); `overpayment_refund` corrige un sobrepago (A-10), solo lo crea el administrador, exige `reason` (texto no vacío) y no lleva `saleReturnId` (R-07). Los dos tipos de reintegro restan caja. No guarda cliente: se deduce de la venta o de la deuda de apertura |
| `saleReturns` | `saleId`, `classification`, `buckets`, `creditAmount`, `restock` (verdadero si los huevos reingresan), `reason` |
| `eggOutflows` | `classification`, `eggs` (entero mayor que 0), `type` (`own_use`, `gift` o `loss`), `reason` (obligatorio si es `loss`) |
| `feedPurchases` | `ingredient`, `bags`, `totalKg` (bultos por 40), `totalCost`, `methodId`, `supplier` (opcional) |
| `feedDepletions` | `ingredient`, `bags`, `kg` (bultos por 40) |
| `feedLosses` | `ingredient`, `kg`, `type` (`waste`, `loss` u `other`), `reason` |
| `expenses` | `categoryId`, `amount`, `methodId`, `description` (opcional) |
| `withdrawals` | `amount`, `methodId`, `note` |
| `counts` | `kind` (`eggs`, `feed` o `cash`), `items` (mapa clave a `{expected, physical, difference, reason}`; solo las claves contadas, R-12), `isClosing` (booleano). `reason` es obligatorio en cada ítem con `difference` distinta de cero. Los huevos siempre en huevos. No se anula (R-18) |
| `closings` | `summary` (mapa con ventas, cobros, gastos, retiros y diferencias; reconstruible), `countIds` (lista). No se anula (R-18) |
| `reversals` | `targetCollection`, `targetId`, `reason` (obligatorio); solo administrador (R-18). `targetCollection` solo puede ser una colección de movimientos anulables: `openingBalances`, `harvests`, `sales`, `payments`, `saleReturns`, `eggOutflows`, `feedPurchases`, `feedDepletions`, `feedLosses`, `expenses` o `withdrawals` (no `reversals`, `counts`, `closings`, catálogos ni clientes) |

Notas del esquema:

- Enumeraciones y claves: clasificaciones `B`, `A`, `AA`, `AAA`, `JUMBO`; ingredientes `concentrate` y `corn_bran`.
- Todo valor monetario, de huevos, bultos o kilos es entero.
- Los campos derivados (`totalKg`, `kg`, `eggs` de una línea, `total`, `creditAmount`) se guardan para facilitar exportes y auditoría; las pruebas deben verificar que coinciden con su cálculo.
- Las ventas guardan `customerName` copiado del cliente en ese momento para que el historial sea legible aunque el cliente se desactive.
- La auditoría la cubren `createdBy`, `createdAt`, `syncedAt` y `reversals`. Los clientes guardan además `updatedBy` y `updatedAt`; las categorías de gasto y las formas de pago solo guardan `name` y `active`, sin auditoría de cambios.

### 8.4 Ejemplo de documento de venta

```json
{
  "fechaOperativa": "2026-10-24",
  "createdAt": "2026-10-24T15:42:10-05:00",
  "syncedAt": "(serverTimestamp)",
  "createdBy": "uidDeLaMadre",
  "customerId": "cli_001",
  "customerName": "Tienda La Esquina",
  "lines": [
    { "classification": "AA", "buckets": 2, "eggs": 60, "listPrice": 18000, "unitPrice": 17500, "subtotal": 35000 },
    { "classification": "A", "buckets": 1, "eggs": 30, "listPrice": 16000, "unitPrice": 16000, "subtotal": 16000 }
  ],
  "total": 51000,
  "deliveryStatus": "pending",
  "note": "Entregar mañana temprano"
}
```

Los valores del ejemplo son ilustrativos, no precios reales.

### 8.5 Consultas e índices

Principio: los saldos se consultan por `createdAt` posterior al punto de partida (sección 9.1); el rango de `fechaOperativa` se usa solo para mostrar (Inicio, Historial, Reportes y CSV), nunca para calcular saldos. Todas las lecturas se hacen con listeners sobre la caché local (sección 10.2) y los cálculos los hace el módulo `domain`.

| Uso | Consulta | Notas |
| --- | --- | --- |
| Configuración y catálogos | Listener sobre las colecciones completas `prices`, `expenseCategories`, `paymentMethods`, `customers` y `openingBalances`, más el documento del negocio y el del miembro | Colecciones pequeñas. El precio vigente se elige en `domain` (R-04) |
| Reversos | Listener sobre la colección completa `reversals` | Colección pequeña (solo correcciones). `domain` arma el conjunto de `targetId` anulados y lo aplica en todas las pantallas y cálculos |
| Punto de partida por clave (huevos, alimento, caja) | `counts` donde `kind == k`, ordenado por `createdAt` descendente, por páginas, hasta encontrar para cada clave el último conteo que la incluye; si no hay, la apertura | Índice compuesto `kind` + `createdAt`. T0 es el `createdAt` más antiguo entre los puntos de partida de las claves de ese tipo |
| Saldo de huevos (Físico) | `harvests`, `eggOutflows` y `saleReturns` donde `createdAt > T0`; `sales` donde `deliveredAt > T0` (entregas) | `domain` aplica R-21 por clave (en las entregas, solo `deliveredAt` posterior al `createdAt` del punto de partida) y descarta los anulados |
| Ventas pendientes de entrega, PorEntregar y A-06 | `sales` donde `deliveryStatus == 'pending'`, sin límite de fecha | El conjunto es pequeño porque las ventas entregadas salen de la consulta. A-06: las de `fechaOperativa` anterior a hoy |
| Saldo de alimento | `feedPurchases`, `feedDepletions` y `feedLosses` donde `createdAt > T0` | |
| Caja | `payments`, `expenses`, `feedPurchases` y `withdrawals` donde `createdAt > T0` | |
| Saldo de un cliente, A-05 y A-10 | `sales` donde `customerId == c` (todas las fechas); `payments` y `saleReturns` donde `saleId in [...]` y `payments` donde `openingDebtId in [...]`, en grupos de 30 (máximo de `in`); la deuda de apertura sale de `openingBalances`. Los pagos no guardan cliente: se asocian por su venta o deuda de apertura | A-05: ventas activas con `PendienteVenta > 0` y `fechaOperativa` a más de 15 días de calendario. A-10: `PendienteVenta < 0` o pago activo sobre venta anulada. La lista de clientes repite el cálculo por cliente; su costo se mide en el piloto (Fase 5) |
| A-10 por entrega sobre venta anulada | `sales` donde `deliveredAt > T0` (la misma consulta del saldo de huevos) cruzada con `reversals` | Venta anulada cuyo `deliveredAt` es posterior al `createdAt` de su reverso |
| A-04 | Resultado de la consulta de punto de partida | Días de calendario entre la `fechaOperativa` del último conteo que incluye cada clave (o de la apertura) y hoy |
| A-07 | Estado local de sincronización (sección 10.2), sin consulta | |
| A-08 | Resultado del cálculo de saldos | Movimientos (no entregas) con `createdAt` posterior al punto de partida que R-21 excluye |
| Inicio (resumen y lista reciente) | Cada colección de movimientos donde `fechaOperativa >= hoy − 6 días` | Solo para mostrar; los saldos y alertas de Inicio salen de las filas anteriores. A-10 de ventas sin cliente se evalúa solo sobre esta ventana (limitación aceptada de la v1, §9.5). La consulta de `closings` de esta ventana indica si hoy ya tiene cierre (fecha por defecto, R-21) |
| Bloqueo «Esperando apertura» | El listener de `openingBalances` de la primera fila, con metadatos | Se desbloquea al recibir un documento activo con `isFromCache == false` y `hasPendingWrites == false` (§5.1) |
| Historial | Colección elegida donde `fechaOperativa` entre desde y hasta | Filtros de tipo, usuario y estado en el dispositivo |
| Reportes y CSV por periodo | Cada colección donde `fechaOperativa` entre desde y hasta | La exportación completa (RF-26) lee las colecciones sin rango |

- Los índices compuestos se crean cuando la consola de Firebase los solicite y se guardan en `firestore.indexes.json` dentro del repositorio. Los de un solo campo (`createdAt`, `deliveredAt`, `deliveryStatus`, `fechaOperativa`, `customerId`, `saleId`, `openingDebtId`) son automáticos.

## 9. Cálculos, alertas, reportes y exportación

Todos los saldos se calculan en el módulo `domain` a partir de movimientos y puntos de partida; ninguno se guarda como fuente de verdad. Cada fórmula debe tener pruebas unitarias (sección 13).

### 9.1 Punto de partida: el último conteo

Para no leer todo el historial, cada clave (clasificación de huevo, ingrediente o forma de pago) parte de su punto de partida: el último conteo que la incluya (por `createdAt`) o, si no hay ninguno, su saldo de apertura. Un conteo parcial no cambia el punto de partida de las claves que no contó (R-12). Puede haber más de un cierre en el mismo día; también entonces vale el último conteo que incluye cada clave.

Un movimiento activo entra al saldo de una clave solo si cumple las dos condiciones de R-21:

1. Su `createdAt` es posterior al `createdAt` del punto de partida.
2. Su `fechaOperativa` es mayor que la del punto de partida si este es un cierre (`isClosing: true`), o mayor o igual si es un conteo intermedio o la apertura.

Las entregas son la única excepción: el efecto de una entrega sobre Físico entra al saldo solo si `deliveredAt` es posterior al `createdAt` del punto de partida. No se compara ninguna fecha (ni la `fechaOperativa` de la venta ni la fecha de `deliveredAt`), porque la entrega es un hecho físico con hora real; por eso una entrega nunca es registro tardío. Una venta creada como `delivered` después de un cierre propone el día siguiente como `fechaOperativa`, pero su entrega cuenta por `deliveredAt`. La venta en sí (su efecto sobre PorEntregar) no depende del punto de partida (§9.2).

Si cumple la 1 pero no la 2, es un registro tardío: se excluye del saldo, porque el conteo ya lo refleja, y genera A-08. Si su `fechaOperativa` estaba mal puesta, el administrador lo anula y lo registra de nuevo. Para reducir estos casos, cuando hoy ya tiene cierre la app propone el día siguiente como fecha por defecto y avisa (R-21).

- Un reverso solo cambia el saldo si el movimiento anulado entra al saldo según las condiciones anteriores; el conteo ya refleja la realidad física anterior.
- `createdAt` sale del reloj del dispositivo; se exige fecha y hora automáticas y las reglas rechazan un `createdAt` mayor que `request.time` más 5 minutos (R-22). No se usa `syncedAt` para ordenar.
- Si un conteo se hizo en un dispositivo antes de sincronizar movimientos que ya existían, la diferencia guardada puede quedar desfasada; la app la recalcula y la muestra como «diferencia revisada» sin modificar el conteo.

### 9.2 Huevos por clasificación

```latex
\text{Físico}_c = \text{Base}_c + \text{Aptos}_c - 30 \cdot \text{CubetasEntregadas}_c + 30 \cdot \text{CubetasReingresadas}_c - \text{Egresos}_c
```

```latex
\text{PorEntregar}_c = 30 \cdot \text{CubetasPendientesDeEntrega}_c \qquad \text{Disponible}_c = \text{Físico}_c - \text{PorEntregar}_c
```

- `Base` es el huevo físico del punto de partida; `Aptos` son los huevos aptos de las recolecciones posteriores; `Egresos` son consumo propio, obsequios y pérdidas.
- `CubetasPendientesDeEntrega` incluye todas las ventas activas con entrega pendiente, sin importar su fecha.
- Disponible negativo significa que se vendió más de lo registrado: genera la alerta A-01 pero no bloquea la venta (§7.3).
- El conteo de huevos se compara con `Físico`. Descartados de recolección no participan (R-10).

### 9.3 Alimento por ingrediente

```latex
\text{Saldo}_i\,(\text{kg}) = \text{Base}_i + 40 \cdot \text{BultosComprados}_i - 40 \cdot \text{BultosAgotados}_i - \text{Pérdidas}_i
```

```latex
\text{ConsumoDiarioEstimado}_i = \frac{40 \cdot \text{BultosAgotados}_i\ \text{en el periodo}}{\text{días del periodo}}
```

El promedio es aproximado y solo es útil con periodos de al menos 14 días. Los días del periodo cuentan ambos extremos (inclusivo). Se calcula con `BigDecimal` sin redondear y se muestra con un decimal, redondeando half-up solo al mostrar (por ejemplo, 40 kg en 14 días se muestra como 2,9 kg/día). No se guarda. Limitación conocida: tras un conteo, el primer bulto que se marque agotado resta 40 kg completos aunque ya estuviera parcialmente usado; el siguiente conteo semanal corrige el saldo. Medir el sobrante del bulto abierto queda como mejora posible.

### 9.4 Clientes, caja y resultado aproximado

```latex
\text{PendienteVenta} = \text{Total} - \sum \text{Devoluciones} - \sum \text{Pagos} + \sum \text{Reintegros}
```

```latex
\text{SaldoCliente} = \sum \text{PendienteVenta (ventas activas)} + \sum \text{Deudas de apertura pendientes}
```

```latex
\text{Caja}_m = \text{Base}_m + \text{Cobros}_m - \text{Reintegros}_m - \text{Gastos}_m - \text{ComprasAlimento}_m - \text{Retiros}_m
```

```latex
\text{ResultadoAproximado} = \text{VentasNetas} - \text{CostoAlimentoEstimado} - \text{GastosOperativos}
```

- `PendienteVenta` no puede quedar negativo al confirmar en un mismo dispositivo: la app rechaza cualquier operación que lo deje por debajo de 0. Si queda negativo por concurrencia sin conexión (dos teléfonos pagan la misma venta), se dispara A-10 y el administrador lo corrige con un `overpayment_refund` por el exceso (R-07). No hay saldo a favor persistente ni se traslada crédito a otras ventas.
- `Reintegros` suma los dos tipos de reintegro: `refund` (excedente de una devolución, en el mismo `WriteBatch`, R-09) y `overpayment_refund` (corrección de sobrepago, R-07). Ambos suman en `PendienteVenta` (o en la deuda de apertura) y restan en `Caja` de su forma de pago. `Cobros` son los pagos `type: payment`.
- La caja se calcula para todas las formas de pago, incluidas las desactivadas (D-04).
- `VentasNetas` son las ventas activas del periodo menos el crédito de devoluciones.
- `CostoAlimentoEstimado` es, por ingrediente, los kg agotados por el costo promedio por kg de las compras de los últimos 90 días (total pagado entre total de kg), redondeado al peso entero. Si no hubo compras en esos 90 días se usa el costo por kg de la última compra; si no existe ninguna compra, el costo y el resultado se muestran como «sin dato».
- Los retiros personales, el consumo propio y los obsequios no entran al resultado.
- El resultado se rotula siempre como «aproximado, no contable»; no es utilidad contable ni flujo de caja.

### 9.5 Alertas (RF-23)

| Código | Condición | Dónde se ve |
| --- | --- | --- |
| A-01 | Huevos disponibles negativos en alguna clasificación | Inicio, Venta |
| A-02 | Saldo de alimento negativo | Inicio, Alimento |
| A-03 | Conteo con diferencia distinta de cero | Cierre, Historial |
| A-04 | Conteo atrasado: huevos o caja más de 4 días, alimento más de 8 días (días de calendario de Bogotá) | Inicio |
| A-05 | Venta con saldo pendiente por más de 15 días de calendario de Bogotá (D-12) | Inicio, Clientes |
| A-06 | Entrega pendiente con `fechaOperativa` anterior a hoy (días de calendario de Bogotá) | Inicio, Cobrar |
| A-07 | Hay cambios pendientes de sincronizar y la última sincronización confirmada guardada en el teléfono tiene más de 24 horas (se cuenta en horas) | Inicio |
| A-08 | Registro tardío (R-21) | Historial |
| A-09 | Más de 30 días sin exportación completa en el teléfono del administrador (RF-26). Solo la ve el administrador | Inicio (administrador) |
| A-10 | Pago que excede el saldo de la venta o deuda (`PendienteVenta` negativo tras sincronizar), pago activo sobre una venta anulada o entrega registrada sobre una venta anulada (`deliveredAt` posterior al reverso). Limitación aceptada de la v1: en ventas sin cliente solo se revisa dentro de la ventana de 7 días de Inicio (§8.5) | Inicio, Clientes, Historial |

### 9.6 Reportes mínimos (RF-24)

- Huevos recogidos, vendidos, entregados, descartados y disponibles por clasificación.
- Diferencias entre inventario esperado y conteo físico (huevos, alimento y caja).
- Ventas totales, cobradas y pendientes; pagos por periodo; cartera por cliente.
- Gastos y compras por categoría, ingrediente y forma de pago; retiros del periodo.
- Bultos agotados y consumo diario estimado por ingrediente.
- Entradas, salidas y saldo esperado de caja por día, semana y mes, por forma de pago.
- Historial de precios aplicados por clasificación.
- Resultado aproximado del periodo con su rótulo.

Todos aceptan filtro por periodo; según el reporte también por clasificación, ingrediente, categoría o cliente.

### 9.7 Exportación CSV (RF-25)

Formato común: UTF-8 con BOM, separador coma, fechas `YYYY-MM-DD`, números enteros sin separador de miles y encabezados en español en minúsculas con guion bajo. Un texto que contenga coma, comillas o salto de línea va entre comillas dobles y sus comillas internas se duplican. `fecha` es la `fechaOperativa`.

- La columna `estado` (`activo` o `anulado`) solo aparece en los archivos de movimientos que se pueden anular (los marcados «sí» en la tabla).
- La columna `registrado_por` (nombre del miembro según `createdBy`) aparece en todos los archivos cuyos documentos tienen `createdBy`.
- Los archivos de movimientos se exportan por rango de fechas. Los catálogos y `clientes_saldos.csv` reflejan el estado en el momento de exportar.
- La exportación completa (RF-26) incluye todos los archivos de la tabla, sin rango de fechas.

| Archivo | Columnas principales | `estado` |
| --- | --- | --- |
| `ventas_lineas.csv` | venta\_id, fecha, cliente, clasificacion, cubetas, huevos, precio\_lista, precio\_aplicado, subtotal, total\_venta, entrega, estado, registrado\_por | Sí |
| `pagos.csv` | pago\_id, fecha, venta\_id, deuda\_apertura\_id, devolucion\_id, cliente (deducido de la venta o de la deuda de apertura), tipo (`payment`, `refund` u `overpayment_refund`), monto, forma\_pago, motivo (solo `overpayment_refund`), estado, registrado\_por | Sí |
| `devoluciones.csv` | devolucion\_id, fecha, venta\_id, clasificacion, cubetas, credito, reingresa, motivo, estado, registrado\_por | Sí |
| `recolecciones.csv` | recoleccion\_id, fecha, aptos\_b, aptos\_a, aptos\_aa, aptos\_aaa, aptos\_jumbo, descartados, estado, registrado\_por | Sí |
| `huevos_egresos.csv` | egreso\_id, fecha, clasificacion, huevos, tipo, motivo, estado, registrado\_por | Sí |
| `alimento_compras.csv` | compra\_id, fecha, ingrediente, bultos, kg, costo\_total, forma\_pago, proveedor, estado, registrado\_por | Sí |
| `alimento_agotamientos.csv` | agotamiento\_id, fecha, ingrediente, bultos, kg, estado, registrado\_por | Sí |
| `alimento_perdidas.csv` | perdida\_id, fecha, ingrediente, kg, tipo, motivo, estado, registrado\_por | Sí |
| `gastos.csv` | gasto\_id, fecha, categoria, monto, forma\_pago, descripcion, estado, registrado\_por | Sí |
| `retiros.csv` | retiro\_id, fecha, monto, forma\_pago, nota, estado, registrado\_por | Sí |
| `apertura.csv` | apertura\_id, fecha, tipo, clave, cantidad, estado, registrado\_por | Sí |
| `conteos.csv` | conteo\_id, fecha, tipo, clave, esperado, fisico, diferencia, motivo, nota, es\_cierre, registrado\_por (una fila por clave contada) | No |
| `cierres.csv` | cierre\_id, fecha, ventas, cobros, gastos, retiros, conteo\_ids, registrado\_por | No |
| `reversos.csv` | reverso\_id, fecha, coleccion, movimiento\_id, motivo, registrado\_por | No |
| `clientes_saldos.csv` | cliente\_id, cliente, deuda\_apertura, ventas, devoluciones, pagos, reintegros, saldo (sin teléfono, D-08) | No |
| `precios.csv` | precio\_id, clasificacion, precio\_por\_cubeta, vigente\_desde, registrado\_por (sin `fecha`: los precios no tienen `fechaOperativa`, §8.1) | No |
| `clientes.csv` | cliente\_id, cliente, notas, activo, registrado\_por (sin teléfono, D-08) | No |
| `formas_pago.csv` | forma\_pago\_id, nombre, activo | No |
| `categorias_gasto.csv` | categoria\_id, nombre, activo | No |

## 10. Arquitectura técnica y sincronización

La app es un cliente Android que habla directamente con Firebase, sin servidor propio; toda la lógica de negocio vive en el dispositivo, en un módulo de Kotlin puro que se puede probar sin emulador.

### 10.1 Stack y estructura

| Elemento | Decisión |
| --- | --- |
| Lenguaje e interfaz | Kotlin y Jetpack Compose; Material 3 |
| Android mínimo | API 26 (D-13) |
| Arquitectura | Capas `ui` (pantallas y ViewModels), `domain` (modelos, reglas, casos de uso y cálculos; Kotlin puro) y `data` (repositorios sobre Firestore) |
| Autenticación | Firebase Authentication con correo y contraseña (sin SMS) |
| Base de datos | Cloud Firestore con persistencia local del SDK (activa por defecto en Android) configurada con caché sin límite de tamaño (`PersistentCacheSettings` con `CACHE_SIZE_UNLIMITED`), para que el SDK nunca descarte documentos de la caché; sin Room en el MVP |
| Identificador del negocio | `businessId` fijo en `BuildConfig` (D-15), porque hay un solo negocio. La app lee `businesses/{businessId}` y `businesses/{businessId}/members/{uid}` directamente, sin consultas de grupo de colecciones |
| Inyección de dependencias | Hilt (D-14) |
| CSV | Generado en el dispositivo y compartido con el selector de archivos del sistema |
| Pruebas | JUnit para `domain`, Firebase Emulator Suite para reglas y repositorios, revisión estática (lint o detekt) para AC-36, pruebas manuales en modo avión en teléfonos reales |
| Distribución | APK instalado directo o Firebase App Distribution durante el piloto; Google Play se evalúa después |
| Fuera del MVP | Cloud Functions, Cloud Storage, App Check, Room, WorkManager propio |

### 10.2 Reglas de sincronización y conflictos

1. Cada movimiento es un documento independiente e inmutable, con ID creado en el dispositivo; un reintento no duplica porque reescribe el mismo ID.
2. La interfaz lee siempre del estado local mediante listeners con metadatos y muestra «pendiente de sincronizar» cuando `hasPendingWrites` es verdadero.
3. No se espera (`await`) la tarea de escritura de Firestore en la UI; sin conexión no se completa hasta reconectar. Única excepción: la apertura (§5.1), en la que la app tampoco espera la tarea, sino que observa con un listener de metadatos los documentos de `openingBalances`. Cada teléfono bloquea los movimientos con el aviso «Esperando apertura» hasta recibir una apertura activa con `isFromCache == false` y `hasPendingWrites == false`; si la escritura de la apertura se rechaza, el bloqueo se levanta con el error de RF-31 (punto 11).
4. Las operaciones de varios documentos (abono repartido, devolución con `refund`, anulación conjunta de una devolución y su `refund`, cierre con conteos) usan `WriteBatch`, que funciona sin conexión. No se usan transacciones, que requieren servidor. Presupuesto de accesos a documentos en reglas (peor caso sin caché, tope oficial de 20 por lote y 10 por operación): `payment` = **2** (miembro + negocio), `refund` = **3** (+ `existsAfter`), `overpayment_refund` = **3** (miembro + negocio en `validMovement` + miembro en `isAdmin`); devolución con `refund` = **5**; cierre (`3 counts + 1 closings`) = **8**; **reverso = 4** (`isAdmin`: miembro; `validMovement`/`isOperator`: miembro de nuevo + negocio; `exists` del destino); **anulación conjunta devolución + `refund` = 8** (2 reversos en `WriteBatch`). El tope de **10 pagos por lote** aplica al abono repartido (solo `type: payment`, constante `MAX_PAYMENTS_PER_BATCH = 10` en `:domain`). *Advertencia:* el emulador local no valida el límite de 20 accesos por lote; en la **Tarea 5** queda pendiente verificar en un proyecto real de Firebase lotes de **10, 11 y 20 pagos** con la variante B desplegada.
5. Ningún saldo compartido se actualiza como dato único; al llegar cambios de otros teléfonos, los saldos se recalculan y se levantan alertas (inventario negativo, diferencias, registros tardíos, pagos que exceden el saldo).
6. Durante una caída de internet solo un teléfono designado registra ventas de huevos (D-06). Es un acuerdo operativo no automatizable: la app no lo controla. Los demás pueden consultar y registrar otros movimientos.
7. `syncedAt` usa `serverTimestamp()` y se valida en las reglas igual a `request.time`; `fechaOperativa` y `createdAt` salen del dispositivo, y las reglas rechazan un `createdAt` mayor que `request.time` más 5 minutos (R-22).
8. Antes de cerrar sesión, la app avisa si hay cambios pendientes de sincronizar, porque esos datos solo existen en ese teléfono.
9. Los reportes históricos completos requieren que esos datos estén en caché o haya conexión; la UI distingue datos locales de datos confirmados por el servidor.
10. **Estado de sincronización (RF-02).** El SDK no expone la cantidad exacta de escrituras pendientes, así que la app muestra solo «hay cambios pendientes» (sí/no). Al hacer cada escritura marca localmente que hay pendientes y llama a `waitForPendingWrites()`; cuando esa tarea termina, borra la marca y guarda localmente (en el teléfono, no en Firestore) la hora como «última sincronización confirmada». A-07 usa esos dos datos.
11. **Escrituras rechazadas (RF-31).** Cada escritura o `WriteBatch` registra un listener de fallo sobre su tarea, sin esperarla en la UI. Si el servidor la rechaza (permiso denegado por miembro desactivado o regla incumplida), la app guarda localmente el aviso con el tipo de movimiento, su fecha operativa y el motivo, y lo muestra en Inicio hasta que el usuario lo descarta. El documento rechazado desaparece de la caché, así que el aviso es la única constancia y el usuario debe volver a registrarlo si corresponde. Si lo rechazado es la apertura, el bloqueo «Esperando apertura» deja de esperar, muestra el error y ofrece reintentar o corregir (§5.1). Limitación conocida del SDK: el listener de fallo vive en memoria; si la app se cierra antes de sincronizar, un rechazo posterior no se puede atribuir a un movimiento concreto (ver sección 15).

### 10.3 Lecturas y costo

Firestore cobra por documento leído desde el servidor, no desde la caché. La consulta de agregación (`sum`, `count`) no funciona sin conexión, así que todos los cálculos se hacen en el dispositivo sobre los documentos en caché, con las consultas definidas en la sección 8.5. Criterios de costo:

| Pantalla o cálculo | Campo que acota la lectura | Por qué es barato |
| --- | --- | --- |
| Saldos de huevos, alimento y caja | `createdAt` posterior al punto de partida (y `deliveredAt` para entregas) | Solo se leen los movimientos desde el último conteo; los conteos frecuentes (D-07) mantienen la ventana corta |
| Inicio | `fechaOperativa` de los últimos 7 días, solo para mostrar | Ventana fija y pequeña |
| Ventas pendientes de entrega | `deliveryStatus == 'pending'` | Las entregadas salen de la consulta |
| Saldo de cliente | `customerId` de la venta y luego `saleId` / `openingDebtId` en grupos de 30 | Lee solo el historial de ese cliente; es la consulta que más crece con el tiempo. Se acepta su costo y se mide en el piloto (Fase 5); si resulta caro, se evaluará como mejora un corte de cartera |
| Reversos y catálogos | Colección completa | Colecciones pequeñas; tras la primera descarga, el listener solo recibe cambios |
| Historial, reportes y CSV por periodo | Rango de `fechaOperativa` | Se piden a demanda |
| Exportación completa | Sin rango | Una vez al mes, en el teléfono del administrador (RF-26) |

- Los listeners se mantienen vivos en los repositorios (no se recrean al cambiar de pantalla) para no repetir lecturas. Si un listener estuvo desconectado más de 30 minutos, Firestore cobra la reconsulta como nueva.
- La caché sin límite (sección 10.1) evita que el SDK descarte documentos que luego habría que volver a leer del servidor.
- Las reglas de seguridad leen el documento del miembro, el del negocio (`startDate`), en los reversos el documento destino y en los reintegros la devolución (sección 11.1); son lecturas facturables, pocas por escritura.

## 11. Seguridad, privacidad y respaldo

Los datos solo los ve y escribe un miembro activo del negocio, y las reglas de Firestore, no la app, son la defensa real: cualquiera con el APK podría intentar llamar a la base directamente. Las reglas deben desplegarse y verificarse antes de instalar la app en producción.

### 11.1 Borrador de reglas de Firestore

Es un punto de partida que la IA debe completar (validar tipos y rangos de cada colección con el mismo patrón que `sales`, `payments` y `customers`) y probar con Firebase Emulator Suite. Las reglas se aplican con permisos de lectura para todos los miembros activos y escritura solo por creación, salvo las excepciones indicadas. La ruta usa el `businessId` fijo de `BuildConfig` (D-15); no se necesitan reglas de grupo de colecciones. Presupuesto de accesos por escritura en el peor caso (variante B, un solo `get()` del miembro): `payment` = 2, `refund` = 3, `overpayment_refund` = 3, devolución con `refund` = 5, cierre (`3 counts + 1 closings`) = 8 (tope de 10 pagos por lote para abonos de `type: payment`).

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function memberDoc(b) {
      return get(/databases/$(database)/documents/businesses/$(b)/members/$(request.auth.uid));
    }
    function businessData(b) {
      return get(/databases/$(database)/documents/businesses/$(b)).data;
    }
    function isActive(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true;
    }
    function isAdmin(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true && m.data.role == 'admin';
    }
    function isOperator(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true && m.data.role in ['admin', 'operator'];
    }

    // Fecha YYYY-MM-DD con mes 01-12 y día válido para el mes.
    // El 29 de febrero de años no bisiestos se valida solo en la app.
    function validDate(s) {
      return s is string
        && s.matches('^[0-9]{4}-((0[13578]|1[02])-(0[1-9]|[12][0-9]|3[01])|(0[469]|11)-(0[1-9]|[12][0-9]|30)|02-(0[1-9]|1[0-9]|2[0-9]))$');
    }
    // R-22: createdAt no puede estar más de 5 minutos en el futuro.
    function validDeviceTime(t) {
      return t is timestamp && t <= request.time + duration.value(5, 'm');
    }

    function validMovement(b) {
      let d = request.resource.data;
      return isOperator(b)
        && d.createdBy == request.auth.uid
        && validDate(d.fechaOperativa)
        && d.fechaOperativa >= businessData(b).startDate
        && validDeviceTime(d.createdAt)
        && d.syncedAt == request.time;
    }

    match /businesses/{b} {
      allow read: if isActive(b);
      allow write: if false;

      match /{coll}/{id} {
        allow read: if isActive(b);
        allow create: if coll in ['harvests', 'saleReturns', 'eggOutflows', 'feedPurchases',
                                  'feedDepletions', 'feedLosses', 'expenses', 'withdrawals',
                                  'counts', 'closings']
                      && validMovement(b);
        allow create: if coll == 'openingBalances'
                      && isAdmin(b) && validMovement(b);
        allow create, update: if coll in ['expenseCategories', 'paymentMethods']
                              && isAdmin(b)
                              && request.resource.data.keys().hasOnly(['name', 'active'])
                              && request.resource.data.name is string
                              && request.resource.data.name.size() > 0
                              && request.resource.data.active is bool;
        allow delete: if false;
      }

      // Los precios no son movimientos: sin fechaOperativa ni restricción de startDate (§8.1).
      match /prices/{id} {
        function validPrice(d) {
          return d.keys().hasOnly(['classification', 'pricePerBucket', 'validFrom',
                                   'createdAt', 'createdBy', 'syncedAt', 'note'])
            && d.classification in ['B', 'A', 'AA', 'AAA', 'JUMBO']
            && d.pricePerBucket is int && d.pricePerBucket > 0
            && validDate(d.validFrom)
            && d.createdBy == request.auth.uid
            && validDeviceTime(d.createdAt)
            && d.syncedAt == request.time;
        }
        allow create: if isAdmin(b) && validPrice(request.resource.data);
      }

      match /reversals/{id} {
        allow create: if isAdmin(b) && validMovement(b)
          && request.resource.data.reason is string
          && request.resource.data.reason.size() > 0
          && request.resource.data.targetCollection in ['openingBalances', 'harvests', 'sales',
               'payments', 'saleReturns', 'eggOutflows', 'feedPurchases', 'feedDepletions',
               'feedLosses', 'expenses', 'withdrawals']
          && request.resource.data.targetId is string
          && exists(/databases/$(database)/documents/businesses/$(b)/$(request.resource.data.targetCollection)/$(request.resource.data.targetId));
      }

      match /sales/{id} {
        allow create: if validMovement(b)
          && request.resource.data.keys().hasOnly([
               'fechaOperativa', 'createdAt', 'syncedAt', 'createdBy', 'note',
               'customerId', 'lines', 'customerName', 'total', 'deliveryStatus', 'deliveredAt', 'deliveredBy'])
          && request.resource.data.lines is list
          && request.resource.data.lines.size() > 0
          && request.resource.data.total is int
          && request.resource.data.total > 0
          && (
            (request.resource.data.deliveryStatus == 'pending'
              && !('deliveredAt' in request.resource.data)
              && !('deliveredBy' in request.resource.data))
            ||
            (request.resource.data.deliveryStatus == 'delivered'
              && request.resource.data.deliveredAt == request.resource.data.createdAt
              && request.resource.data.deliveredBy == request.auth.uid)
          );
        allow update: if isOperator(b)
          && resource.data.deliveryStatus == 'pending'
          && request.resource.data.deliveryStatus == 'delivered'
          && request.resource.data.deliveredBy == request.auth.uid
          && validDeviceTime(request.resource.data.deliveredAt)
          && request.resource.data.diff(resource.data).affectedKeys()
               .hasOnly(['deliveryStatus', 'deliveredAt', 'deliveredBy']);
      }

      match /payments/{id} {
        // Lista cerrada de campos por tipo (§8.3).
        function validPaymentByType(d) {
          return (d.type == 'payment'
                && d.keys().hasOnly(['fechaOperativa', 'createdAt', 'syncedAt', 'createdBy', 'note',
                     'saleId', 'openingDebtId', 'amount', 'methodId', 'type']))
            || (d.type == 'refund'
                && d.keys().hasOnly(['fechaOperativa', 'createdAt', 'syncedAt', 'createdBy', 'note',
                     'saleId', 'amount', 'methodId', 'type', 'saleReturnId'])
                && d.get('saleId', null) is string
                && d.get('saleReturnId', null) is string
                // La devolución debe existir al terminar el lote (mismo WriteBatch, R-09).
                && existsAfter(/databases/$(database)/documents/businesses/$(b)/saleReturns/$(d.saleReturnId)))
            || (d.type == 'overpayment_refund'
                && isAdmin(b)
                && d.keys().hasOnly(['fechaOperativa', 'createdAt', 'syncedAt', 'createdBy', 'note',
                     'saleId', 'openingDebtId', 'amount', 'methodId', 'type', 'reason'])
                && d.get('reason', null) is string
                && d.reason.size() > 0);
        }
        allow create: if validMovement(b)
          && request.resource.data.amount is int
          && request.resource.data.amount > 0
          && request.resource.data.methodId is string
          && request.resource.data.type in ['payment', 'refund', 'overpayment_refund']
          && ((request.resource.data.get('saleId', null) is string)
              != (request.resource.data.get('openingDebtId', null) is string))
          && validPaymentByType(request.resource.data);
      }

      match /customers/{id} {
        function validCustomerFields(d) {
          return d.keys().hasOnly(['name', 'phone', 'notes', 'active',
                                   'createdAt', 'createdBy', 'updatedAt', 'updatedBy'])
            && d.name is string && d.name.size() > 0
            && (!('phone' in d) || d.phone is string)
            && (!('notes' in d) || d.notes is string)
            && d.active is bool
            && d.updatedBy == request.auth.uid
            && validDeviceTime(d.updatedAt);
        }
        allow create: if isOperator(b)
          && validCustomerFields(request.resource.data)
          && request.resource.data.active == true
          && request.resource.data.createdBy == request.auth.uid
          && validDeviceTime(request.resource.data.createdAt);
        allow update: if isOperator(b)
          && validCustomerFields(request.resource.data)
          && request.resource.data.createdBy == resource.data.createdBy
          && request.resource.data.createdAt == resource.data.createdAt
          && (request.resource.data.active == resource.data.active || isAdmin(b));
      }
    }
  }
}
```

Validación de `fechaOperativa >= startDate` en las reglas: añade una lectura facturable (el documento del negocio) por escritura, insignificante frente a la cuota gratuita con decenas de movimientos al día. La Fase 1 incluye medir en el emulador si los lotes más grandes (abono repartido, cierre con conteos, devolución con reintegro) superan el límite de accesos a documentos por solicitud de las reglas. Si lo superan, esta validación se quita de las reglas, queda solo en la app y se actualiza este documento.

Validaciones que quedan solo en la app (y en las pruebas de la sección 13), porque las reglas no pueden hacerlas o serían demasiado costosas:

- Que el total sea la suma de las líneas y que los demás campos derivados coincidan con su cálculo (§8.3).
- Que un pago no supere el saldo pendiente y el reparto del abono general (R-07); los excesos por sincronización se detectan con A-10. Que un `overpayment_refund` no supere el exceso que corrige.
- Que una devolución no supere lo vendido menos lo devuelto, que el reintegro obligatorio vaya en el mismo lote y que su monto sea el excedente correcto (R-09).
- Las dependencias de anulación (R-19), incluida la anulación conjunta de una devolución y su `refund`, y que la apertura solo se anule antes del primer movimiento posterior (§5.1).
- Que la entrega no se marque sobre una venta anulada (si ocurre por concurrencia, A-10).
- La existencia de un precio vigente para cada clasificación vendida (R-04).
- La clasificación de registros tardíos (R-21).
- El 29 de febrero en años no bisiestos.
- Los motivos obligatorios por ítem en `counts` y en pérdidas (R-11, R-12).

Limitaciones que hay que conocer:

- Cada `get()`, `exists()` o `existsAfter()` en una regla cuenta como lectura facturable: por escritura se leen el documento del miembro y el del negocio; en los reversos, también el documento destino, y en los reintegros, la devolución.
- Las reglas se evalúan cuando la escritura llega al servidor, no cuando se crea sin conexión: un miembro desactivado mientras estaba sin conexión verá rechazadas sus escrituras pendientes (RF-31).
- Nunca se despliega `allow read, write: if true` ni reglas de modo de prueba. Verificar en la consola de Firebase que las reglas publicadas son las del repositorio.
- Referencia oficial: [reglas de seguridad de Firestore](https://firebase.google.com/docs/firestore/security/get-started).

### 11.2 Acceso y cuentas

- Correo y contraseña con Firebase Authentication; recuperación de contraseña por correo. No se usa verificación por SMS por su costo.
- Una cuenta por persona; cada movimiento guarda `createdBy`.
- La app no contiene claves de servicio ni credenciales de administrador. El archivo `google-services.json` no es secreto, pero el repositorio debe ser privado.

### 11.3 Privacidad

El teléfono del cliente es opcional, es un dato personal y se recoge solo con conocimiento del cliente. No se incluye en los CSV por defecto (D-08). Si luego se publica o se amplía el uso a terceros, revisar las obligaciones de protección de datos personales aplicables en Colombia.

### 11.4 Respaldo

Firestore no se respalda solo en el plan gratuito, y la exportación administrada de Firebase exige plan de pago, así que el respaldo se hace por dos vías de bajo costo:

1. **CSV periódico desde la app** (RF-25 y RF-26): exportación completa cada mes, guardada en el almacenamiento que elija la familia (por ejemplo, Google Drive).
2. **Script de respaldo en el computador del desarrollador**: usa Firebase Admin SDK para descargar todas las colecciones a archivos JSON. La clave de servicio se guarda fuera del repositorio y nunca va en los teléfonos.

El respaldo es de consulta y auditoría; en la versión 1 no hay restauración automática. Si algún día se necesita, se construye un importador a partir de esos archivos.

## 12. Requisitos no funcionales, costos y sostenibilidad

El diseño busca costo mensual cero: sin servidor propio, sin Cloud Functions y dentro de las cuotas gratuitas de Firebase, con lecturas controladas.

### 12.1 Requisitos no funcionales

| ID | Requisito | Criterio |
| --- | --- | --- |
| RNF-01 | Facilidad de uso | Acciones frecuentes a uno o dos toques desde Inicio; formularios breves; textos en español cotidiano. *Acuerdo no automatizable* (13.0) |
| RNF-02 | Modo offline | Capturar sin internet, ver el registro tras cerrar y abrir la app, y señalar cambios pendientes |
| RNF-03 | Integridad | No borrar movimientos confirmados; guardar actor, fecha y motivo en toda corrección |
| RNF-04 | Seguridad | Autenticación individual y reglas de acceso por negocio, probadas en emulador |
| RNF-05 | Rendimiento | Pantallas principales con datos de caché en menos de 2 segundos; reportes por periodo. *Acuerdo no automatizable* (13.0) |
| RNF-06 | Compatibilidad | Android 8.0 (API 26) o superior, probado en los teléfonos reales de la familia |
| RNF-07 | Respaldo | Exportación CSV mensual y script de respaldo local (sección 11.4) |
| RNF-08 | Costo | Sin servicios de pago; revisar el consumo en la consola de Firebase cada mes durante el piloto |
| RNF-09 | Trazabilidad | Cada movimiento con fecha operativa, fecha de registro, usuario y vínculo de corrección |
| RNF-10 | Calidad | Lógica de negocio con pruebas unitarias que cubren los criterios de la sección 13 |
| RNF-11 | Accesibilidad | Respeta el tamaño de letra del sistema; contraste legible a plena luz. *Acuerdo no automatizable* (13.0) |

### 12.2 Costos

- El plan gratuito de Firebase (Spark) incluye cuotas diarias de lecturas y escrituras de Firestore, almacenamiento y transferencia. Para tres usuarios y decenas de movimientos al día, el consumo esperado es una fracción de esas cuotas, siempre que las consultas sigan la sección 8.5 (saldos desde el último conteo, rangos de fecha solo para mostrar) y los listeners se mantengan vivos (sección 10.3). Los saldos de cliente leen todo el historial de cada cliente; su costo se mide en el piloto.
- Las cuotas y condiciones cambian y no garantizan costo cero para siempre: revisar [los precios y cuotas de Firestore](https://firebase.google.com/docs/firestore/pricing) al iniciar el desarrollo y el consumo real en la consola durante el piloto.
- Cloud Functions, almacenamiento de archivos y exportaciones administradas requieren plan de pago; este diseño no los usa.
- No se usa verificación por SMS. Google Play se evalúa solo después del piloto y sus requisitos y costos se revisan en ese momento.

## 13. Criterios de aceptación y casos de prueba

El MVP se considera terminado cuando todos estos casos pasan. La columna Nivel indica dónde se prueba: Unit (módulo `domain`, JUnit), Emulador (reglas y repositorios con Firebase Emulator Suite), Estático (lint o detekt en la compilación) o Manual (teléfono real, incluido modo avión). La IA debe convertir cada fila en pruebas antes de implementar la función correspondiente.

### 13.0 Acuerdos no automatizables

Quedan fuera de las tablas de pruebas porque se cumplen por diseño, por acuerdo familiar o por revisión en el piloto, no con una prueba automática: R-06 (domicilio gratuito), la parte informativa de R-17 (saldo aproximado con bulto abierto), D-06 (un solo teléfono vende sin conexión) y RNF-01, RNF-05 y RNF-11 (facilidad de uso, rendimiento y accesibilidad, que se revisan en el piloto).

### 13.1 Ventas, pagos y huevos

| ID | Escenario | Resultado esperado | Nivel |
| --- | --- | --- | --- |
| AC-01 | Venta de 2 cubetas AA a precio X con entrega inmediata | Total 2 × X; Físico y Disponible de AA bajan 60 huevos | Unit |
| AC-02 | Venta con entrega pendiente y luego entrega | Al vender: Disponible baja 60 y PorEntregar sube 60, Físico igual. Al entregar: PorEntregar y Físico bajan 60, Disponible no cambia. Si hubo un conteo o cierre entre la venta y la entrega, la entrega entra al saldo si `deliveredAt` es posterior al `createdAt` del punto de partida, sin comparar fechas (R-21) | Unit |
| AC-03 | Intentar vender 15 huevos, docenas, fracciones, 0 o negativos, o mezclar clasificaciones en una línea | Rechazado; solo se aceptan cubetas enteras de una clasificación | Unit |
| AC-04 | Cambiar el precio del catálogo | Ventas anteriores conservan su precio; la siguiente venta precarga el nuevo | Unit |
| AC-05 | Vender con precio distinto al de lista | La línea guarda `listPrice` y `unitPrice` | Unit |
| AC-06 | Venta parcial a un cliente | Saldo del cliente igual a total menos lo pagado | Unit |
| AC-07 | Venta sin cliente con pago menor al total | No se puede confirmar (R-08) | Unit |
| AC-08 | Pago mayor al saldo de la venta | Rechazado (R-07) | Unit |
| AC-09 | Abono general con una deuda de apertura y tres ventas pendientes | Cubre primero la deuda de apertura y luego las ventas de la más antigua a la más reciente por `fechaOperativa` (a igual fecha, por `createdAt`); un pago por deuda cubierta, en un solo lote (al ser hasta 10 pagos) | Unit |
| AC-71 | Abono general que cubre 25 ventas pendientes (`MAX_PAYMENTS_PER_BATCH = 10` en `:domain`) | Se reparte en lotes de 10, 10 y 5 pagos; ningún lote supera 10 pagos; se conserva el orden de cobertura entre lotes (deudas de apertura primero, luego ventas de la más antigua a la más reciente por `fechaOperativa` y `createdAt`) (R-07, §10.2) | Unit |
| AC-72 | Abono general mayor que la deuda total del cliente | Se rechaza antes de enviar y no genera ningún lote de pagos (R-07) | Unit |
| AC-10 | Recolección de 100 AA, 20 A y 5 descartados | AA sube 100 y A sube 20; los descartados no cambian saldos y aparecen en reportes | Unit |
| AC-11 | Devolver 1 cubeta AA de una venta entregada con reingreso | Crédito igual al precio aplicado; Físico AA sube 30; saldo del cliente baja el crédito | Unit |
| AC-12 | Devolución sobre venta pendiente de entrega o por más cubetas de las vendidas | Rechazada | Unit |
| AC-13 | Devolución de una venta ya pagada completa | En el mismo lote se crea un reintegro (`type: refund`) por todo el crédito, con `saleReturnId` igual al ID de la devolución; `PendienteVenta` queda en 0, nunca negativo; el crédito no se aplica a otras ventas del cliente | Unit |
| AC-14 | Consumo propio de 10 huevos A; pérdida sin motivo | Físico A baja 10; la pérdida sin motivo se rechaza | Unit |
| AC-39 | Venta de una clasificación sin precio vigente en su `fechaOperativa`; dos precios con el mismo `validFrom` | Sin precio: no se puede agregar la línea. Empate: se precarga el de mayor `createdAt`. El vigente se elige por la `fechaOperativa` de la venta, no por hoy (R-04) | Unit |
| AC-40 | Abono general que excede la deuda total del cliente | Rechazado; no se crea ningún pago (R-07) | Unit |
| AC-41 | Devolución con crédito 20.000 sobre una venta con pendiente 5.000, reintegro en efectivo | En el mismo lote se crea un reintegro de 15.000 con `saleReturnId` de esa devolución; `PendienteVenta` queda en 0; la caja en efectivo baja 15.000; sin el reintegro la devolución no se confirma (R-09) | Unit |
| AC-42 | Dos pagos creados sin conexión que juntos exceden el saldo de una venta; pago activo sobre una venta anulada; entrega registrada sin conexión sobre una venta que otro teléfono anuló | Tras sincronizar aparece A-10 en los tres casos (en ventas sin cliente, dentro de la ventana de 7 días de Inicio) | Unit |
| AC-69 | Administrador registra `overpayment_refund` con motivo obligatorio sobre una venta con sobrepago (A-10) | La caja de la forma de pago baja, suma en `Reintegros` de `PendienteVenta`, resuelve el negativo y se puede anular por sí solo (R-07, R-19) | Unit |

### 13.2 Alimento, caja y gastos

| ID | Escenario | Resultado esperado | Nivel |
| --- | --- | --- | --- |
| AC-15 | Comprar 3 bultos de concentrado | El saldo de concentrado sube 120 kg | Unit |
| AC-16 | Agotar 1 bulto de salvado en un periodo del día 1 al día 14 | El saldo baja 40 kg; el consumo diario se calcula con `BigDecimal` como 40 entre 14 días (periodo inclusivo) y se muestra «2,9 kg/día» (un decimal, half-up solo al mostrar) | Unit |
| AC-17 | Registrar combustible pagado en efectivo | La caja en efectivo baja y el gasto aparece en la categoría combustible | Unit |
| AC-18 | Registrar un retiro personal | Resta caja, no aparece en gastos y no afecta el resultado aproximado | Unit |
| AC-19 | Compra de alimento | No aparece como gasto; las categorías de gasto no incluyen concentrado ni salvado | Unit |
| AC-20 | Cálculo de resultado aproximado | Ventas netas menos costo de alimento estimado (redondeado según AC-46) menos gastos; rotulado «aproximado» | Unit |
| AC-43 | Pérdida de 5 kg de concentrado con motivo; pérdida sin motivo (RF-17) | El saldo de concentrado baja 5 kg; sin motivo se rechaza | Unit |
| AC-44 | Conteo de alimento solo de concentrado, 70 kg (RF-17) | Concentrado parte de 70 kg; salvado conserva su punto de partida (conteo parcial, R-12) | Unit |
| AC-45 | Conteo de caja: efectivo esperado 100.000 y contado 95.000; Nequi sin contar (RF-20) | Diferencia de −5.000 con motivo obligatorio en ese ítem; Nequi conserva su punto de partida | Unit |
| AC-46 | Costo por kg con promedio no entero; sin compras en 90 días; sin ninguna compra | Se redondea al peso entero; se usa el costo de la última compra; se muestra «sin dato» | Unit |
| AC-67 | Desactivar Nequi con saldo 30.000; registrar un gasto nuevo | Nequi no se ofrece en el gasto; su caja se sigue calculando y aparece en el cierre mientras su saldo sea distinto de cero; con saldo 0 deja de aparecer (D-04) | Unit |

### 13.3 Conteos, cierre y reversos

| ID | Escenario | Resultado esperado | Nivel |
| --- | --- | --- | --- |
| AC-21 | Conteo con un ítem con diferencia distinta de cero | Muestra la diferencia y exige motivo en ese ítem; sin motivo no se confirma; los ítems sin diferencia no lo exigen | Unit |
| AC-22 | Conteo confirmado | Se vuelve el punto de partida; los movimientos creados antes no se suman de nuevo | Unit |
| AC-23 | Reverso de un movimiento anterior y de uno posterior al punto de partida | El posterior revierte su efecto; el anterior no cambia el saldo | Unit |
| AC-24 | Anular una venta con pagos activos | Rechazado hasta anular los pagos (R-19) | Unit |
| AC-25 | Apertura con saldos de huevos, alimento, caja y una deuda de cliente | Los saldos parten de esos valores; la deuda de apertura admite pagos | Unit |
| AC-26 | Cierre de jornada | Muestra esperado, contado y diferencia por elemento y guarda el resumen del día | Manual |
| AC-47 | Anular un reverso; anular un conteo o un cierre | Rechazado en la app y en las reglas (R-18, R-19) | Unit, Emulador |
| AC-48 | Anular una venta con devoluciones activas | Rechazado hasta anular las devoluciones (R-19) | Unit |
| AC-49 | Anular una deuda de apertura con pagos activos | Rechazado hasta anular los pagos (R-19) | Unit |
| AC-50 | Anular una venta entregada, una que entra al saldo y otra anterior al punto de partida | La que entra al saldo deja de contarse como entregada (Físico sube); la anterior no cambia el saldo (R-19, §9.1) | Unit |
| AC-51 | Anular la apertura después de registrar un movimiento posterior; anularla cuando solo se han creado precios | El primero se rechaza; el segundo se permite, porque crear precios no cuenta como movimiento posterior (§5.1) | Unit |
| AC-52 | Movimiento con `fechaOperativa` anterior a `startDate` | Rechazado en la app y en las reglas (§5.1) | Unit, Emulador |
| AC-53 | Alertas A-01, A-02 y A-03 | A-01 con disponible negativo en una clasificación, A-02 con saldo de alimento negativo y A-03 con un conteo con diferencia distinta de cero | Unit |
| AC-62 | Anular una devolución que tiene reintegro (`type: refund`) activo | Se crean en un solo `WriteBatch` los reversos de la devolución y del `refund`; `PendienteVenta` vuelve al valor previo a la devolución y nunca queda negativo (R-19) | Unit |
| AC-63 | Anular solo un `refund` mientras su devolución está activa; anular un `overpayment_refund` por sí solo | El primero se rechaza; el segundo se permite (R-19) | Unit |
| AC-66 | Hoy (día D) ya tiene cierre y se abre un formulario nuevo; luego el usuario fuerza la fecha D | La fecha propuesta es D+1 con aviso; con la fecha D forzada, el movimiento se excluye del saldo y se marca como registro tardío (R-21, A-08) | Unit |

### 13.4 Sincronización y offline

| ID | Escenario | Resultado esperado | Nivel |
| --- | --- | --- | --- |
| AC-27 | Registrar una venta en modo avión, cerrar y abrir la app | Sigue visible, marcada como pendiente de sincronizar | Manual |
| AC-28 | Reconectar | No se duplica y los otros teléfonos la reciben | Manual |
| AC-29 | Dos teléfonos venden sin internet la misma existencia | Tras sincronizar el disponible queda negativo y aparece la alerta A-01 | Manual |
| AC-30 | Movimientos creados después de un conteo del día D: (a) tras un cierre, con `fechaOperativa` D; (b) tras un conteo intermedio, con `fechaOperativa` D; (c) tras cualquier conteo, con `fechaOperativa` D−1; (d) entrega de una venta con `fechaOperativa` D−2, marcada tras un conteo intermedio con `deliveredAt` posterior al conteo; (e) la misma entrega, pero tras un cierre del día D | (a) y (c) se excluyen del saldo y se marcan como registro tardío (A-08); (b), (d) y (e) entran al saldo, porque las entregas solo evalúan `deliveredAt > createdAt` del conteo (R-21) | Unit |
| AC-31 | Cierre sin conexión | Se guarda localmente y queda pendiente hasta reconectar | Manual |
| AC-54 | Un miembro desactivado registra un gasto sin conexión y luego reconecta (RF-31) | La escritura se rechaza; Inicio muestra el aviso con tipo y fecha del movimiento; la interfaz sigue usable | Manual |
| AC-55 | Escritura rechazada por una regla, por ejemplo `createdAt` 10 minutos en el futuro (RF-31) | Se genera el aviso de escritura rechazada sin bloquear la interfaz | Emulador |
| AC-56 | Registrar con escrituras pendientes y luego reconectar (RF-02) | Muestra «hay cambios pendientes»; al sincronizar desaparece el aviso y se actualiza la última sincronización guardada en el teléfono | Manual |
| AC-68 | Teléfonos antes y durante la apertura: (a) teléfono sin apertura en caché; (b) administrador registra la apertura con internet (lento y normal); (c) otro teléfono recibe la apertura sincronizada | En (a) todos los teléfonos bloquean movimientos con «Esperando apertura»; en (b) se muestra indicador de progreso y sigue bloqueado hasta que el documento activo de `openingBalances` tiene `isFromCache == false` y `hasPendingWrites == false`, sin esperar la tarea de Firestore; en (c) se desbloquea al recibirla confirmada (§5.1) | Manual |
| AC-70 | Apertura rechazada al sincronizar (por ejemplo, regla incumplida o miembro desactivado) | El bloqueo «Esperando apertura» deja de esperar, muestra el error de RF-31 y permite reintentar o corregir (§5.1) | Manual |

### 13.5 Seguridad y exportación

| ID | Escenario | Resultado esperado | Nivel |
| --- | --- | --- | --- |
| AC-32 | Usuario sin documento de miembro o con `active: false` | No puede leer ni escribir | Emulador |
| AC-33 | Operador intenta crear un reverso, una apertura o cambiar precios | Rechazado | Emulador |
| AC-34 | Intento de actualizar o borrar un movimiento confirmado | Rechazado, salvo el paso de entrega de `pending` a `delivered` | Emulador |
| AC-35 | Crear un movimiento con `createdBy` distinto del usuario autenticado | Rechazado | Emulador |
| AC-36 | Revisión estática (lint o detekt) | Ningún importe usa `Float` ni `Double`; la compilación falla si aparece | Estático |
| AC-37 | Abrir los CSV en Google Sheets | Encabezados claros, fechas `YYYY-MM-DD`, clasificaciones y enteros sin separadores; incluyen anulados con su estado; sin teléfono | Manual |
| AC-38 | Alertas A-04 a A-09 | Aparecen cuando se cumplen los umbrales de la sección 9.5, contando días de calendario de Bogotá en A-04 a A-06 y horas en A-07; A-09 solo para el administrador | Unit |
| AC-57 | Crear un movimiento con `createdAt` más de 5 minutos después de `request.time`, o con `fechaOperativa` `2026-13-01` o `2026-04-31` | Rechazado por las reglas (R-22) | Emulador |
| AC-58 | Actualizar un precio; crear un reverso con `targetCollection` fuera de la lista de movimientos anulables (por ejemplo `reversals`, `counts`, `closings`, `prices` o `customers`), o con un documento destino inexistente | Rechazado por las reglas | Emulador |
| AC-59 | Marcar entrega con `deliveredBy` distinto del usuario o `deliveredAt` que no sea `Timestamp`; crear una venta `delivered` con `deliveredAt` distinto de `createdAt`; editar un cliente cambiando `createdBy` o con campos no permitidos | Rechazado por las reglas | Emulador |
| AC-60 | Generar un CSV con textos que contienen comas y comillas | El archivo empieza con BOM UTF-8; esos textos van entre comillas dobles con las comillas internas duplicadas; enteros sin separador; `estado` solo en los archivos que lo llevan; `pagos.csv` incluye `deuda_apertura_id` y `devolucion_id`, y su `cliente` se deduce de la venta o de la deuda de apertura | Unit |
| AC-61 | Exportación completa | Incluye todos los archivos de la sección 9.7; guarda la fecha en el teléfono del administrador y A-09 se calcula con esa fecha | Unit |
| AC-64 | Crear un precio sin `fechaOperativa` con `validFrom` anterior a `startDate`; crear un precio con `validFrom` `2026-02-30`, con `createdBy` ajeno, con `createdAt` 10 minutos en el futuro o como operador | El primero se acepta; los demás se rechazan (§11.1) | Emulador |
| AC-65 | Crear un `refund` sin `saleReturnId`, uno cuyo `saleReturnId` no existe al terminar el lote, un `payment` con `saleReturnId` o `reason`, un pago con `customerId`, o un `overpayment_refund` como operador, sin `reason` (o vacío) o con `saleReturnId` | Rechazado por las reglas; un `refund` con su devolución en el mismo lote y un `overpayment_refund` válido creado por el administrador se aceptan (§11.1) | Emulador |

## 14. Plan de desarrollo y estrategia con IA

El plan avanza por rebanadas usables y valida con datos reales antes de invertir en gráficos o automatizaciones. La duración depende del tiempo disponible del desarrollador; el orden y los criterios de salida son lo importante.

### 14.1 Fases

| Fase | Entregable | Requisitos | Criterio de salida |
| --- | --- | --- | --- |
| 0. Validación | Saldos iniciales contados y anotados (D-09 y D-10 cerradas en v2.3; las «Propuesta» se usan tal como están) |  | La familia sabe qué registrar y cuándo |
| 1. Fundamentos | Proyecto Android, Firebase en plan Spark, autenticación, miembros por consola, reglas con pruebas en emulador, catálogos, módulo `domain` base, revisión estática e indicador de sincronización básico. Medición de accesos a documentos en reglas completada (decisión: variante B con `startDate` en reglas y tope de 10 pagos por lote, §10.2 y §11.1) | RF-01, RF-02 (básico), RF-04, RF-05 | Acceso autorizado y datos protegidos; AC-32 a AC-36, AC-57 a AC-59, AC-64 y AC-65 pasan; medición de accesos documentada y decisión sobre `startDate` registrada en este documento |
| 2. Rebanada A: huevos y cobros | Apertura (incluido el manejo de rechazo de RF-31 y el bloqueo «Esperando apertura»), recolección, venta, entrega, pagos (`MAX_PAYMENTS_PER_BATCH = 10` en `:domain`), clientes, saldo y conteo de huevos | RF-03, RF-06 a RF-12, RF-31 (apertura) | Una jornada completa se registra en la app; AC-01 a AC-10, AC-21, AC-22, AC-25, AC-39, AC-40, AC-52, AC-68, AC-70, AC-71 y AC-72 pasan |
| 3. Rebanada B: insumos y caja | Devoluciones, egresos de huevos, alimento, gastos, retiros, conteos de alimento y caja, cierre, historial y reversos | RF-13 a RF-22 | Alimento y dinero se concilian; AC-11 a AC-19, AC-23, AC-24, AC-26, AC-41, AC-43 a AC-45, AC-47 a AC-51, AC-62, AC-63, AC-66, AC-67 y AC-69 pasan |
| 4. Rebanada C: confiabilidad | Sincronización completa, avisos de escrituras rechazadas, alertas, reportes, CSV y recordatorio de respaldo | RF-02 (completo), RF-23 a RF-26, RF-31 | Pruebas en modo avión pasan (AC-27 a AC-31, AC-38, AC-42, AC-53 a AC-56); la semana y el mes se revisan en Sheets (AC-20, AC-37, AC-46, AC-60 y AC-61) |
| 5. Piloto | Uso real en paralelo con el cuaderno y ajuste de formularios | Todos | Captura consistente durante varias semanas y diferencias de conteo explicables; lecturas de Firestore de los saldos de cliente medidas en la consola. Si resultan caras, se evalúa un corte de cartera como mejora |

### 14.2 Cómo trabajar con la IA

1. **Un repositorio, un contexto.** Guardar este documento en el repositorio (por ejemplo `docs/ESPECIFICACION.md`) y un archivo de contexto para el asistente (por ejemplo `CLAUDE.md` o `AGENTS.md`) con la sección 0 y la lista de decisiones D-xx.
2. **Una rebanada por conversación o tarea.** Pedir un requisito RF a la vez, citando sus reglas R-xx y sus casos AC-xx. Evitar pedir «toda la app».
3. **Pruebas primero.** Para cada tarea: la IA escribe las pruebas de los AC correspondientes, ve que fallan, implementa y ve que pasan.
4. **Dominio antes que pantallas.** Construir y probar los cálculos de la sección 9 en el módulo `domain` antes de conectarlos con Firestore o Compose.
5. **Reglas de seguridad siempre con emulador.** Ningún cambio en reglas se acepta sin sus pruebas de AC-32 a AC-35, AC-57 a AC-59, AC-64 y AC-65.
6. **Revisar cada cambio.** Leer el diff, ejecutar las pruebas y probar en un teléfono real en modo avión antes de pasar a la siguiente tarea.
7. **Cambios al documento primero.** Si durante el desarrollo se descubre una regla nueva, se actualiza este documento y después el código, nunca al revés.

Plantilla de solicitud para cada tarea:

```
Contexto: lee docs/ESPECIFICACION.md (secciones 0, 4, 8 y 9).
Tarea: implementar RF-xx (nombre).
Reglas que aplican: R-xx, R-xx.
Casos de aceptación: AC-xx a AC-xx.
Pasos: 1) escribe las pruebas de los AC en el módulo domain; 2) implementa la lógica;
3) implementa repositorio y pantalla; 4) actualiza reglas de Firestore y sus pruebas si toca datos.
Restricciones: no cambies nombres de colecciones ni campos; no uses Double para dinero;
si algo es ambiguo, pregúntame antes de decidir.
```

## 15. Riesgos y mitigaciones

| Riesgo | Efecto | Mitigación |
| --- | --- | --- |
| Dos dispositivos venden sin internet la misma existencia | Disponible negativo al sincronizar | Un solo teléfono designado para vender sin conexión (D-06, acuerdo no automatizable); alerta A-01 |
| No se registran descartes ni pérdidas | Faltantes sin explicación | Campo de descartados en la recolección, registro de pérdidas, conteos periódicos con motivo |
| Se sobrescribe un precio | Se pierde el precio histórico | Precios como documentos nuevos con vigencia; cada línea guarda precio de lista y aplicado |
| Bulto parcialmente abierto | Consumo promedio impreciso y saldo desfasado tras un conteo | Conteo semanal de alimento; mejora posible midiendo el sobrante del bulto abierto (9.3) |
| Duplicados o registros incompletos | Saldos no confiables | IDs creados en el dispositivo, campos mínimos obligatorios, resumen antes de guardar, `WriteBatch` |
| Reglas de Firestore abiertas | Exposición de información | Reglas con pruebas en emulador; nunca modo de prueba ni acceso universal |
| Confundir caja con ganancia | Decisiones equivocadas | Caja, compras, consumo y resultado se muestran separados; el resultado se rotula «aproximado» |
| Reloj del teléfono incorrecto | Movimientos mal ordenados respecto a los conteos | Fecha y hora automáticas; las reglas rechazan un `createdAt` mayor que `request.time` más 5 minutos (R-22). Un reloj atrasado no se detecta |
| Borrar datos o desinstalar con registros pendientes | Se pierden registros que solo estaban en ese teléfono | Aviso antes de cerrar sesión con pendientes; sincronizar pronto |
| Escritura rechazada al sincronizar (miembro desactivado, regla incumplida) | El movimiento desaparece de la caché sin que nadie lo note | Aviso de escritura rechazada (RF-31). Si la app se cerró antes de sincronizar, el rechazo no se puede atribuir a un movimiento concreto; el siguiente conteo revela la diferencia |
| Pagos concurrentes sin conexión sobre la misma venta, pago sobre una venta anulada en otro teléfono o entrega sobre una venta anulada | Venta sobrepagada, pago huérfano o entrega sin efecto en el saldo | Alerta A-10 (en ventas sin cliente, dentro de la ventana de 7 días de Inicio). El administrador corrige el sobrepago con un `overpayment_refund` (R-07) y anula el pago sobre la venta anulada; la entrega sobre venta anulada la corrige el siguiente conteo |
| La caché local crece sin límite | Más espacio en el teléfono y consultas locales más lentas con los años | Caché sin límite para no perder documentos (§10.1); revisar el tamaño y el tiempo de respuesta (RNF-05) en el piloto |
| Lecturas de Firestore por encima de la cuota gratuita | Costo inesperado o bloqueo del servicio | Consultas según la sección 8.5, saldos desde el último conteo, revisión mensual del consumo; medición de los saldos de cliente en el piloto y corte de cartera como mejora si resultan caros |
| MVP demasiado grande para un solo desarrollador | Retrasos y abandono | Tres rebanadas usables; si hace falta recortar, se posponen alertas y reportes antes que registros |
| Cuenta de Firebase en manos de una sola persona | Pérdida de acceso al proyecto | Agregar una segunda cuenta de la familia como propietaria del proyecto; repositorio privado con copia |
| Pérdida de datos de Firestore | Historial irrecuperable | CSV mensual y script de respaldo local (11.4) |

## 16. Glosario, referencias y pendientes

### 16.1 Glosario

| Término | Definición |
| --- | --- |
| Clasificación | B, A, AA, AAA o JUMBO (`classification` en el código) |
| Cubeta | Única presentación de venta: 30 huevos de una sola clasificación |
| Bulto | Unidad de alimento de 40 kg (concentrado o salvado de maíz) |
| Agotamiento | Registro de que un bulto terminó; descuenta 40 kg |
| Físico | Huevos que físicamente deberían estar en casa: parte del último conteo y cambia con recolecciones, entregas, devoluciones y egresos |
| Por entregar | Huevos de ventas confirmadas cuya entrega sigue pendiente |
| Disponible | Físico menos por entregar: lo que aún se puede vender |
| Punto de partida | Último conteo (o apertura) a partir del cual se calcula un saldo |
| Conteo físico | Cantidad contada y comparada con la esperada |
| Apertura | Saldos iniciales registrados una sola vez al empezar a usar la app |
| Reverso | Movimiento que anula otro sin borrar el historial |
| Pendiente de sincronizar | Registro local aún no confirmado por el servidor |
| Registro tardío | Movimiento creado después de un conteo cuya `fechaOperativa` no supera la del conteo (o es anterior, si el conteo es intermedio); en las entregas se usan `deliveredAt` y su fecha en Bogotá. Se excluye del saldo y genera A-08 (R-21) |
| Reintegro | Pago `type: refund` (excedente de una devolución sobre lo pendiente de la venta; lleva `saleReturnId`, va en el mismo lote y se anula junto con ella, R-09, R-19) o `type: overpayment_refund` (corrección de un sobrepago por el administrador con `reason` obligatorio, R-07). Ambos restan caja |
| Resultado aproximado | Indicador de gestión que no equivale a utilidad ni a estado contable |

### 16.2 Pendientes por confirmar

No quedan decisiones de producto pendientes que bloqueen la Fase 1. Solo quedan verificaciones técnicas o de piloto:

- Verificar los precios y cuotas vigentes de Firestore antes de iniciar la Fase 1.
- Verificación en proyecto real de Firebase (Fase 1, Tarea 5): probar lotes de 10, 11 y 20 pagos (`type: payment`) con la variante B desplegada para confirmar el comportamiento del límite de 20 accesos en servidor (§10.2).
- Medición de lecturas de saldos de cliente en el piloto (Fase 5): evaluar un corte de cartera si resulta caro (§10.3).
- Reevaluación de D-10 al final del piloto: si el operador puede anular sus propios registros del mismo día. Las decisiones «Propuesta» de la sección 5 también pueden ajustarse durante el piloto actualizando primero este documento.

### 16.3 Cambios respecto a la versión 1.0

| Cambio | Motivo |
| --- | --- |
| Dos roles en lugar de tres | Tres usuarios no justifican un tercer rol en el MVP |
| Apertura de saldos (RF-03), con deudas de clientes y caja inicial | La v1.0 usaba «saldo inicial» sin definirlo |
| Devoluciones (RF-13) y egresos de huevos (RF-14) | La fórmula de la v1.0 los mencionaba sin funcionalidad que los registre |
| Físico, por entregar y disponible (R-05) | Una venta con entrega pendiente hacía fallar el conteo físico |
| Descartados como total general; roturas posteriores por clasificación | La v1.0 restaba del inventario huevos descartados que nunca habían entrado |
| Compras de alimento fuera de las categorías de gasto (R-14) | La v1.0 las listaba como compra y como gasto, lo que duplicaba el costo |
| Reglas de pagos: siempre ligados a una venta, sin saldo a favor, venta sin cliente pagada completa | La v1.0 dejaba ambiguo a qué se aplicaba un pago |
| Saldos calculados desde el último conteo (9.1) | Evita leer todo el historial y sobrepasar la cuota gratuita |
| Reversos como documentos aparte y «pendiente de sincronizar» como estado derivado | Mantiene los movimientos inmutables y evita un campo que Firestore no gestiona |
| Esquema de Firestore, reglas borrador y CSV definidos | Para que la IA no invente nombres ni estructuras |
| Criterios de aceptación de 13 a 38, con nivel de prueba | Para que cada función tenga una prueba verificable |
| Recordatorio y script de respaldo | El respaldo de la v1.0 dependía de recordarlo |

#### Cambios de v2.0 a v2.1

| Cambio | Motivo |
| --- | --- |
| Saldo a favor eliminado: el excedente del crédito de una devolución se reintegra obligatoriamente en el mismo `WriteBatch`; `PendienteVenta` nunca es negativo (§2.3, R-07, R-09, §9.4, RF-13, AC-13, AC-41) | §2.3 y R-07 negaban el saldo a favor que R-09 y AC-13 creaban |
| Regla de registros tardíos: entra al saldo solo con `createdAt` posterior y `fechaOperativa` mayor (cierre) o mayor o igual (conteo intermedio); si no, se excluye y genera A-08 (R-21, §9.1, AC-30) | La v2.0 sumaba al saldo movimientos que el conteo ya reflejaba |
| `createdAt` del dispositivo como orden; reglas rechazan `createdAt` más de 5 minutos en el futuro (R-22, §10.2, §11.1, §15, AC-57) | La alerta «createdAt frente a syncedAt» daba falsos positivos con desconexiones largas |
| Conteos y cierres no se anulan; la apertura solo antes del primer movimiento posterior y registrada con internet (§3, R-12, R-18, §5.1, AC-47, AC-51) | Anular un punto de partida obligaba a releer historial y reabría saldos ya conciliados |
| Anular significa «nunca ocurrió»; nuevas dependencias de anulación para devoluciones con reintegro y deudas de apertura con pagos (R-18, R-19, AC-48 a AC-50) | La v2.0 no decía qué pasaba con los huevos de una venta entregada anulada ni con estas dependencias |
| Entrega marcada por error no se deshace; una venta creada `delivered` lleva `deliveredAt = createdAt` (R-05, R-18, §8.3) | `deliveredAt` no estaba definido en ventas entregadas al crearse |
| Abono general: deudas de apertura primero, luego por `fechaOperativa` y `createdAt`; se rechaza si excede la deuda total (R-07, AC-09, AC-40) | «Más antigua» no tenía criterio de orden ni manejo del exceso |
| «Responsable» es `createdBy` (RF-06, RF-18) | El campo no existía en el esquema |
| Precios solo se crean, pasan por `validMovement`; vigente por la `fechaOperativa` de la venta, desempate por `createdAt`; sin precio no se vende (R-04, §8.2, §11.1, AC-39, AC-58) | Las reglas permitían editar precios y el precio vigente no estaba definido para ventas con fecha pasada |
| Exportación completa definida, fecha guardada en cada teléfono; CSV con `estado` solo donde aplica, `registrado_por` uniforme, `deuda_apertura_id` y deudas de apertura; nuevos archivos de catálogos, apertura y cierres (RF-26, §9.7, AC-60, AC-61) | Columnas inconsistentes y respaldo incompleto en la v2.0 |
| D-06, R-06, la parte informativa de R-17 y RNF-01/05/11 reclasificados como acuerdos no automatizables (§4, §5, §12, §13.0) | No se pueden traducir a pruebas automáticas |
| Fase 0 solo con D-09 y D-10; las «Propuesta» se usan tal como están (§1, §14.1, §16.2) | La Fase 0 incluía decisiones ya cerradas |
| Hilt (D-14) y `businessId` fijo en `BuildConfig` (D-15) (§0, §10.1, §11.1) | Decisiones técnicas que quedaban abiertas |
| Redondeo del costo por kg al peso, respaldo con la última compra o «sin dato», periodos inclusivos y unidades de tiempo de las alertas (§0, §9.3, §9.4, §9.5, AC-16, AC-46) | Fórmulas sin regla de redondeo ni caso de división por cero |
| Conteos con motivo por ítem, parciales permitidos y huevos siempre en huevos; tipos de `deliveredAt`, `startDate` y `validFrom` (R-12, §7.8, §8.3, AC-21, AC-44, AC-45) | Ambigüedades del esquema de `counts` y de tipos de fecha |
| RF-31 y avisos de escritura rechazada (§6, §7.1, §10.2, AC-54, AC-55) | Sin esperar la escritura, un rechazo del servidor pasaba inadvertido |
| Alerta A-10 por pago que excede el saldo o sobre venta anulada (§9.5, AC-42) | Las reglas no pueden impedir pagos concurrentes sin conexión |
| Caché de Firestore sin límite de tamaño (§10.1, §15) | Evitar que el SDK descarte documentos necesarios para saldos y reportes |
| RF-02 muestra «hay cambios pendientes» y la última sincronización guardada localmente (§6, §7.1, §10.2, AC-56) | El SDK no expone una cantidad exacta de escrituras pendientes |
| Reglas reforzadas: reversos con destino existente y colección permitida, entrega con `deliveredBy` y `deliveredAt` validados, clientes con campos validados y `createdBy` inmutable, fecha válida, `createdAt` acotado y `fechaOperativa >= startDate`; lista de validaciones que quedan en la app (§11.1, AC-52, AC-57 a AC-59) | El borrador de la v2.0 dejaba huecos de validación |
| AC-36 pasa a revisión estática y se asigna a la Fase 1 (§10.1, §13, §14.1) | No era una prueba unitaria y no tenía fase |
| §8.5 y §10.3 reescritos con consultas por pantalla y cálculo: saldos por `createdAt`, `fechaOperativa` solo para mostrar | La v2.0 limitaba por `fechaOperativa` consultas que deben ir por `createdAt` |
| Referencias reconciliadas: A-07 y A-08 entre §7.1 y §9.5, cita de D-06 en §9.2, notificaciones push sin RF en §2.3 y §10.2.8 limitado a avisar al cerrar sesión | Inconsistencias entre secciones de la v2.0 |
| Nuevos AC-39 a AC-61 y fases actualizadas (§13, §14.1) | Reglas y requisitos sin criterio de aceptación |

#### Cambios de v2.1 a v2.2

| Cambio | Motivo |
| --- | --- |
| La apertura es la única excepción a «no esperar escrituras»: se observa `hasPendingWrites` falso con indicador de progreso y bloqueo de otros movimientos, sin esperar la tarea (§0.5, §5.1, §10.2.3, AC-68) | §5.1 exigía esperar la confirmación del servidor y contradecía §0.5 y §10.2.3 |
| Las entregas se evalúan con `deliveredAt` y su fecha en Bogotá frente al punto de partida (R-21, §9.1, §8.5, AC-02, AC-30) | Con la `fechaOperativa` de la venta, una entrega posterior a un conteo quedaba excluida como tardía |
| `payments.saleReturnId`, obligatorio en `refund` y ausente en los demás tipos, validado en las reglas con `existsAfter` (§8.3, §11.1, R-09, AC-13, AC-41, AC-65) | No había forma de ligar un reintegro con su devolución |
| Devolución y reintegro se anulan juntos en un solo lote; no se anula un reintegro por separado mientras su devolución esté activa (R-19, §7.9, §10.2.4, AC-49, AC-62, AC-63) | Anular primero el reintegro dejaba `PendienteVenta` negativo |
| `PendienteVenta` no negativo se garantiza al confirmar en un mismo dispositivo; si queda negativo por concurrencia, A-10 y corrección del administrador con reintegro (R-07, §9.4, §15) | Las reglas no pueden impedir pagos concurrentes sin conexión |
| A-09 y la exportación completa solo para el administrador (§3, §7.1, §9.5, RF-26, AC-38, AC-61) | Con la fecha guardada por teléfono, los operadores habrían visto A-09 siempre |
| Precios fuera de `validMovement`: sin `fechaOperativa` ni restricción de `startDate`; regla propia; crearlos no impide anular la apertura; `precios.csv` sin `fecha` (§5.1, §8.1, §8.2, §9.7, §11.1, AC-51, AC-64) | Los precios se crean antes o fuera de la operación diaria y no son movimientos |
| Fecha por defecto del día siguiente cuando hoy ya tiene cierre, con aviso; forzar la fecha cerrada la marca tardía (R-21, §7, §7.2, §8.5, §9.1, AC-66) | Evitar registros tardíos involuntarios después del cierre |
| Consumo diario con `BigDecimal` y un decimal half-up solo al mostrar (§9.3, §7.6, AC-16) | La fórmula no daba un entero y no tenía regla de presentación |
| Formas de pago desactivadas: ocultas en registros nuevos, con caja calculada y presentes en el cierre mientras su saldo sea distinto de cero (D-04, §7, §7.8, §8.2, §9.4, AC-67) | No estaba definido qué pasaba con su caja |
| Eliminado `payments.customerId`; el cliente se deduce de la venta o de la deuda de apertura; `pagos.csv` con `devolucion_id` (§8.3, §8.5, §9.7, §10.3, AC-60, AC-65) | El campo era opcional y ninguna consulta lo usaba |
| A-10 también cubre una entrega registrada sobre una venta anulada (§9.5, §8.5, §7.1, §15, AC-42) | La entrega concurrente sobre una venta anulada no tenía alerta |
| RF-31 añadido a la rebanada C (§2.4) | Faltaba en el resumen de rebanadas |
| Medición del costo de los saldos de cliente en el piloto y corte de cartera como posible mejora (§8.5, §10.3, §12.2, §14.1, §15) | Se acepta su costo, pero hay que verificarlo con datos reales |
| Tarea de la Fase 1 para medir los accesos a documentos de las reglas en lotes grandes y decidir dónde se valida `startDate` (§5.1, §11.1, §14.1) | La viabilidad de esa validación en las reglas no estaba comprobada |
| Reversos limitados en las reglas a colecciones de movimientos anulables (§8.3, §11.1, AC-58) | La regla de la v2.1 permitía «anular» catálogos, clientes o miembros |
| Pagos con lista cerrada de campos y `methodId` obligatorio en las reglas (§11.1) | Necesario para rechazar `customerId` y `saleReturnId` indebidos |
| Correcciones de coherencia: auditoría de catálogos en §8.3, ajuste de «Propuesta» durante el piloto en §5, D-10 sin referencia a una versión del documento, §12.2 alineada con §8.5, exportación completa en el teléfono del administrador en §10.3 | Incoherencias encontradas en la lectura completa de la v2.1 y la v2.2 |
| Tres pendientes nuevos en §16.2: reintegro de corrección sin devolución, entregas después de un cierre y bloqueo por apertura en los demás teléfonos | Contradicciones que aparecen al combinar las decisiones de la v2.2 y que requieren una decisión |

#### Cambios de v2.2 a v2.3

| Cambio | Motivo |
| --- | --- |
| Nuevo tipo `overpayment_refund` en `payments`: solo administrador, exige `reason` y sin `saleReturnId`; `refund` queda exclusivo de devoluciones y se anula junto con ellas, mientras `overpayment_refund` se anula solo; ambos suman en `Reintegros` y restan caja (R-07, R-09, R-19, §8.3, §9.4, §9.7, §11.1, §15, AC-62, AC-63, AC-65, AC-69) | La v2.2 exigía `saleReturnId` en todo reintegro pero usaba reintegros sin devolución para corregir sobrepagos |
| Entregas exentas de comparación de fechas en R-21: solo evalúan `deliveredAt > createdAt` del punto de partida (R-21, §9.1, §8.5, AC-02, AC-30) | Una entrega después de un cierre quedaba excluida como registro tardío pese a ser un hecho físico real |
| Todos los teléfonos bloquean movimientos con «Esperando apertura» hasta ver una apertura activa confirmada (`isFromCache == false` y `hasPendingWrites == false`); si la apertura se rechaza al sincronizar, el bloqueo se levanta con el error de RF-31 y permite reintentar o corregir (§5.1, §10.2, RF-31, AC-68, AC-70) | Evitar que otros teléfonos registren movimientos antes de tener la apertura en caché y evitar bloqueo infinito si falla |
| Se permite más de un cierre en el mismo día; vale el último conteo de cada clave (§7.8, §9.1) | No estaba explícito si un día admitía varios cierres |
| D-09 y D-10 cerradas: desarrollador = administrador, madre y hermano = operadores; solo administrador anula hasta el final del piloto (§3, §5, §14.1, §16.2) | Desbloquear la configuración de miembros de la Fase 1 |
| A-10 en ventas sin cliente limitada a la ventana de 7 días de Inicio (§8.5, §9.5, §15, AC-42) | Documentar la limitación aceptada de la v1 |
| §16.2 limpiado: solo quedan pendientes de verificación o piloto (precios/cuotas, medición de lecturas en reglas, medición de saldos de cliente y reevaluación de D-10) | Los bloqueos de producto quedaron resueltos |

#### Cambios de v2.3 a v2.4

| Cambio | Motivo |
| --- | --- |
| §11.1 adopta la variante B (un `get()` del miembro con `let`, sin `exists()` previo) manteniendo `startDate` en `validMovement`; presupuesto de accesos documentado (`payment` = 2, `refund` = 3, `overpayment_refund` = 3, devolución+`refund` = 5, cierre = 8) (§10.2, §11.1) | La variante anterior (4 accesos por pago) limitaba el lote a 5 pagos según el tope oficial de 20 accesos por `WriteBatch` |
| Abono repartido en uno o varios `WriteBatch` atómicos de hasta 10 pagos (`MAX_PAYMENTS_PER_BATCH = 10` en `:domain`); rechazo por exceso antes del primer lote; manejo de fallo parcial y nuevo `AC-71` (R-07, §7.4, §10.2, §14.1) | Permitir abonos con más de 10 deudas pendientes sin superar el límite de 20 accesos por `WriteBatch` |
| §14.1, §16.2 y §16.4 actualizados: medición en emulador cerrada, verificación en proyecto real (10, 11 y 20 pagos) movida a Tarea 5 y referencia oficial de condiciones de reglas añadida | El emulador no valida el límite de 20 accesos por lote; la comprobación definitiva requiere proyecto real |

### 16.4 Referencias técnicas

Revisarlas al iniciar el desarrollo, porque características y condiciones pueden cambiar.

- [Jetpack Compose (Android)](https://developer.android.com/develop/ui)
- [Firestore: acceso sin conexión](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Firestore: reglas de seguridad](https://firebase.google.com/docs/firestore/security/get-started)
- [Firestore: condiciones de reglas de seguridad](https://firebase.google.com/docs/firestore/security/rules-conditions)
- [Firestore: precios y cuotas](https://firebase.google.com/docs/firestore/pricing)
- [Firebase Emulator Suite](https://firebase.google.com/docs/emulator-suite)
- [Firebase App Distribution](https://firebase.google.com/docs/app-distribution)

Fin de la especificación v2.4.
