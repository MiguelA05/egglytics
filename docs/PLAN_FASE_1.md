# Plan de la Fase 1: Fundamentos

Basado en [ESPECIFICACION.md](ESPECIFICACION.md) v2.3.

## 1. Decisiones técnicas confirmadas para Fase 1

- **Estructura multimódulo:** `:domain` como módulo Kotlin puro (`java-library` + `kotlin("jvm")`, sin dependencias de Android ni Firebase) y `:app` como módulo Android (`com.android.application`) con Jetpack Compose, Material 3, Hilt y `BuildConfig.BUSINESS_ID`.
- **Toolchain fijado (Tarea 1a):** `AGP 8.13.2` + `Gradle 8.13` (wrapper) + `Kotlin 2.2.21` + `KSP 2.3.0` + `Hilt 2.57.2` + `Compose BOM 2025.12.00` + `compileSdk 36` / `targetSdk 36` / `minSdk 26`, ejecutando Gradle con JDK 21 y compilando a bytecode 17. Se eligió `Compose BOM 2025.12.00` (Compose `1.10`) porque `2026.09.00` (Compose `1.12.1`) exige AGP `9.1+` y `compileSdk 37`.
- **Regla de IDE:** No aceptar el asistente de actualización de AGP (*AGP Upgrade Assistant*) de Android Studio mientras estemos en esta línea. La migración a AGP 9 queda como una tarea futura separada.
- **Pruebas de reglas de seguridad:** TypeScript/Node.js con `@firebase/rules-unit-testing` contra Firebase Emulator Suite.
- **Pruebas de repositorios (Tarea 5):** Kotlin/JUnit contra Firebase Emulator Suite.
- **Revisión estática (`AC-36`):** Detekt configurado sobre `:domain` (y `:app`) más regla/prueba automática que falle si aparece `Float` o `Double` en `:domain` o en importes.

## 2. Orden y dependencias

1. **Tarea 1a (Hecha — `fa9e11d`):** Proyecto Gradle multimódulo (`:app` y `:domain`), Hilt, `BuildConfig` con `businessId`, `google-services.json.example`.
2. **Tarea 1b:** Detekt y verificación de `AC-36` (incluyendo demostración de fallo con un caso de prueba).
3. **Tarea 2:** Modelos base y lógica pura de dominio de Fase 1 en `:domain` (JUnit primero).
4. **Tarea 3a:** Reglas mínimas de Firestore (`members`, `businesses`, `payments` y `validMovement`) con sus pruebas en `@firebase/rules-unit-testing`.
5. **Tarea 4:** Medición en el emulador de accesos a documentos en reglas con lotes grandes + contraste con documentación oficial vigente de Firestore; decisión sobre dónde validar `startDate` (`§11.1`).
6. **Tarea 3b:** Resto de reglas de Firestore (`prices`, `expenseCategories`, `paymentMethods`, `reversals`, `sales`, `customers`, `openingBalances` y demás colecciones) y sus AC en el emulador.
7. **Tarea 5:** Capa `data`: inicialización de Firestore offline (`CACHE_SIZE_UNLIMITED`), Firebase Auth y repositorio de sesión/miembros probado en Kotlin contra el emulador.
8. **Tarea 6:** Estado básico de sincronización (`RF-02` básico) y aviso al cerrar sesión con pendientes.
9. **Tarea 7:** Capa `data` y `ui`: Login, navegación por rol y pantallas de Catálogos (solo Administrador).

## 3. Detalle de tareas (una por commit)

### Tarea 1a — Proyecto Android, módulos `:app` y `:domain`, Hilt y `BuildConfig` *(Hecha)*
- **Objetivo:** Crear la estructura base de Gradle con `:domain` (Kotlin JVM puro) y `:app` (Android API 26+, Compose, Material 3, Hilt, `BuildConfig.BUSINESS_ID` y `google-services.json.example`).
- **Requisitos / Reglas:** `D-01`, `D-13`, `D-14`, `D-15`, `§0`, `§10.1`.
- **AC:** Estructura base compilable.
- **Archivos / Módulos:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `domain/build.gradle.kts`, `app/build.gradle.kts`, `app/google-services.json.example`, `app/src/main/...`.
- **Verificación:** Compilación de `:domain` y `:app` (`.\gradlew.bat clean :domain:test :app:compileDebugKotlin` → `BUILD SUCCESSFUL`). Commit: `fa9e11d` (y `a24cd0d` para dejar de rastrear `.idea/`).

### Tarea 1b — Detekt y `AC-36`
- **Objetivo:** Configurar Detekt y una verificación que prohíba `Float` y `Double` en `:domain` (y en importes), demostrando primero que falla ante un caso de prueba con `Double`/`Float` y luego pasando en limpio.
- **Requisitos / Reglas:** `§0` (Dinero como `Long`), `§10.1`.
- **AC:** `AC-36`.
- **Archivos / Módulos:** `config/detekt/detekt.yml`, `build.gradle.kts`, prueba/tarea de verificación de `AC-36`.
- **Verificación:** Demostración del fallo con caso de prueba y ejecución en verde tras limpiarlo.

### Tarea 2 — Modelos base y lógica pura de dominio (Fase 1)
- **Objetivo:** Implementar en `:domain` las enumeraciones (`EggClassification`, `FeedIngredient`, `MemberRole`, `PaymentType`), fechas operativas (`YYYY-MM-DD` en `America/Bogota`), modelos (`Member`, `Price`, `ExpenseCategory`, `PaymentMethod`) y el caso de uso de precio vigente por `fechaOperativa` con desempate por `createdAt`.
- **Requisitos / Reglas:** `RF-04`, `R-02`, `R-04`, `D-02`, `D-03`, `D-04`.
- **AC:** `AC-04` (catálogo), `AC-39`.
- **Archivos / Módulos:** `domain/src/main/kotlin/...`, `domain/src/test/kotlin/...`.
- **Verificación:** Pruebas JUnit escritas primero (fallan) y luego en verde.

### Tarea 3a — Reglas mínimas de Firestore y pruebas base
- **Objetivo:** Crear `firestore.rules` con las funciones base (`memberData`, `businessData`, `isActive`, `isAdmin`, `isOperator`, `validDate`, `validDeviceTime`, `validMovement`) y las reglas de `businesses`, `members` (solo lectura para miembros activos) y `payments` (sus 3 tipos con lista cerrada de campos), más la configuración del proyecto de pruebas TypeScript/Node con `@firebase/rules-unit-testing`.
- **Requisitos / Reglas:** `RF-01`, `RF-05`, `R-07`, `R-09`, `R-22`, `§11.1`.
- **AC:** `AC-32`, `AC-35`, `AC-57`, `AC-65` (y parte de `AC-33`, `AC-34`).
- **Archivos / Módulos:** `firebase.json`, `firestore.rules`, `firestore-tests/` (`package.json`, `tsconfig.json`, `test/*.test.ts`).
- **Verificación:** Pruebas contra Firebase Emulator Suite escritas primero y en verde.

### Tarea 4 — Medición de accesos a documentos en reglas con lotes grandes
- **Objetivo:**
  1. Consultar la documentación oficial vigente de Firestore sobre límites de accesos a documentos (`get`/`exists`/`existsAfter`) por operación y por lote (`WriteBatch`) con su fuente, y verificar si el emulador aplica los mismos límites que producción (si algo no se puede confirmar, indicarlo sin suponerlo).
  2. Medir en el emulador el número de accesos en:
     - Abono repartido en 10 ventas pendientes (`WriteBatch` de 10 `payments`).
     - Cierre con conteos (`WriteBatch` de 3 `counts` + 1 `closings`).
     - Devolución con `refund` (`WriteBatch` de `saleReturns` + `payments` con `existsAfter`).
  3. Decidir si `fechaOperativa >= businessData(b).startDate` permanece en `validMovement` o pasa solo a la app. Si cambia el diseño de `validMovement`, actualizar primero `§11.1` de `docs/ESPECIFICACION.md` y avisar antes de seguir.
- **Requisitos / Reglas:** `§5.1`, `§11.1`, `§14.1`.
- **AC:** Pruebas de medición de lotes en Emulador.
- **Verificación:** Reporte con fuente oficial, mediciones y decisión documentada en `docs/ESPECIFICACION.md`.

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
