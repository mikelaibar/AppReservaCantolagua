# Validación de la entrega 1.0.3

Fecha: 18 de octubre de 2026. Paquete `es.cantolagua.misemana`, versionCode 4. Android mínimo 8/API 26; destino API 35.

## Diagnóstico

El aviso «Requiere pago» lo genera el cliente al interpretar `data-importe` del calendario autenticado. El servicio registra ese resultado sin enviar la reserva. Se reprodujo en una prueba que la versión anterior interpreta `0,00 €` como un importe positivo: la prueba falló antes de la corrección.

Esta reproducción demuestra un defecto del parser, pero no prueba que fuese el contenido recibido en el intento del usuario a las 20:30. No se dispone de la respuesta autenticada de ese intento ni se ha confirmado la actividad/fecha. La consulta pública real no incluye importes y no permite resolver esa duda. No se ha iniciado sesión ni enviado ninguna reserva en esta revisión.

## Cambios

- Reconocimiento de ceros con moneda EUR/€ y espacios no separables. Un precio positivo sigue bloqueando la reserva.
- Un formato desconocido bloquea el envío con «Importe no reconocido», sin afirmar que exista una deuda. El historial conserva hasta 80 caracteres del importe consultado.
- Las marcas de inscripción pendiente de pago (`reserva__sinpagar`, `data-idlinea`) impiden considerar la plaza confirmada. Estas marcas y la diferencia entre importe informativo y línea de pago se comprobaron en el JavaScript público de Cantolagua.
- Se conservan el bloqueo de duplicados y la regla de cuatro días naturales a la misma hora de Madrid. No se repite automáticamente un intento ya registrado.

## Comprobaciones

- **81 comprobaciones JVM correctas:** 46 generales, 19 de horario y 16 de descarga del calendario. Incluyen cuatro formatos de cero con moneda, un precio positivo, un formato desconocido y una inscripción pendiente de pago. Las reservas se simulan mediante transporte de prueba.
- APK 1.0.3 compilado; firma v2/v3 y alineación verificadas. Misma firma de distribución que la 1.0.2. SHA-256 junto al APK.
- No se han repetido las pruebas de interfaz ni de alarmas en Android para esta entrega. La validación anterior está en `VALIDACION-1.0.2.md`; sus resultados no se presentan como ejecuciones nuevas.
- Pendiente confirmar el contenido concreto del intento comunicado por el usuario. No se ha probado una reserva real ni se ha realizado ningún pago.

Para repetir las comprobaciones: `bash scripts/test.sh` y `bash scripts/build_android.sh`.
