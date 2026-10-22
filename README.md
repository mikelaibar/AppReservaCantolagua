# Mi Cantolagua para Android

Aplicación nativa independiente para programar reservas en [Cantolagua](https://cantolagua.sanguesa.es/reservas/actividades.aspx). Android 8 o posterior. No requiere servidor, suscripción, cuenta de Google ni mantener la pantalla abierta.

## Instalar y empezar

1. Descarga `artifacts/MiCantolagua-1.0.6.apk` en el móvil y ábrelo. Si ya usas una versión anterior, instala la actualización encima, sin desinstalar: conserva tu cuenta y horarios. Si Android lo solicita, permite instalar aplicaciones desde la aplicación con la que has descargado el archivo.
2. Abre **Mi semana**: el calendario público se carga automáticamente, sin necesitar una cuenta. Verás el número de sesiones descargadas o un error de conexión concreto. Para reservar, conecta después tu cuenta en **Ajustes → Guardar y comprobar acceso**. La verificación no realiza reservas.
3. La regla confirmada ya está configurada: **4 días naturales antes, a la misma hora de la sesión**, hora de Madrid. Por ejemplo, lunes 17:30 → jueves anterior 17:30; martes 18:30 → viernes anterior 18:30. Cada actividad tiene su apertura propia. La antelación se puede cambiar en Ajustes si el centro modifica la regla.
4. Permite **Alarmas y recordatorios** y **Notificaciones**. En los ajustes de batería del teléfono permite el funcionamiento sin restricciones para Mi Cantolagua. Si el fabricante tiene un ajuste de inicio automático, actívalo.
5. En **Mi semana**, añade las actividades de cada día a **Todas las semanas**. Esta es tu plantilla habitual y se repite indefinidamente. **Solo una semana** permite quitar o añadir clases en una semana concreta sin cambiar las siguientes. **Volver al horario habitual** elimina esa excepción.
6. Activa **Reservar por mí**. Comprueba la próxima apertura en la pantalla principal o en **Reservas**. Si una apertura ya ha pasado, se recuperan las clases elegidas que todavía no hayan empezado y no tengan un envío previo pendiente de comprobar.

Al pulsar **+ Añadir actividad**, primero se ofrecen las horas con el nombre guardado para ese día de la semana. Por ejemplo, martes **09:20 · Step-Training**; no se copia el Pilates del lunes. Si hay dos actividades a la misma hora, puedes elegir entre ambas.

Las tarjetas futuras que Cantolagua publica sin enlace de reserva también se ofrecen en el selector. La fecha se valida contra el encabezado del día y la hora se lee de la propia tarjeta. Así puedes elegir, por ejemplo, Fitball del lunes a las 16:30 antes de su apertura. La reserva espera a su hora y exige que el calendario autenticado publique el enlace válido.

La app conserva un horario consultado, independiente del historial de reservas: guarda día, hora, actividad y fecha de la sesión observada. Una consulta parcial de una semana nueva no borra los demás días. Los nombres se reutilizan de una semana a otra; una publicación más reciente actualiza esa combinación de día y hora. El servicio también aprende de los calendarios que consulta al reservar.

Siempre se ofrecen horas, incluso cuando todavía no hay un nombre guardado. En ese caso se indica **Actividad pendiente de consultar**: es una propuesta de hora, no una clase confirmada. La franja de mañana utiliza la hora observada para cada día (por ejemplo, 09:20 el martes); si aún no se conoce, se ofrecen 09:00 y 09:20 sin asignarles una actividad. Las horas habituales de tarde son 16:30, 17:30, 18:30, 19:30 y 20:30.

**Mi semana** descarga el calendario público al abrirse si no hay una consulta válida reciente. Se actualiza cada cinco minutos al volver a esa pantalla o abrir el selector, y fuerza una consulta al actualizar desde una versión anterior. **Actualizar horarios** y **Actualizar** permiten consultar de nuevo. Ver los horarios no requiere conectar la cuenta; para reservar sí se verifica el calendario autenticado del socio. Si la descarga falla, la pantalla muestra el error y permite usar el horario guardado. Un viernes aún no publicado conserva su nombre de consultas anteriores; si nunca se ha consultado, queda pendiente en lugar de recibir una actividad de otro día.

**Otra hora (manual)** permite configurar una combinación no verificada de hora y actividad, con una confirmación explícita. Todas las opciones muestran la hora. Solo se enviará una reserva si la web publica exactamente la actividad, fecha y hora elegidas. Los horarios ya seleccionados por ti se conservan al actualizar: revisa y quita los que hayas añadido por error.

## Funcionamiento con la app cerrada

Una alarma exacta de Android activa un servicio visible mediante una notificación aproximadamente un minuto antes de la apertura. Se inicia sesión, se espera hasta la hora de apertura y se consulta la disponibilidad para enviar una única solicitud por sesión. Si hay varias clases para la misma apertura, se procesan juntas; una clase sin publicar no bloquea las demás. Cada activación consulta durante un máximo de 90 segundos desde que puede empezar a reservar. Si Android entrega tarde la alarma, el servicio hace esa comprobación igualmente: no descarta una sesión por haber pasado más de 90 segundos desde su apertura. Las solicitudes enviadas no se repiten automáticamente y las clases que ya han comenzado se excluyen.

La app puede quitarse de las aplicaciones recientes. **Forzar detención**, apagar el móvil, retirar los permisos o las restricciones del fabricante pueden impedir la alarma. Después de reiniciar, desbloquea el teléfono al menos una vez. Se necesita Internet al abrirse las reservas. El modo de espera de bajo consumo de Android puede cortar la conexión incluso con un servicio activo; la app avisa si detecta ese modo.

En un emulador o máquina virtual, tanto Android como el ordenador que lo aloja deben permanecer encendidos, conectados y sin suspensión. Comprueba la zona horaria de Madrid y los permisos de alarma y notificaciones en ese Android.

Este sistema evita un servidor permanente, pero el teléfono, la red y el centro pueden retrasar o impedir una solicitud. No garantiza plaza ni precisión de milisegundos. GitHub Actions tampoco ofrece un horario puntual adecuado para competir por plazas a una hora concreta.

## Resultados y duplicados

- **Reservada**: la respuesta identifica la sesión solicitada o una consulta posterior confirma que la cuenta está inscrita.
- **Ya reservada**: el calendario ya mostraba la inscripción; no se toca el enlace de cancelación.
- **Completa**, **En lista de espera**, **Requiere pago**: se registra el estado y no se realiza una reserva directa. El historial incluye el importe recibido o indica si existe una inscripción pendiente de pago.
- **Importe no reconocido**: la web ha enviado un formato de precio que la app no puede interpretar; se detiene la reserva y se pide comprobarlo. No se afirma que haya un pago pendiente.
- **Comprobar en la web**: la solicitud pudo llegar al centro, pero no se pudo confirmar el resultado. Se bloquea la repetición automática. Abre la web y comprueba tu cuenta antes de permitir otro intento.

La app no cancela reservas, paga actividades ni se apunta automáticamente a listas de espera. Estos casos se gestionan en la web. Pausar impide solicitudes futuras; una solicitud ya enviada puede terminar en el servidor.

**He revisado la web: permitir otro intento** conserva una entrada visible con «Reintento pendiente». Permite preparar otro envío si la sesión todavía no ha empezado y la reserva no está confirmada. Conserva el resultado al terminar la sesión; no borra la fila. Hasta la 1.0.4 solo permitía reintentar durante 90 segundos desde la apertura; la 1.0.5 permite recuperar una clase abierta con plazas, previa revisión del resultado anterior. El botón de versiones hasta la 1.0.3 podía borrar el resultado aunque no quedase tiempo para otro intento. La 1.0.4 corrige este fallo; no recupera resultados borrados anteriormente.

El historial corresponde a los intentos realizados por esta instalación; no es una importación del historial completo de la web. Puedes probar la conexión sin reservar desde Ajustes. Una contraseña modificada en la web debe guardarse de nuevo en la app.

## Datos y privacidad

La contraseña se cifra con AES-GCM y una clave de Android Keystore. La clave queda en el teléfono, sin exigir autenticación en cada uso para permitir reservas en segundo plano después del primer desbloqueo. Usuario, horarios elegidos, horario consultado y resultados están en datos privados de la aplicación. No se permite copia de seguridad automática. La app solo se comunica por HTTPS con Cantolagua y no incluye analítica, anuncios ni las credenciales facilitadas durante el desarrollo. **Borrar cuenta y datos** elimina los datos locales; no cancela reservas de la web.

## Desarrollo y compilación

Se utiliza Java, las APIs nativas de Android y Jsoup 1.21.2. El APK del amigo se examinó como referencia de funcionamiento; no se incluye su código ni su aplicación. El proyecto puede compilarse sin Gradle ni Android Studio.

Requisitos: Linux x86_64, Python 3 con TLS, Java 21 con módulos `jdk.compiler` y `jdk.jartool`, y `keytool`. El script descarga Android Platform 35 y Build Tools 35.0.0 de Google, y verifica sus sumas con los metadatos oficiales. Jsoup y el JSON usado por las pruebas se verifican con los metadatos de Maven Central. No se deshabilita TLS ni la comprobación de integridad.

```sh
cd /workspace/AppReservaCantolagua
python3 scripts/bootstrap_android.py
bash scripts/test.sh
bash scripts/build_android.sh
```

`CANTOLAGUA_TOOLS` permite cambiar la carpeta de herramientas (por defecto `/workspace/.tools`). El APK se genera en `artifacts/MiCantolagua-1.0.6.apk`, junto con su SHA-256. La compilación verifica la firma del APK. Es una compilación de distribución, sin `debuggable` y con componentes internos no exportados.

La firma se conserva fuera del repositorio en `/workspace/.cantolagua-android-signing`, o en `CANTOLAGUA_SIGNING_DIR`. **Guarda una copia privada de esta carpeta para firmar futuras actualizaciones**. No la subas al repositorio. Una instalación nueva que genere otra firma no podrá actualizar el APK instalado; habrá que desinstalar antes, perdiendo los datos locales.

### Pruebas

`bash scripts/test.sh` ejecuta las pruebas de fechas, cambio de hora, repetición semanal, excepciones, parsing del calendario, protección de enlaces, confirmación de reservas y conservación del horario por día y hora. Usa transporte simulado: no envía reservas reales.

`bash scripts/build_device_tests.sh` construye una APK de instrumentación separada. **Se ejecuta exclusivamente en un emulador limpio: borra los datos locales de la aplicación de destino**. Las pruebas deterministas comprueban Keystore, persistencia, exclusión de duplicados, alarmas y selectores de cada día sin realizar conexiones. La prueba opcional `NetworkDeviceChecks` consulta la web pública real y verifica la carga automática desde una instalación sin cuenta; no inicia sesión ni envía reservas. No se distribuye con la aplicación. Ver `VALIDACION.md` para el resultado de esta entrega.

**Reservas → Funcionamiento en segundo plano** muestra la alarma programada, la última alarma recibida y el último paso del servicio. «Última activación» identifica el inicio del servicio. **Actualizar resultados** relee el historial local. Si una alarma estaba programada y no se recibió, revisa **Ajustes → Preparar este móvil**, permite alarmas y recordatorios y configura la batería sin restricciones. El diagnóstico permite distinguir un bloqueo de Android de un error al iniciar sesión o enviar la reserva.

El diagnóstico opcional `tests/LiveReadOnly.java` permite verificar acceso y calendario desde una terminal privada. Lee las credenciales de forma interactiva, no acepta contraseñas en argumentos ni envía reservas. No guardar credenciales en código, comandos, logs ni archivos de configuración.

## Mantenimiento de la integración

El sitio no proporciona una API pública documentada. `ClubClient.java` interpreta el formulario y calendario actuales. Comprueba fecha, tipo de actividad, hora, identificador de sesión, destino HTTPS y persona seleccionada antes de enviar una reserva. Cualquier cambio incompatible del sitio detiene el intento y requiere revisar la integración. Las cookies solo viven en memoria durante cada ejecución.

Al reiniciar el teléfono o actualizar la aplicación, el receptor del sistema únicamente vuelve a programar la alarma. El servicio de tipo `dataSync` comienza desde una alarma exacta autorizada, no desde el arranque del sistema, respetando las restricciones de Android 15.


### Descarga del calendario completo

La web devuelve una vista móvil de un solo día cuando el identificador HTTP contiene `Android`. Esto explicaba por qué las versiones anteriores solo recibían las siete sesiones del lunes, mientras un navegador de ordenador veía 26 hasta el jueves. La 1.0.2 solicita la vista completa con el identificador de la app sin ese indicador móvil. Si la web aun así devuelve pestañas de días, consulta exclusivamente sus enlaces de calendario y reúne las sesiones publicadas. Esta lógica se aplica tanto al calendario público como al autenticado utilizado para reservar. Las pruebas incluyen las respuestas móviles reales de los cuatro días y verifican que jamás se siga un enlace de reserva o cancelación durante una lectura.

### Importes del calendario (1.0.3)

La app interpreta también importes con moneda y espacios, como `0,00 €`, `0.00 EUR` y espacios no separables. Las versiones anteriores los trataban erróneamente como importes positivos. Un cero permite la reserva directa si se cumplen las demás condiciones. Un importe positivo o una inscripción marcada como pendiente de pago detiene la automatización; una inscripción pendiente de pago no se considera confirmada. Un formato desconocido se registra expresamente para comprobarlo en la web. Esta corrección reproduce un fallo del parser; no identifica por sí sola el importe que devolvió la cuenta en un intento anterior.

### Recuperación y clases incluidas en el abono (1.0.5)

Cantolagua utiliza `Incluida en Abono Actividades` como texto de importe para el socio. Se reconoce ese texto concreto como incluido, además de los ceros con moneda; otros textos desconocidos siguen bloqueándose sin afirmar que exista una deuda. El diálogo de la web genera el botón «Reservar plaza» con el mismo enlace `r.aspx?a=d&R=…` de la clase. El cliente añade la persona seleccionada y los parámetros JSON usados por la web; no hay una llamada de reserva adicional para abrir ese diálogo.

Después del envío se consulta el calendario autenticado para comprobar la inscripción, también si se perdió la respuesta. Una respuesta incierta nunca causa otro envío automático. La app mantiene las sesiones seleccionadas que no hayan empezado aunque su apertura fuera hace horas; al activarse o volver a programar una alarma puede recuperarlas. Un intento anterior registrado sigue bloqueado hasta que el usuario revise la web y permita otro intento. La clase completa no se convierte automáticamente en una inscripción en lista de espera.

`BookingFlowTests` verifica el flujo de cliente con un servidor simulado. La instrumentación **BookingFlowChecks** ejecuta la alarma, el servicio de producción, el almacenamiento y las notificaciones, sustituyendo únicamente el transporte HTTP por respuestas controladas. No envía reservas al centro.

El diagnóstico `bash scripts/live_booking.sh YYYY-MM-DD HH:mm actividad` usa el mismo cliente contra la web real y, sin opciones, solo consulta. Las credenciales se leen de `CANTOLAGUA_USERNAME` y `CANTOLAGUA_PASSWORD`, nunca de argumentos. `--reserve` permite un envío real para esa sesión. `--waitlist` permite inscribirla en espera si está completa y la web ofrece esa acción; si hay plaza, permite la reserva directa. Estas opciones solo se usan para una acción solicitada por el titular. Antes del envío guarda un marcador privado fuera del repositorio para impedir repetir automáticamente una petición incierta. Siempre comprueba la cuenta y el estado actual; si ya está inscrita o en espera, no reenvía. La confianza del proxy del entorno se configura exclusivamente para este diagnóstico, sin cambiar el APK ni deshabilitar HTTPS.
