# Egglytics

Aplicación Android para gestionar una granja familiar. Registra recolecciones, descartes y ventas por clasificación, además de pagos, gastos e inventario de alimento. El funcionamiento sin conexión permite registrar actividad y sincronizarla cuando vuelve la red.

## Probar la aplicación con datos locales

Usa JDK 17, Android SDK 36 y un emulador o dispositivo con Android API 26 o superior. Para el entorno de demostración necesitas Node.js y Firebase CLI. La [guía para depuración](docs/DEBUG_EMULATOR.md) detalla los puertos, los datos de prueba y la configuración para un teléfono físico.

1. Inicia Auth y Firestore con `firebase emulators:start --project demo-egglytics-test --only auth,firestore`.
2. Instala las dependencias indicadas en [firestore-tests](firestore-tests/package.json) y ejecuta desde la raíz `node firestore-tests/scripts/seed-debug-emulator.mjs`.
3. Con el emulador Android abierto, ejecuta `.\gradlew.bat :app:installDebug` en Windows o `./gradlew :app:installDebug` en macOS/Linux.
4. Accede como operador de prueba. La guía indica el negocio y el precio que deben aparecer. Después registra una operación sin conexión, restablece la red y comprueba el registro sincronizado.

## Demostración con dos perfiles

Desde la raíz, instala las herramientas del proyecto con `npm ci --prefix firestore-tests`. Mantén los emuladores abiertos mientras cargas los datos e instalas el APK. El emulador de Android llega al equipo anfitrión mediante `10.0.2.2`. Si usas un teléfono físico, sigue el apartado de red local de la guía de depuración.

Con las cuentas de demostración documentadas en esa guía, comprueba dos resultados:

- **Operador activo.** Al entrar debe aparecer *Granja Familiar* y el precio de referencia de **18.500 COP para la clasificación AA**. Registra una recolección de prueba y anota sus unidades. Después de recuperar la conexión, comprueba el movimiento desde una segunda sesión autorizada.
- **Usuario sin membresía.** El inicio de sesión debe mostrar el rechazo de acceso al negocio. Esto permite distinguir la autenticación de la autorización para consultar datos de la granja.

La [siembra de datos](firestore-tests/scripts/seed-debug-emulator.mjs) crea el escenario de demostración. Para investigar una diferencia entre usuarios, revisa las [reglas de Firestore](firestore.rules) junto con las [pruebas de acceso](firestore-tests).

## Estructura

[app](app) contiene el cliente Android, [domain](domain) la lógica de dominio y [firestore.rules](firestore.rules) las reglas de acceso. La variante de depuración está preparada para los emuladores locales. Consulta la [licencia](LICENSE) para las condiciones de uso.
