# Validación de la entrega 1.0.6

Fecha: 22 de octubre de 2026. Paquete `es.cantolagua.misemana`, versionCode 7; Android 8/API 26 o posterior, destino API 35.

## Corrección del selector

La web pública ya mostraba Fitball del lunes 26 a las 16:30, pero publicaba la tarjeta futura sin enlace y sin `data-fecha`. El lector anterior exigía ambos datos y la omitía. Se conserva ahora el nombre y la hora reales de estas tarjetas; la fecha se obtiene del encabezado validado de su columna o del día seleccionado en la vista móvil. No se utiliza la hora de la fila, que puede diferir de la sesión (por ejemplo, Step-Training del martes a las 09:20).

El encabezado debe concordar con el día de la semana y el número de día dentro del calendario próximo. Se rechazan columnas combinadas, fechas incoherentes y tarjetas sin contexto de día. Las tarjetas futuras se ofrecen para configurar el plan, pero siguen sin poder enviar una reserva. A la apertura, el flujo autenticado vuelve a consultar la sesión y exige el enlace válido, la fecha, la hora, la actividad y el importe incluido. No se modifica el envío ni se adelanta la reserva.

Se actualiza la versión del catálogo local para descargar de nuevo el calendario tras instalar la actualización, conservando cuenta, plantilla e historial. Sigue aplicándose la apertura cuatro días naturales antes a la misma hora de Madrid, incluido el cambio de hora.

## Evidencia y alcance

- **119 comprobaciones JVM correctas:** 55 generales, 19 de horario, 16 de descarga, 16 de reserva/verificación y 13 nuevas sobre tarjetas futuras. La nueva muestra procede de una consulta pública real del 22/10; incluye Fitball del lunes 26 a las 16:30 y del miércoles 28 a las 17:30. Se prueban también la vista móvil, las actividades paralelas, el cambio de año y el rechazo de mutaciones desde una tarjeta futura.
- La nueva prueba ejecutada contra el código de la 1.0.5 falla en la primera comprobación: falta Fitball del lunes a las 16:30. El fallo esperado se conserva en `artifacts/comprobaciones-regresion-1.0.6.txt`.
- La APK de instrumentación compiló correctamente. No se completó su ejecución en esta entrega: el emulador local sin KVM no respondió al enlace ADB tras más de nueve minutos de arranque. No se cuentan como pasadas las pruebas visuales nuevas ni se atribuyen a esta versión las ejecuciones anteriores. Se conserva el intento en `artifacts/comprobaciones-emulador-1.0.6.txt`. Los resultados JVM se guardan en `artifacts/comprobaciones-jvm-1.0.6.txt`.
- APK de distribución con firma v2/v3 y la misma identidad de firma; versionCode 7. SHA-256 adjunto. Sin credenciales, código de instrumentación ni CA de pruebas.
- En esta entrega solo se consultó el calendario público real: no se enviaron reservas ni inscripciones en listas de espera. Las pruebas simuladas no garantizan conseguir plaza frente a otros usuarios.

La configuración de batería del teléfono se da por comprobada por el usuario. Esta corrección resuelve la ausencia de actividades en el selector; no atribuye ni da por resuelta la causa de una alarma que no se reciba en el teléfono. En una máquina virtual, Android y el equipo anfitrión deben permanecer encendidos, con red y sin suspensión. Consultar **Reservas → Funcionamiento en segundo plano** para distinguir alarma programada, recibida y resultado del servicio.

## Repetición

```sh
bash scripts/test.sh
bash scripts/build_android.sh
bash scripts/build_device_tests.sh
```

`FutureCalendarChecks` abre los selectores reales, elige Fitball antes de su apertura y comprueba la persistencia del día, hora y actividad. `BookingFlowChecks` comprueba el flujo completo de alarma, login, envío único, verificación, historial y notificación mediante transporte simulado. Solo deben ejecutarse en un emulador aislado: borran los datos de la app de prueba.

La validación de la versión anterior se conserva en `VALIDACION-1.0.5.md`; sus ejecuciones no se cuentan como nuevas.
