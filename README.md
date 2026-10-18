# Mi Cantolagua para Android

Aplicación nativa independiente para programar reservas en [Cantolagua](https://cantolagua.sanguesa.es/reservas/actividades.aspx). Android 8 o posterior. No requiere servidor, suscripción, cuenta de Google ni mantener la pantalla abierta.

## Instalar y empezar

1. Descarga `artifacts/MiCantolagua-1.0.2.apk` en el móvil y ábrelo. Si ya usas una versión anterior, instala la actualización encima, sin desinstalar: conserva tu cuenta y horarios. Si Android lo solicita, permite instalar aplicaciones desde la aplicación con la que has descargado el archivo.
2. Abre **Mi semana**: el calendario público se carga automáticamente, sin necesitar una cuenta. Verás el número de sesiones descargadas o un error de conexión concreto. Para reservar, conecta después tu cuenta en **Ajustes → Guardar y comprobar acceso**. La verificación no realiza reservas.
3. La regla confirmada ya está configurada: **4 días naturales antes, a la misma hora de la sesión**, hora de Madrid. Por ejemplo, lunes 17:30 → jueves anterior 17:30; martes 18:30 → viernes anterior 18:30. Cada actividad tiene su apertura propia. La antelación se puede cambiar en Ajustes si el centro modifica la regla.
4. Permite **Alarmas y recordatorios** y **Notificaciones**. En los ajustes de batería del teléfono permite el funcionamiento sin restricciones para Mi Cantolagua. Si el fabricante tiene un ajuste de inicio automático, actívalo.
5. En **Mi semana**, añade las actividades de cada día a **Todas las semanas**. Esta es tu plantilla habitual y se repite indefinidamente. **Solo una semana** permite quitar o añadir clases en una semana concreta sin cambiar las siguientes. **Volver al horario habitual** elimina esa excepción.
6. Activa **Reservar por mí**. Comprueba la próxima apertura en la pantalla principal o en **Reservas**. Los intentos cuyo plazo ya ha pasado no se ejecutan tarde.

Al pulsar **+ Añadir actividad**, primero se ofrecen las horas con el nombre guardado para ese día de la semana. Por ejemplo, martes **09:20 · Step-Training**; no se copia el Pilates del lunes. Si hay dos actividades a la misma hora, puedes elegir entre ambas.

La app conserva un horario consultado, independiente del historial de reservas: guarda día, hora, actividad y fecha de la sesión observada. Una consulta parcial de una semana nueva no borra los demás días. Los nombres se reutilizan de una semana a otra; una publicación más reciente actualiza esa combinación de día y hora. El servicio también aprende de los calendarios que consulta al reservar.

Siempre se ofrecen horas, incluso cuando todavía no hay un nombre guardado. En ese caso se indica **Actividad pendiente de consultar**: es una propuesta de hora, no una clase confirmada. La franja de mañana utiliza la hora observada para cada día (por ejemplo, 09:20 el martes); si aún no se conoce, se ofrecen 09:00 y 09:20 sin asignarles una actividad. Las horas habituales de tarde son 16:30, 17:30, 18:30, 19:30 y 20:30.

**Mi semana** descarga el calendario público al abrirse si no hay una consulta válida reciente. Se actualiza cada cinco minutos al volver a esa pantalla o abrir el selector, y fuerza una consulta al actualizar desde una versión anterior. **Actualizar horarios** y **Actualizar** permiten consultar de nuevo. Ver los horarios no requiere conectar la cuenta; para reservar sí se verifica el calendario autenticado del socio. Si la descarga falla, la pantalla muestra el error y permite usar el horario guardado. Un viernes aún no publicado conserva su nombre de consultas anteriores; si nunca se ha consultado, queda pendiente en lugar de recibir una actividad de otro día.

**Otra hora (manual)** permite configurar una combinación no verificada de hora y actividad, con una confirmación explícita. Todas las opciones muestran la hora. Solo se enviará una reserva si la web publica exactamente la actividad, fecha y hora elegidas. Los horarios ya seleccionados por ti se conservan al actualizar: revisa y quita los que hayas añadido por error.

## Funcionamiento con la app cerrada

Una alarma exacta de Android activa un servicio visible mediante una notificación aproximadamente un minuto antes de la apertura. Se inicia sesión, se espera hasta la hora de apertura y se consulta la disponibilidad para enviar una única solicitud por sesión. Si hay varias clases para la misma apertura, se procesan juntas; una clase sin publicar no bloquea las demás. Se permite consultar durante un margen de hasta 90 segundos tras la apertura; las solicitudes enviadas no se repiten automáticamente.

La app puede quitarse de las aplicaciones recientes. **Forzar detención**, apagar el móvil, retirar los permisos o las restricciones del fabricante pueden impedir la alarma. Después de reiniciar, desbloquea el teléfono al menos una vez. Se necesita Internet al abrirse las reservas. El modo de espera de bajo consumo de Android puede cortar la conexión incluso con un servicio activo; la app avisa si detecta ese modo.

Este sistema evita un servidor permanente, pero el teléfono, la red y el centro pueden retrasar o impedir una solicitud. No garantiza plaza ni precisión de milisegundos. GitHub Actions tampoco ofrece un horario puntual adecuado para competir por plazas a una hora concreta.

## Resultados y duplicados

- **Reservada**: la respuesta identifica la sesión solicitada o una consulta posterior confirma que la cuenta está inscrita.
- **Ya reservada**: el calendario ya mostraba la inscripción; no se toca el enlace de cancelación.
- **Completa**, **En lista de espera**, **Requiere pago**: se registra el estado y no se realiza una reserva directa.
- **Comprobar en la web**: la solicitud pudo llegar al centro, pero no se pudo confirmar el resultado. Se bloquea la repetición automática. Abre la web y comprueba tu cuenta antes de permitir otro intento.

La app no cancela reservas, paga actividades ni se apunta automáticamente a listas de espera. Estos casos se gestionan en la web. Pausar impide solicitudes futuras; una solicitud ya enviada puede terminar en el servidor.

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

`CANTOLAGUA_TOOLS` permite cambiar la carpeta de herramientas (por defecto `/workspace/.tools`). El APK se genera en `artifacts/MiCantolagua-1.0.2.apk`, junto con su SHA-256. La compilación verifica la firma del APK. Es una compilación de distribución, sin `debuggable` y con componentes internos no exportados.

La firma se conserva fuera del repositorio en `/workspace/.cantolagua-android-signing`, o en `CANTOLAGUA_SIGNING_DIR`. **Guarda una copia privada de esta carpeta para firmar futuras actualizaciones**. No la subas al repositorio. Una instalación nueva que genere otra firma no podrá actualizar el APK instalado; habrá que desinstalar antes, perdiendo los datos locales.

### Pruebas

`bash scripts/test.sh` ejecuta las pruebas de fechas, cambio de hora, repetición semanal, excepciones, parsing del calendario, protección de enlaces, confirmación de reservas y conservación del horario por día y hora. Usa transporte simulado: no envía reservas reales.

`bash scripts/build_device_tests.sh` construye una APK de instrumentación separada. **Se ejecuta exclusivamente en un emulador limpio: borra los datos locales de la aplicación de destino**. Las pruebas deterministas comprueban Keystore, persistencia, exclusión de duplicados, alarmas y selectores de cada día sin realizar conexiones. La prueba opcional `NetworkDeviceChecks` consulta la web pública real y verifica la carga automática desde una instalación sin cuenta; no inicia sesión ni envía reservas. No se distribuye con la aplicación. Ver `VALIDACION.md` para el resultado de esta entrega.

El diagnóstico opcional `tests/LiveReadOnly.java` permite verificar acceso y calendario desde una terminal privada. Lee las credenciales de forma interactiva, no acepta contraseñas en argumentos ni envía reservas. No guardar credenciales en código, comandos, logs ni archivos de configuración.

## Mantenimiento de la integración

El sitio no proporciona una API pública documentada. `ClubClient.java` interpreta el formulario y calendario actuales. Comprueba fecha, tipo de actividad, hora, identificador de sesión, destino HTTPS y persona seleccionada antes de enviar una reserva. Cualquier cambio incompatible del sitio detiene el intento y requiere revisar la integración. Las cookies solo viven en memoria durante cada ejecución.

Al reiniciar el teléfono o actualizar la aplicación, el receptor del sistema únicamente vuelve a programar la alarma. El servicio de tipo `dataSync` comienza desde una alarma exacta autorizada, no desde el arranque del sistema, respetando las restricciones de Android 15.


### Descarga del calendario completo

La web devuelve una vista móvil de un solo día cuando el identificador HTTP contiene `Android`. Esto explicaba por qué las versiones anteriores solo recibían las siete sesiones del lunes, mientras un navegador de ordenador veía 26 hasta el jueves. La 1.0.2 solicita la vista completa con el identificador de la app sin ese indicador móvil. Si la web aun así devuelve pestañas de días, consulta exclusivamente sus enlaces de calendario y reúne las sesiones publicadas. Esta lógica se aplica tanto al calendario público como al autenticado utilizado para reservar. Las pruebas incluyen las respuestas móviles reales de los cuatro días y verifican que jamás se siga un enlace de reserva o cancelación durante una lectura.
