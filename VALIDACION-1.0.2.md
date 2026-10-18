# Validación de la entrega 1.0.2

Fecha: 18 de octubre de 2026. Paquete `es.cantolagua.misemana`, versionCode 3. Android mínimo 8/API 26; compilación y destino API 35. Misma firma de distribución que las versiones anteriores.

## Fallo reproducido y corregido

La web cambia el contenido según el identificador HTTP. Con el que utilizaban las versiones anteriores, `MiCantolagua/1.0 Android`, devuelve una vista móvil con pestañas y únicamente las **siete sesiones del lunes**. Sin el indicador móvil devuelve el calendario completo, con **26 sesiones de lunes a jueves** en la fecha de la comprobación. La 1.0.1 conservaba los datos recibidos, pero no recorría las pestañas de días: el calendario guardado no podía completarse. Las pruebas anteriores del selector utilizaban la respuesta de ordenador y no cubrían esa diferencia.

La 1.0.2 solicita la vista completa. Si la web devuelve de nuevo la vista móvil, consulta los enlaces de cada día publicado y reúne las sesiones. Se comprueba el origen HTTPS, la ruta, la fecha y la coherencia de las sesiones; no se siguen enlaces de reserva ni de cancelación durante una consulta. Esto se aplica tanto al calendario público como al autenticado usado para reservar.

Además, **Mi semana carga automáticamente el calendario público** desde una instalación vacía, sin necesitar conectar la cuenta. El nuevo indicador de formato invalida la consulta incompleta de versiones anteriores y fuerza una descarga al actualizar. La pantalla muestra progreso, número de sesiones y errores de conexión. Se conserva el horario de semanas previas y no se asignan nombres inventados a horas desconocidas.

## Resultados

- **74 comprobaciones JVM correctas:** 39 generales, 19 del horario persistente y 16 de descarga. Las nuevas usan las respuestas móviles públicas reales de los cuatro días, reproducen los siete registros iniciales, siguen las pestañas hasta reunir 26, verifican Step-Training del martes 09:20 y comprueban que no se siga una cancelación ni se acepte una sesión expirada.
- **9 comprobaciones con red real desde Android 15 correctas:** el cliente HTTP de la aplicación descargó 26 sesiones de cuatro fechas. Se abrió después la pantalla con datos vacíos y sin credenciales; su carga automática descargó y guardó de nuevo el calendario real. Se pulsó el botón del martes y apareció `09:20 · Step-Training`. No se inyectó una copia HTML en esta prueba.
- **18 comprobaciones deterministas del selector en Android 15 correctas**, incluyendo todos los botones de lunes a jueves y el viernes todavía no publicado.
- **19 comprobaciones generales en Android 15 correctas**, incluyendo Keystore, persistencia, exclusión de duplicados, tres pestañas y arranque desde una alarma con la interfaz cerrada.
- Compilación, firma v2/v3 y alineación del APK verificadas. SHA-256 junto al archivo. Sin modo depurable, sin tráfico HTTP claro y sin copia de seguridad de datos.

Resultados de dispositivo: `artifacts/comprobaciones-android-1.0.2.txt` y `artifacts/comprobaciones-red-1.0.2.txt`. Solo se realizaron lecturas públicas durante la prueba de red; no hubo inicio de sesión, envío de reservas ni repetición de la inscripción en lista de espera ya completada.

## Red del entorno de pruebas

La nube requiere su proxy de salida. El primer intento del emulador falló por resolución DNS. La prueba se completó configurando en la **instrumentación** la ruta del proxy del entorno y su certificado de confianza, previamente comprobado mediante una conexión TLS verificada. Se utilizó el gestor de confianza estándar y se mantuvo la verificación del nombre del servidor. No se deshabilitó TLS ni se aceptaron certificados arbitrarios.

El certificado y esta configuración solo se aplicaron al proceso de pruebas. **El APK de distribución no incluye el certificado del entorno ni cambios en la confianza HTTPS** y usa la conexión normal del móvil. La aplicación muestra un error si el dispositivo no puede resolver el dominio, conectar o validar HTTPS.

## Repetir la validación

```sh
python3 scripts/bootstrap_android.py
bash scripts/test.sh
bash scripts/build_android.sh
bash scripts/build_device_tests.sh
```

**Solo en emulador aislado: la instrumentación borra datos locales y crea estados sintéticos.**

```sh
adb install -r artifacts/MiCantolagua-1.0.2.apk
adb install -r android/build/device-tests/device-tests.apk
adb shell appops set es.cantolagua.misemana SCHEDULE_EXACT_ALARM allow
adb shell pm grant es.cantolagua.misemana android.permission.POST_NOTIFICATIONS
adb shell am instrument -w es.cantolagua.misemana.tests/es.cantolagua.misemana.CalendarDeviceChecks
adb shell am instrument -w es.cantolagua.misemana.tests/es.cantolagua.misemana.DeviceChecks
```

La prueba de red es opcional y consulta la web pública real:

```sh
adb shell am instrument -w es.cantolagua.misemana.tests/es.cantolagua.misemana.NetworkDeviceChecks
```

Las expectativas de 26 sesiones y Step-Training corresponden al calendario publicado en la fecha de esta validación y deben revisarse si cambia. En una nube con proxy, la instrumentación admite `proxyHost`, `proxyPort` y `useRuntimeCa`. Para incluir únicamente en la APK de pruebas el certificado aprobado del entorno, define `CANTOLAGUA_TEST_CA_FILE` al construirla. No incluyas credenciales o certificados privados. Las pruebas deterministas no necesitan Internet.

La regla de reserva sigue siendo **cuatro días naturales antes, a la misma hora de la clase, Europe/Madrid**. Los horarios seleccionados por el usuario no se cambian automáticamente al actualizar el catálogo. Se conserva el registro de la entrega anterior en `VALIDACION-1.0.1.md`.

## Límites

El emulador funciona sin aceleración por hardware. Su lanzador Quickstep mostró un aviso de falta de respuesta durante la captura, por lo que esa captura no se utiliza como evidencia visual ni se incluye en la entrega. Las comprobaciones de descarga, datos y callbacks de la aplicación se completaron.

La carga del calendario y el arranque en segundo plano están comprobados. Sigue pendiente observar una reserva programada completa en el móvil real. El teléfono necesita permisos, Internet, estar encendido y primer desbloqueo tras reiniciar; no se garantiza plaza. Las políticas de batería de cada fabricante y la red de cada dispositivo pueden afectar a la ejecución.
