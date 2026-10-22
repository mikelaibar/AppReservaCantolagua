# Validación de la entrega 1.0.5

Fecha: 22 de octubre de 2026. Paquete `es.cantolagua.misemana`, versionCode 6; Android 8/API 26 o posterior, destino API 35.

## Problemas encontrados

1. El texto **Incluida en Abono Actividades**, mostrado por Cantolagua como importe de una clase incluida, no se reconocía. En 1.0.4 se consideraba un importe desconocido y bloqueaba el envío. Se ejecutó la nueva prueba contra el código anterior del commit `2506386`: falló precisamente con ese texto. La 1.0.5 reconoce el valor exacto sin aceptar indiscriminadamente cualquier texto como gratuito.
2. El planificador eliminaba una sesión 90 segundos después de su apertura, aunque la clase no hubiese empezado y todavía quedasen plazas. Se conserva ahora hasta su inicio. La alarma tardía tiene su propia ventana acotada de consulta y puede recuperar el intento. Se mantiene la regla de cuatro días naturales a la misma hora de Madrid, incluido el cambio de hora del lunes 26/10.
3. La última activación comunicada por el usuario era de la noche anterior. No se dispone de evidencia suficiente para determinar por qué Android no arrancó el servicio a la hora prevista en ese teléfono. Se registran ahora por separado programación, recepción de alarma, inicio del servicio y último paso. No se afirma haber corregido una restricción concreta de ese fabricante.

Se verificó en el JavaScript público que el botón **Reservar plaza** utiliza el enlace de la clase con `a=d`. El diálogo se construye localmente en el navegador; no requiere una segunda petición previa de reserva.

## Confirmación y duplicados

El cliente verifica la inscripción mediante otra consulta autenticada tras enviar la reserva. También consulta si la respuesta se pierde, sin reenviar. Un justificante específico puede servir de confirmación si la lectura posterior no está disponible; una contradicción explícita con el calendario requiere revisión. La inscripción pendiente de pago no cuenta como plaza confirmada.

La recuperación de una apertura no vuelve a enviar solicitudes que ya tienen un resultado registrado o incierto. El reintento revisado conserva la fila y requiere que la clase no haya empezado. La lista de espera sigue sin activarse automáticamente: la operación del cliente añadida para diagnósticos puntuales exige selección explícita y verifica su resultado.

## Resultados

- **106 comprobaciones JVM correctas:** 55 generales, 19 de horario, 16 de descarga y 16 del flujo autenticado con transporte simulado. Cubren el texto de abono, Pilates del lunes a las 09:00 después de la antigua ventana, desfase de verano/invierno, verificación posterior, respuesta perdida, contradicciones, destino/persona correctos e inscripción explícita en espera sin duplicados.
- **13 comprobaciones del flujo completo en Android 15 correctas:** alarma exacta con la interfaz cerrada, recepción registrada, inicio de sesión, una sola solicitud, verificación autenticada, historial, notificación de plaza, recuperación tras perder la respuesta y fallo de login sin enviar. Transporte HTTP simulado, sin solicitudes reales al centro.
- **34 comprobaciones generales en Android 15 correctas:** Keystore, persistencia, duplicados, reintentos, permisos, diagnóstico de programación, alarma en segundo plano y pantallas.
- APK de distribución firmado con la misma clave y sin credenciales. Firma v2/v3, alineación y SHA-256 comprobados. No se incluye la CA de pruebas en el APK.
- Se realizó una comprobación autenticada real con la cuenta autorizada. La sesión consultada estaba completa y no se envió una solicitud de plaza. Los resultados de acciones personales autorizadas se conservan de forma privada, fuera del repositorio. No se afirma haber probado una nueva reserva real de plaza disponible.

Los resultados de instrumentación y JVM se incluyen en `artifacts/comprobaciones-*-1.0.5.txt`; la reproducción del defecto anterior se incluye como fallo esperado. Las comprobaciones de red y selectores de otras entregas no se cuentan como ejecuciones nuevas.

## Repetición

```sh
python3 scripts/bootstrap_android.py
bash scripts/test.sh
bash scripts/build_android.sh
bash scripts/build_device_tests.sh
```

La instrumentación `BookingFlowChecks` y `DeviceChecks` se ejecuta solo en un emulador aislado: borra sus datos y utiliza credenciales ficticias. Las pruebas reales no forman parte de esa suite. El emulador sin aceleración requiere tiempo de arranque y un watchdog ampliado en el sistema de pruebas; esa configuración no se incorpora a la app.

El fallo concreto del arranque en el teléfono real sigue necesitando revisar sus permisos y batería; los nuevos indicadores permiten localizarlo si se repite.
