# Conexión del APK debug con Firebase Emulator Suite

La variante `debug` conecta `FirebaseAuth` (puerto `9099`) y `FirebaseFirestore` (puerto `8080`) al emulador antes de cualquier operación y habilita tráfico HTTP en texto claro únicamente en `app/src/debug/AndroidManifest.xml`. En `release`, `USE_FIREBASE_EMULATOR` es `false` y no se usa la configuración demo.

## 1. Emulador Android estándar (`10.0.2.2`)

1. Arranca ambos emuladores desde la raíz del repositorio:
   ```powershell
   firebase emulators:start --project demo-egglytics-test --only auth,firestore
   ```
2. En otra terminal, siembra los datos locales de prueba (`egglytics_default`, operador activo, usuario sin membresía y precio vigente):
   ```powershell
   node firestore-tests/scripts/seed-debug-emulator.mjs
   ```
3. Compila e instala el APK debug (usa `10.0.2.2` por defecto):
   ```powershell
   .\gradlew.bat :app:installDebug
   ```
4. Prueba en la app:
   - **Operador activo:** `operador@example.com` / `egglytics123` → muestra `Granja Familiar` y `AA: 18500 COP`.
   - **Sin membresía:** `sinmembresia@example.com` / `egglytics123` → rechazado en login (*"Acceso denegado: no tienes membresía activa en este negocio."*).

## 2. Teléfono físico en la misma red Wi-Fi

1. Cambia temporalmente `"host": "127.0.0.1"` por `"host": "0.0.0.0"` en `firebase.json` (sección `emulators.auth` y `emulators.firestore`) y reinicia `firebase emulators:start --project demo-egglytics-test --only auth,firestore`.
2. Compila pasando la IP LAN de tu PC:
   ```powershell
   .\gradlew.bat :app:installDebug -PEMULATOR_HOST=192.168.1.50
   ```
   *(Permite también tráfico entrante a los puertos `8080` y `9099` en el firewall de Windows si está activo).*
