# Validación de la entrega 1.0.0

Fecha: 18 de octubre de 2026. Paquete: `es.cantolagua.misemana`. Android mínimo 8/API 26; compilación y destino API 35.

## Regla de reserva confirmada por el usuario

**Cuatro días naturales antes, a la misma hora de la sesión, Europe/Madrid.**

| Clase | Se intenta reservar |
| --- | --- |
| Lunes 17:30 | Jueves anterior 17:30 |
| Martes 18:30 | Viernes anterior 18:30 |
| Miércoles 17:30 | Sábado anterior 17:30 |
| Jueves 18:30 | Domingo anterior 18:30 |
| Viernes 17:30 | Lunes anterior 17:30 |

Esta regla sustituye la hipótesis inicial de una apertura diaria fija. El calendario resta días naturales y conserva la hora local de cada clase, incluido el cambio de hora de verano.

## Comprobaciones completadas

- **39 pruebas JVM: todas correctas.** Incluyen los cinco ejemplos de día de apertura, distintas horas para distintas sesiones, cambio de hora, cambio de año, repetición semanal, excepciones, semanas lejanas, fechas vencidas, enlaces de reserva, cancelación, pago, duplicados, respuestas inciertas y validación de sesión.
- **19 pruebas en un emulador Android 15/API 35: todas correctas.** Se verificaron el valor inicial de cuatro días, cifrado y descifrado con Android Keystore, persistencia, plantillas y excepciones, exclusión transaccional de intentos duplicados, firma/instalación de la aplicación de distribución y las tres pestañas de la interfaz.
- **Arranque en segundo plano verificado**: una alarma exacta inició el servicio en primer plano con la interfaz cerrada. El ensayo usó una planificación vacía para comprobar el arranque sin conectarse al centro.
- **Calendario público real consultado por HTTPS**: HTTP 200; el lector reconoció 26 sesiones de 11 tipos de actividad.
- **APK firmado verificado** con Android Build Tools: firmas v2 y v3 válidas; no depurable; tráfico HTTP sin cifrar deshabilitado; copia automática de datos deshabilitada. SHA-256 junto al APK.
- Scripts de preparación, pruebas y compilación ejecutados en este entorno. Instrucciones de instalación y arranque actualizadas en el borrador del entorno.

Las pruebas usan datos sintéticos y transportes simulados. **No se ha enviado ninguna reserva real nueva durante esta entrega**, ni se ha vuelto a ejecutar la inscripción en lista de espera autorizada y completada anteriormente. La consulta pública no verifica por sí sola el acceso autenticado de la nueva aplicación. La pantalla **Guardar y comprobar acceso** verifica la cuenta al configurarla en el teléfono; la contraseña real no está incluida en el APK.

## Límites de la validación

El emulador carece de aceleración por hardware; se amplió el tiempo del watchdog de ese emulador para permitir su arranque. Esto no modifica la aplicación distribuida. La prueba de alarma confirma el inicio del servicio y la prueba de red usa transporte simulado: queda por observar una reserva programada completa en el móvil real, a la apertura de una sesión elegida por el usuario.

No se han probado las políticas de batería de todos los fabricantes, un reinicio físico de cada modelo ni la red móvil del usuario. El teléfono necesita Internet, permisos, estar encendido y haberse desbloqueado una vez tras el reinicio. El centro, Android y la red pueden retrasar las solicitudes; no se garantiza obtener una plaza.

## Repetir las pruebas de dispositivo

**Solo en un emulador aislado: la instrumentación borra los datos de Mi Cantolagua.**

```sh
bash scripts/build_android.sh
bash scripts/build_device_tests.sh
adb install -r artifacts/MiCantolagua-1.0.0.apk
adb install -r android/build/device-tests/device-tests.apk
adb shell appops set es.cantolagua.misemana SCHEDULE_EXACT_ALARM allow
adb shell pm grant es.cantolagua.misemana android.permission.POST_NOTIFICATIONS
adb shell am instrument -w es.cantolagua.misemana.tests/es.cantolagua.misemana.DeviceChecks
```

Las dos concesiones de permisos de prueba corresponden al emulador API 35; en el teléfono se conceden desde la interfaz. El resultado esperado es `RESULT: 19 device checks passed; no live requests sent.` Una finalización del comando sin ese resultado no cuenta como prueba correcta.
