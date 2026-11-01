# NaviReloj

Manda al reloj Amazfit solo las indicaciones importantes de Google Maps, en vez de una notificación cada pocos metros.

**Cuándo vibra el reloj:**
- Cuando Maps pasa a una maniobra nueva (te avisa el próximo giro).
- A los 500 m, 200 m y 50 m de cada giro (a los 50 m dice "YA").
- Nada más. Las actualizaciones intermedias de Maps se ignoran.

Ejemplo de lo que ves en el reloj: `← IZQUIERDA · 200 m` / `Girá a la izquierda hacia Av. Corrientes`

---

## 1. Subir el proyecto a GitHub

1. Entrá a [github.com](https://github.com), tocá **New** (repositorio nuevo), ponele de nombre `NaviReloj`. Puede ser privado. Tocá **Create repository**.
2. En la página del repo vacío, tocá el link **uploading an existing file**.
3. Arrastrá **todo el contenido** de la carpeta `NaviReloj` (no la carpeta en sí, sino lo que hay adentro), incluida la carpeta `.github`.
   - En Mac, `.github` está oculta en Finder: apretá `Cmd + Shift + .` para verla.
4. Tocá **Commit changes**.

## 2. Descargar el APK

1. Andá a la pestaña **Actions** del repo. Vas a ver "Compilar APK" corriendo (tarda unos 3–5 minutos).
2. Cuando aparezca el tilde verde ✔, entrá a esa ejecución.
3. Abajo, en **Artifacts**, descargá **NaviReloj**. Es un .zip con `NaviReloj.apk` adentro.

Si sale una cruz roja ✖, entrá a la ejecución, copiá el error y pasáselo a Claude.

## 3. Instalar en el celular

1. Pasá `NaviReloj.apk` al celu (cable, Drive, WhatsApp a vos mismo, etc.) y abrilo. Android te va a pedir permiso para instalar apps desconocidas: aceptá.
2. **Android 13 o superior:** antes de dar el acceso a notificaciones, andá a *Ajustes → Apps → NaviReloj*, tocá los tres puntitos ⋮ arriba a la derecha y elegí **Permitir configuración restringida**. Sin esto, Android no te deja activar el acceso a notificaciones en apps instaladas a mano.
3. Abrí NaviReloj y tocá los tres botones en orden:
   1. Dar acceso a notificaciones → activá NaviReloj.
   2. Permitir que muestre avisos.
   3. Quitar restricción de batería → buscá NaviReloj y poné "Sin restricciones".

## 4. Configurar Zepp

En la app Zepp: *Perfil → tu reloj → Notificaciones / Alertas de apps*:
- **Desactivá** Google Maps.
- **Activá** NaviReloj.

## 5. Probar

- Tocá **Probar en el reloj (simulación de 30 s)**. Te deberían llegar 6 avisos al reloj en medio minuto.
- Después probá con una navegación real en Google Maps.

## Si algo no anda bien con Maps real

La app guarda los últimos textos que recibió de Google Maps (abajo de todo en la pantalla). Mantené apretado el texto para copiarlo y pasáselo a Claude: con eso se ajusta el filtro al formato exacto que usa tu Maps.

---

### Para modificar la app

- La lógica de cuándo avisar está en `app/src/main/java/com/navireloj/NavFilter.java` (los umbrales están en `THRESHOLDS`).
- Cómo se interpretan las distancias y las flechas está en `NavParser.java`.
- Cada vez que subas cambios a GitHub se compila un APK nuevo. Se instala encima del anterior sin desinstalar.
