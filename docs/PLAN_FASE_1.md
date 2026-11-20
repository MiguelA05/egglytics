# Plan de la Fase 1: Fundamentos

Basado en [ESPECIFICACION.md](ESPECIFICACION.md) v2.3.

## 1. Decisiones técnicas confirmadas para Fase 1

- **Estructura multimódulo:** `:domain` como módulo Kotlin puro (`java-library` + `kotlin("jvm")`, sin dependencias de Android ni Firebase) y `:app` como módulo Android (`com.android.application`) con Jetpack Compose, Material 3, Hilt y `BuildConfig.BUSINESS_ID`.
- **Toolchain fijado (Tarea 1a):** `AGP 8.13.2` + `Gradle 8.13` (wrapper) + `Kotlin 2.2.21` + `KSP 2.3.0` + `Hilt 2.57.2` + `Compose BOM 2025.12.00` + `compileSdk 36` / `targetSdk 36` / `minSdk 26`, ejecutando Gradle con JDK 21 y compilando a bytecode 17. Se eligió `Compose BOM 2025.12.00` (Compose `1.10`) porque `2026.09.00` (Compose `1.12.1`) exige AGP `9.1+` y `compileSdk 37`.
- **Regla de IDE:** No aceptar el asistente de actualización de AGP (*AGP Upgrade Assistant*) de Android Studio mientras estemos en esta línea. La migración a AGP 9 queda como una tarea futura separada.
- **Pruebas de reglas de seguridad:** TypeScript/Node.js con `@firebase/rules-unit-testing` contra Firebase Emulator Suite.
- **Pruebas de repositorios (Tarea 5):** Kotlin/JUnit contra Firebase Emulator Suite.
- **Revisión estática (`AC-36`):** La cumple la prueba JUnit `NoFloatingPointTypesTest` en `:domain` (escanea `domain/src/main/kotlin`), con `detekt 1.23.8` en `:domain` como complemento. Lo que `NoFloatingPointTypesTest` **no** cubre: inferencia de tipos sin anotación ni literal flotante, `DoubleArray`/`FloatArray`, `toDoubleOrNull`/`toFloatOrNull`, notación científica sin punto (`1e3`), literales con guion bajo (`1_000.5`) y archivos de test (`domain/src/test/kotlin`).

## 2. Orden y dependencias

1. **Tarea 1a (Hecha — `fa9e11d`):** Proyecto Gradle multimódulo (`:app` y `:domain`), Hilt, `BuildConfig` con `businessId`, `google-services.json.example`.
2. **Tarea 1b (Hecha — `f5dff57`):** Detekt `1.23.8` y `NoFloatingPointTypesTest` (`AC-36`) en `:domain`.
3. **Tarea 2a (Hecha — `c23ea8f`):** Enumeraciones (`EggClassification`, `FeedIngredient`, `MemberRole`, `PaymentType`) y `OperationalDate` en `:domain`.
4. **Tarea 2b (Hecha — `fb2b85e`):** Modelos (`Member`, `Price`, `ExpenseCategory`, `PaymentMethod`), `ActivePriceResult` y `GetActivePriceUseCase` en `:domain`.
5. ~~**Tarea 3a-1:** Configuración de emulador y suite TypeScript (`firebase.json`, `firestore-tests/`, `.gitignore`) con 1 prueba trivial de reglas.~~ *(Hecha en `72077bb`)*
6. ~~**Tarea 3a-2:** Reglas mínimas de Firestore (`members`, `businesses`, `payments`, `validMovement` y regla provisional de `saleReturns`) con sus pruebas en `@firebase/rules-unit-testing`.~~ *(Hecha en `be8a80b`)*
7. ~~**Tarea 4:** Medición en el emulador de accesos a documentos en reglas con lotes grandes + contraste con documentación oficial vigente de Firestore; decisión sobre dónde validar `startDate` (`§11.1`).~~ *(Hecha — pendiente de commit)*
8. **Tarea 3b:** Resto de reglas de Firestore (`prices`, `expenseCategories`, `paymentMethods`, `reversals`, `sales`, `customers`, `openingBalances` y demás colecciones) y sus AC en el emulador.
9. **Tarea 5:** Capa `data`: inicialización de Firestore offline (`CACHE_SIZE_UNLIMITED`), Firebase Auth y repositorio de sesión/miembros probado en Kotlin contra el emulador.
10. **Tarea 6:** Estado básico de sincronización (`RF-02` básico) y aviso al cerrar sesión con pendientes.
11. **Tarea 7:** Capa `data` y `ui`: Login, navegación por rol y pantallas de Catálogos (solo Administrador).

## 3. Detalle de tareas (una por commit)

### Tarea 1a — Proyecto Android, módulos `:app` y `:domain`, Hilt y `BuildConfig` *(Hecha)*
- **Objetivo:** Crear la estructura base de Gradle con `:domain` (Kotlin JVM puro) y `:app` (Android API 26+, Compose, Material 3, Hilt, `BuildConfig.BUSINESS_ID` y `google-services.json.example`).
- **Requisitos / Reglas:** `D-01`, `D-13`, `D-14`, `D-15`, `§0`, `§10.1`.
- **AC:** Estructura base compilable.
- **Archivos / Módulos:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `domain/build.gradle.kts`, `app/build.gradle.kts`, `app/google-services.json.example`, `app/src/main/...`.
- **Verificación:** Compilación de `:domain` y `:app` (`.\gradlew.bat clean :domain:test :app:compileDebugKotlin` → `BUILD SUCCESSFUL`). Commit: `fa9e11d` (y `a24cd0d` para dejar de rastrear `.idea/`).

### Tarea 1b — Detekt y `AC-36` *(Hecha)*
- **Objetivo:** Configurar Detekt (`1.23.8`) en `:domain` y la prueba JUnit `NoFloatingPointTypesTest` que prohíbe `Float` y `Double` en `domain/src/main/kotlin`.
- **Requisitos / Reglas:** `§0` (Dinero como `Long`), `§10.1`.
- **AC:** `AC-36` (cubierto por `NoFloatingPointTypesTest`, con `detekt` como complemento).
- **Archivos / Módulos:** `config/detekt/detekt.yml`, `build.gradle.kts`, `gradle/libs.versions.toml`, `domain/build.gradle.kts`, `domain/src/test/kotlin/com/egglytics/domain/NoFloatingPointTypesTest.kt`.
- **Verificación:** Fallo demostrado con archivo temporal con `Double`, archivo borrado y `.\gradlew.bat clean :domain:detekt :domain:test` → `BUILD SUCCESSFUL`. Commit: `f5dff57`.
- **Mejora menor pendiente:** Asegurar que el detalle `archivo:línea` del fallo de `NoFloatingPointTypesTest` se imprima completo en consola de Gradle (por ejemplo configurando `testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }` en `:domain`) sin necesidad de abrir el reporte HTML/XML.

### Tarea 2a — Enumeraciones y `OperationalDate` en `:domain` *(Hecha)*
- **Objetivo:** Implementar `EggClassification` (`B`, `A`, `AA`, `AAA`, `JUMBO`), `FeedIngredient` (`concentrate`, `corn_bran`), `MemberRole` (`admin`, `operator`), `PaymentType` (`payment`, `refund`, `overpayment_refund`) y `OperationalDate` (parseo estricto `YYYY-MM-DD`, formateo, zona `America/Bogota`).
- **Requisitos / Reglas:** `R-02`, `R-13`, `§0`, `§3`, `§8.3`.
- **Archivos / Módulos:** `domain/src/main/kotlin/com/egglytics/domain/model/`, `domain/src/test/kotlin/com/egglytics/domain/model/`.
- **Verificación:** Pruebas JUnit con `TODO()` fallando primero, luego implementación y `.\gradlew.bat clean :domain:detekt :domain:test` en verde. Commit: `c23ea8f` (y `1e72b0a` para `docs/PLAN_FASE_1.md`).

### Tarea 2b — Modelos de catálogos, `ActivePriceResult` y `GetActivePriceUseCase` *(Hecha)*
- **Objetivo:** Implementar en `:domain` (Kotlin puro, sin Android ni Firebase) `Member`, `Price` (`pricePerBucket: Long > 0L`, `validFrom: LocalDate`, `createdAt: Instant`), `ExpenseCategory`, `PaymentMethod`, `ActivePriceResult` (`sealed interface`: `Found(price)` / `NotFound`, para forzar el manejo explícito de `R-04` «sin precio no se permite vender») y `GetActivePriceUseCase` (función pura que recibe `fechaOperativa: LocalDate` sin usar reloj del sistema).
  - **Tipos fuera de Tarea 2 (Fases 2 y 3):** movimientos operativos (`Harvest`, `Sale`, `Payment`, `SaleReturn`, `EggOutflow`, `Feed*`, `Expense`, `Withdrawal`, `Count`, `Closing`, `Reversal`, `OpeningBalance`, `Customer`) y cálculos de saldos/alertas/CSV.
  - **Frontera con `:data`:** La conversión entre `String YYYY-MM-DD` / `Timestamp` de Firestore y `LocalDate` / `Instant` de dominio es responsabilidad de la capa `data` (Tarea 5).
- **Requisitos / Reglas:** `RF-04`, `R-04`, `D-02`, `D-03`, `D-04`.
- **AC:** `AC-04` (catálogo), `AC-39`.
- **Casos de prueba de precio vigente:** coincidencia exacta (`validFrom == fechaOperativa`), vigente anterior, frontera futura (`validFrom == fechaOperativa + 1 día` excluido), fecha fija lejana (`2030`) demostrando independencia del reloj del sistema, cambio de catálogo (`AC-04`), empate en `validFrom` desempatado por `createdAt`, empate doble (`validFrom` y `createdAt` iguales) desempatado determinísticamente por `id` mayor (nota: es determinista pero arbitrario porque los IDs de Firestore son aleatorios; se documentará en `ESPECIFICACION.md` después), lista en orden aleatorio (mismo resultado), `NotFound` (lista vacía / solo futuros / otra clasificación), aislamiento por clasificación y rechazo de `pricePerBucket <= 0L`.
- **Archivos / Módulos:** `domain/src/main/kotlin/...`, `domain/src/test/kotlin/...`.
- **Verificación:** `.\gradlew.bat clean :domain:detekt :domain:test` → `BUILD SUCCESSFUL` (25 pruebas en verde, `detekt` sin hallazgos). Commit: `fb2b85e`.
- **Mejora menor pendiente:** Que la prueba de orden aleatorio en `GetActivePriceUseCaseTest` use varias permutaciones (lista invertida y varias semillas distintas), en lugar de una sola semilla (`Random(42)`).

### Tarea 3a-1 — Configuración del emulador y prueba trivial *(Hecha en `72077bb`)*
- **Objetivo:** Configurar `firebase.json` (Firestore Emulator `127.0.0.1:8080`, proyecto `demo-egglytics-test`), `firestore.rules` mínimo de arranque, `firestore-tests/` (TypeScript + `@firebase/rules-unit-testing`), `.gitignore` (`firestore-tests/node_modules/`) y una prueba trivial que arranque el emulador y pase, para validar Node 24 y JDK 21.
- **Archivos / Módulos:** `firebase.json`, `firestore.rules`, `firestore-tests/package.json`, `firestore-tests/tsconfig.json`, `firestore-tests/test/smoke.test.ts`, `.gitignore`.
- **Verificación:** `firebase emulators:exec --project demo-egglytics-test --only firestore "npm --prefix firestore-tests test"` en verde (`1 pass, 0 fail`).

### Tarea 3a-2 — Reglas mínimas de Firestore y pruebas base *(Hecha en `be8a80b`)*
- **Objetivo:** Completar `firestore.rules` con las funciones base (`memberData`, `businessData`, `isActive`, `isAdmin`, `isOperator`, `validDate`, `validDeviceTime`, `validMovement`) y las reglas de `businesses`, `members` (solo lectura para miembros activos), `payments` (sus 3 tipos con lista cerrada de campos) y una regla temporal en `saleReturns` marcada con el comentario `// PROVISIONAL: sustituir en la Tarea 3b`.
- **Requisitos / Reglas:** `RF-01`, `RF-05`, `R-07`, `R-09`, `R-22`, `§11.1`.
- **AC:** `AC-32`, `AC-35`, `AC-57`, `AC-65` (y parciales de `AC-33`, `AC-34`, `AC-52`).
- **Archivos / Módulos:** `firestore.rules`, `firestore-tests/test/payments_and_base.test.ts`.
- **Verificación:** `17 tests, 2 suites, 17 pass, 0 fail` contra Firebase Emulator Suite.
- **Hallazgos verificados en 3a-2:**
  - **Sintaxis de `§11.1` confirmada en el emulador (`cloud-firestore-emulator v1.21.0`):** funciones declaradas dentro de `match /payments/{id}`, `existsAfter(...)` dentro de `WriteBatch`, `request.time + duration.value(5, 'm')`, `businessData(b).startDate` y la regex RE2 de `validDate` funcionan sin errores de compilación ni evaluación.
  - **Limitación conocida (decisión A):** la regex de `validDate` valida formato `YYYY-MM-DD` y rangos básicos de mes/día, por lo que acepta fechas de calendario inválidas como `2026-02-29` (año no bisiesto); la validación de calendario exacto se mantiene en `:domain` (`OperationalDate`).
  - **Pruebas de mutación (M1–M5, ejecutadas contra el emulador):**
    - **M1** (quitar `d.createdBy == request.auth.uid` en `validMovement`): falla `4a/4b (AC-35)` — protege la autoría del movimiento (`createdBy` debe coincidir con el usuario autenticado).
    - **M2** (quitar `&& isAdmin(b)` en `overpayment_refund` de `validPaymentByType`): falla `10a-10d (AC-65, AC-33)` en el caso `10b` — protege que solo `ADMIN` pueda emitir `overpayment_refund`.
    - **M3** (relajar regex de `validDate` a `^.*$`): falla `7a/7b/7c (AC-57)` en el caso `7b` — protege el formato válido de `fechaOperativa`.
    - **M4** (quitar `&& d.fechaOperativa >= businessData(b).startDate` en `validMovement`): fallan `5a/5b` y `5c` — protege que `fechaOperativa` no sea anterior a `startDate` del negocio.
    - **M5** (quitar `existsAfter(...)` en `refund` de `validPaymentByType`): falla `9a-9d (AC-65)` en el caso `9c` — protege que todo `refund` esté atado atómicamente a un `saleReturn` existente tras el lote (`existsAfter`).
  - **Costo de lecturas a medir en Tarea 4:** las reglas actuales leen `memberData(b)` (para `isActive`/`isAdmin`/`isOperator`) y `businessData(b).startDate` (en `validMovement`) en cada escritura; la **Tarea 4** mide el total de accesos por operación y por lote para confirmar si es aceptable respecto al límite oficial.

### Tarea 4 — Medición de accesos a documentos en reglas con lotes grandes *(Hecha)*
- **Objetivo:**
  1. Consultar la documentación oficial vigente de Firestore sobre límites de accesos a documentos (`get`/`exists`/`existsAfter`) por operación (10) y por lote (`WriteBatch`, 20), y verificar si el emulador aplica el mismo límite.
  2. Medir en el emulador (`firestore-tests/measure/batch_limits.ts`) 4 variantes (`A`, `B`, `C`, `D`) con lotes crecientes de `1..30` pagos (`type == 'payment'`), devolución con `refund` (`1 saleReturns + 1 payment`) y cierre (`3 counts + 1 closings`).
  3. Decidir sobre `startDate` y el formato de lectura de `members`.
- **Requisitos / Reglas:** `§5.1`, `§11.1`, `§14.1`.
- **Archivos / Módulos:** `firestore-tests/measure/batch_limits.ts`, `firestore-tests/package.json` (script `measure`), `firestore-tests/tsconfig.json`.
- **Resultados (calculado frente a medido):**

| Variante | Descripción | Llamadas / pago *(doc)* | Máx. pagos / lote *(doc, caso peor)* | Máx. pagos / lote *(medido emulador)* | `1c-write` equiv. *(medido)* | `refundBatch` *(doc)* | `closingBatch` *(doc)* |
|---|---|---:|---:|---:|---|---:|---:|
| **A** | Actuales (`exists` + `2 get` miembro + `get startDate`) | 4 | 5 | `>= 30` | Denegado | 9 | 16 |
| **B** | `1 get` miembro (sin `exists`) + `get startDate` | 2 | 10 | `>= 30` | Denegado | 5 | 8 |
| **C** | `1 get` miembro (sin `exists`) sin `startDate` | 1 | 20 | `>= 30` | Denegado | 3 | 4 |
| **D** | Claims en `request.auth.token` + `get startDate` | 1 | 20 | `>= 30` | Denegado | 3 | 4 |

- **Conclusión de la medición:** **el emulador no aplica el límite de 20; manda la documentación** (caso peor: suma por escritura sin caché entre operaciones, tope de 20 por `WriteBatch` y 10 por operación).
- **Decisión:** adoptar la **variante B** con tope de **10 pagos por lote** (presupuesto: máximo **2** llamadas de acceso por pago); un abono con más de 10 ventas pendientes se aplica en varios lotes atómicos de hasta 10 pagos.
- **Verificación:** `firebase emulators:exec --project demo-egglytics-test --only firestore "npm --prefix firestore-tests run measure"` ejecutado con código de salida `0`.

### Tarea 3b — Resto de reglas de Firestore y sus AC
- **Objetivo:** Completar `firestore.rules` con `prices`, `expenseCategories`, `paymentMethods`, `reversals`, `sales`, `customers`, `openingBalances` y colecciones de movimientos, más `firestore.indexes.json`.
- **Requisitos / Reglas:** `RF-04`, `R-04`, `R-05`, `R-18`, `R-19`, `§11.1`.
- **AC:** `AC-33`, `AC-34`, `AC-52`, `AC-58`, `AC-59`, `AC-64`.
- **Archivos / Módulos:** `firestore.rules`, `firestore.indexes.json`, `firestore-tests/test/*.test.ts`.
- **Verificación:** Suite completa en `@firebase/rules-unit-testing` contra el emulador en verde.

### Tarea 5 — Capa `data`: Auth, Firestore offline y repositorio de sesión/miembros
- **Objetivo:** Configurar `PersistentCacheSettings` con `CACHE_SIZE_UNLIMITED`, `FirebaseAuth` (correo/contraseña) y `AuthRepository` leyendo `businesses/{businessId}` y `members/{uid}` y validando `active == true`.
- **Requisitos / Reglas:** `RF-01`, `RF-05`, `D-09`, `D-15`, `§10.1`.
- **AC:** `AC-32` en Kotlin contra el emulador.
- **Archivos / Módulos:** `app/src/main/kotlin/.../data/`, `app/src/test/...` o `androidTest/...`.
- **Verificación:** Pruebas en Kotlin contra el emulador en verde.

### Tarea 6 — Estado básico de sincronización y aviso al cerrar sesión
- **Objetivo:** Implementar el rastreo local de «hay cambios pendientes» (sí/no) mediante `waitForPendingWrites()` y almacenamiento local de última sincronización confirmada, con aviso antes de cerrar sesión si hay pendientes.
- **Requisitos / Reglas:** `RF-02 (básico)`, `§10.2 (2, 8, 10)`.
- **AC:** Base de `AC-56`.
- **Archivos / Módulos:** `app/src/main/kotlin/.../data/sync/`, `app/src/main/kotlin/.../ui/components/`.
- **Verificación:** Pruebas unitarias del rastreador y verificación manual en modo avión.

### Tarea 7 — Login, navegación por rol y pantallas de Catálogos (Administrador)
- **Objetivo:** Implementar pantalla de login, shell de navegación según rol (`admin`/`operator`) y gestión de catálogos (`prices` solo creación, `expenseCategories` y `paymentMethods`) solo visible/accesible para el administrador.
- **Requisitos / Reglas:** `RF-01`, `RF-04`, `D-02`, `D-03`, `D-04`, `§3`, `§7.11`.
- **AC:** `AC-04`, `AC-33`, `AC-39`, `AC-64`.
- **Archivos / Módulos:** `app/src/main/kotlin/.../data/repository/`, `app/src/main/kotlin/.../ui/`.
- **Verificación:** Pruebas de ViewModel/repositorio y prueba manual en modo avión.

## 4. Protocolo de ejecución

- **Una tarea por vez:** Al terminar cada tarea, detenerse y entregar: resumen, comandos ejecutados, resultado de las pruebas (incluyendo que fallaban antes de implementar) y el commit. No continuar sin aprobación explícita.
- **Aprobación de comandos:** Pedir aprobación antes de cada comando de terminal.
- **Sin despliegue real ni secretos:** No ejecutar `firebase deploy` contra ningún proyecto real (todo va al emulador) y no guardar claves de servicio ni `google-services.json` real en el repositorio (usar `.example` si hace falta).
- **Contradicciones:** Si surge una contradicción con `docs/ESPECIFICACION.md`, detenerse y preguntar sin resolverla por cuenta propia.
