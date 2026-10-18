# Validación de la entrega 1.0.4

Fecha: 18 de octubre de 2026. Paquete `es.cantolagua.misemana`, versionCode 5. Android mínimo 8/API 26; destino API 35. Incluye la corrección de importes de la 1.0.3, documentada en `VALIDACION-1.0.3.md`.

## Historial vacío tras permitir otro intento

El usuario confirmó que pulsó «He revisado la web: permitir otro intento» después del aviso de pago. La implementación anterior borraba la fila del historial mediante DELETE. El planificador descarta las aperturas con más de 90 segundos de retraso: fuera de ese margen, desaparecía el resultado sin ejecutarse otro intento. No se enviaba una inscripción en lista de espera, función que sigue siendo manual.

La 1.0.4 comprueba el plazo al mostrar el botón y de nuevo al autorizar el reintento. Fuera del plazo conserva la fila original y explica el motivo. Dentro del plazo actualiza la fila a «Reintento pendiente», sin borrarla; al iniciarse el servicio, una actualización condicional recupera esa misma fila y mantiene la exclusión de envíos duplicados. Una reserva confirmada no admite reintento. Las filas muestran fecha y hora de su última actualización; el mensaje de historial vacío aclara que consultar horarios no equivale a reservar.

No se pueden reconstruir con esta actualización las filas que ya borró la versión anterior. No se ha comprobado la inscripción concreta en la cuenta del usuario ni se han enviado reservas o pagos.

## Resultados

- **86 comprobaciones JVM correctas:** 51 generales, 19 de horario y 16 de descarga del calendario. Incluyen el límite de los 90 segundos y el rechazo de una apertura vencida.
- APK 1.0.4 compilado y firmado con la misma clave de distribución. SHA-256 junto al archivo.
- **32 comprobaciones en Android 15 correctas:** persistencia cifrada, exclusión de duplicados, conservación del resultado al revisar un reintento, recuperación de la misma fila al iniciar el nuevo intento, rechazo de reintentos vencidos o confirmados, alarma con la interfaz cerrada y pantalla que explica el vencimiento sin ofrecer el botón anterior. Resultado en `artifacts/comprobaciones-android-1.0.4.txt`. Datos sintéticos y ninguna solicitud a Cantolagua.
- Firma v2/v3 y alineación verificadas; misma huella de certificado que la 1.0.2 y la 1.0.3. SHA-256 comprobado.
- El emulador sin aceleración tardó en arrancar y una instalación inicial agotó el tiempo de espera de ADB. Se comprobó la versión instalada, se completó la instalación de la instrumentación y se ejecutó la suite completa. Se amplió el tiempo del watchdog del sistema únicamente en el emulador para permitir su funcionamiento sin aceleración; el APK no cambia esa configuración.

Para repetir: `bash scripts/test.sh`, `bash scripts/build_android.sh`, `bash scripts/build_device_tests.sh`. La instrumentación `DeviceChecks` solo se ejecuta en un emulador aislado, pues borra sus datos y utiliza credenciales sintéticas.
