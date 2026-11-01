# NaviReloj

Indicaciones de Google Maps en una app propia del reloj Amazfit (hecha para el Active 3 Premium), sin el spam de notificaciones.

En el reloj ves una flecha grande, la distancia actualizándose en vivo y la calle. Vibra solo:
- cuando Maps pasa a una maniobra nueva,
- a los 500 m, 200 m y 50 m de cada giro (a los 50 m vibra dos veces y dice "YA").

## Cómo funciona

```
Google Maps ──notificación──▶ NaviReloj (celu) ──127.0.0.1:27380──▶ Zepp (parte de la app del reloj) ──Bluetooth──▶ NaviReloj (reloj)
```

- `app/` → app de Android. Lee las notificaciones de Maps y deja la indicación actual en un mini servidor que solo es accesible desde el mismo celular.
- `reloj/` → app del reloj (Zepp OS). Una parte corre dentro de la app Zepp del celu, consulta ese servidor cada segundo y le pasa los datos al reloj.

---

## Parte 1: app del celular

### Actualizarla en GitHub

Subí a tu repo las carpetas `app` y `reloj` (arrastrándolas en **Add file → Upload files**) y tocá **Commit changes**. Se reemplazan los archivos viejos. La carpeta `.github` ya la tenés, no hace falta volver a subirla.

### Descargar e instalar

1. En **Actions**, esperá el tilde verde ✔, entrá a la ejecución y descargá **NaviReloj** desde *Artifacts*.
2. Instalá el APK **encima** del anterior (no hace falta desinstalar).
3. Abrí NaviReloj y verificá que arriba diga:
   - ✔ Acceso a notificaciones
   - ✔ Conexión para el reloj

   Si la segunda tiene ✖, tocá **1. Dar acceso a notificaciones**, desactivá NaviReloj y volvé a activarlo.
4. En la app **Zepp**, desactivá las notificaciones de Google Maps (*Perfil → tu reloj → Alertas de apps*). Las de NaviReloj ya no hacen falta.

---

## Parte 2: app del reloj (una sola vez, desde la compu)

### Lo que hay que instalar

1. **Node.js** (versión LTS) desde [nodejs.org](https://nodejs.org). Instalalo con las opciones por defecto y reiniciá VS Code.
2. En la terminal de VS Code (*Terminal → New Terminal*):
   ```
   npm install -g @zeppos/zeus-cli
   ```

### Cuenta de desarrollador de Zepp

1. Creá una cuenta gratis en [console.zepp.com](https://console.zepp.com) (podés usar la misma cuenta de la app Zepp).
2. En la terminal:
   ```
   zeus login
   ```
   Se abre el navegador para iniciar sesión.

### Modo desarrollador en la app Zepp del celu

En la app Zepp: *Perfil → Ajustes → Acerca de* y tocá **7 veces** el logo de Zepp. Después aparece **Modo desarrollador** en *Perfil → Ajustes*.

### Instalar en el reloj

1. Descargá el repo a tu compu (en GitHub: botón verde **Code → Download ZIP**) y descomprimilo. O usá la carpeta que ya tenés.
2. En la terminal de VS Code, entrá a la carpeta `reloj`:
   ```
   cd ruta/a/NaviReloj/reloj
   npm install
   zeus preview
   ```
3. Te va a preguntar para qué reloj: elegí **Amazfit Active 3 Premium**. Si no aparece en la lista, elegí uno redondo de 466×466 (por ejemplo **Amazfit Active 2 (Round)**).
4. Aparece un código **QR** en la terminal. En la app Zepp: *Perfil → Ajustes → Modo desarrollador → Escanear* y escanealo.
5. La app se instala en el reloj. La vas a encontrar en la lista de apps como **NaviReloj**.

---

## Probar

1. Abrí **NaviReloj en el reloj**. Tiene que decir "Iniciá una navegación en Google Maps".
2. En **NaviReloj del celu**, tocá **Probar con el reloj (simulación de 1 minuto)**.
3. En el reloj deberías ver las flechas cambiar, la distancia bajar y sentir las vibraciones.
4. Después probá con una navegación real en Google Maps, con la app del reloj abierta.

## Si algo no anda

| En el reloj dice… | Qué revisar |
|---|---|
| "No encuentro NaviReloj en el celu" | Que la app del celu muestre ✔ en "Conexión para el reloj", que el reloj esté conectado a Zepp, y que Zepp esté abierta o en segundo plano. |
| "Iniciá una navegación…" aunque Maps está navegando | Que NaviReloj tenga acceso a notificaciones y que las notificaciones de Google Maps no estén silenciadas en Android. |
| La flecha no corresponde | Copiá el registro de diagnóstico de la app del celu y pasáselo a Claude para ajustar el filtro. |

### Para modificar

- Umbrales de vibración: `THRESHOLDS` en `app/src/main/java/com/navireloj/NavFilter.java`.
- Cómo se interpreta el texto de Maps: `NavParser.java`.
- Pantalla del reloj: `reloj/page/index.js`. Flechas: `reloj/assets/common.r/arrows/`.
- Después de cambiar algo del reloj, volvé a correr `zeus preview` y escaneá el QR nuevo.
