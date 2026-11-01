import * as hmUI from "@zos/ui";
import { px } from "@zos/utils";
import { getDeviceInfo } from "@zos/device";
import { Vibrator } from "@zos/sensor";
import { setPageBrightTime } from "@zos/display";
import { BasePage } from "@zeppos/zml/base-page";

const { width: W } = getDeviceInfo();

// Cada cuánto se consulta al celular (ms)
const POLL_MS = 1000;
// Cuánto tiempo se mantiene encendida la pantalla tras cada aviso (ms)
const BRIGHT_MS = 10 * 60 * 1000;

const ARROW_SIZE = 220;
const ARROWS = [
  "straight",
  "left",
  "right",
  "slight_left",
  "slight_right",
  "exit",
  "uturn",
  "roundabout",
  "arrive",
  "unknown",
];

const MSG_NO_PHONE =
  "No encuentro NaviReloj en el celu.\n\nRevisá que la app tenga acceso a notificaciones.";
const MSG_IDLE = "Iniciá una navegación en Google Maps";

const vibrator = new Vibrator();
let pollTimer = null;

Page(
  BasePage({
    state: {
      arrows: {},
      currentArrow: null,
      etaText: null,
      distText: null,
      instrText: null,
      msgText: null,
      lastSeq: null,
      inFlight: false,
      inFlightSince: 0,
      failures: 0,
    },

    build() {
      setPageBrightTime({ brightTime: BRIGHT_MS });

      // Línea superior: tiempo / distancia restante
      this.state.etaText = hmUI.createWidget(hmUI.widget.TEXT, {
        x: px(100),
        y: px(50),
        w: W - 2 * px(100),
        h: px(40),
        color: 0x9a9a9a,
        text_size: px(24),
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.CENTER_V,
        text: "",
      });

      // Flechas: una imagen por maniobra, se muestra solo la actual
      const ax = Math.round((W - ARROW_SIZE) / 2);
      const ay = px(94);
      ARROWS.forEach((name) => {
        this.state.arrows[name] = hmUI.createWidget(hmUI.widget.IMG, {
          x: ax,
          y: ay,
          src: `arrows/${name}.png`,
        });
        this.state.arrows[name].setProperty(hmUI.prop.VISIBLE, false);
      });

      // Distancia grande
      this.state.distText = hmUI.createWidget(hmUI.widget.TEXT, {
        x: px(40),
        y: px(316),
        w: W - 2 * px(40),
        h: px(72),
        color: 0xffffff,
        text_size: px(64),
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.CENTER_V,
        text: "",
      });

      // Calle / indicación
      this.state.instrText = hmUI.createWidget(hmUI.widget.TEXT, {
        x: px(80),
        y: px(386),
        w: W - 2 * px(80),
        h: px(62),
        color: 0xcfcfcf,
        text_size: px(24),
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.TOP,
        text_style: hmUI.text_style.WRAP,
        text: "",
      });

      // Mensaje de estado (cuando no hay navegación o no hay conexión)
      this.state.msgText = hmUI.createWidget(hmUI.widget.TEXT, {
        x: px(60),
        y: px(120),
        w: W - 2 * px(60),
        h: px(240),
        color: 0xffffff,
        text_size: px(32),
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.CENTER_V,
        text_style: hmUI.text_style.WRAP,
        text: "Conectando…",
      });

      this.poll();
      pollTimer = setInterval(() => this.poll(), POLL_MS);
    },

    poll() {
      const now = Date.now();
      // Evita pedidos superpuestos; si uno quedó colgado, lo descarta a los 5 s
      if (this.state.inFlight && now - this.state.inFlightSince < 5000) return;
      this.state.inFlight = true;
      this.state.inFlightSince = now;

      this.request({ method: "GET_NAV" })
        .then((data) => {
          this.state.inFlight = false;
          if (data && data.ok && data.nav) {
            this.state.failures = 0;
            this.render(data.nav);
          } else {
            this.onFailure();
          }
        })
        .catch(() => {
          this.state.inFlight = false;
          this.onFailure();
        });
    },

    onFailure() {
      this.state.failures += 1;
      // Tolera algún fallo suelto antes de mostrar el error
      if (this.state.failures >= 3) {
        this.showMessage(MSG_NO_PHONE);
      }
    },

    render(nav) {
      if (!nav.active) {
        this.state.lastSeq = nav.seq;
        this.showMessage(MSG_IDLE);
        return;
      }

      this.state.msgText.setProperty(hmUI.prop.VISIBLE, false);
      this.showArrow(nav.dir || "unknown");
      this.state.distText.setProperty(hmUI.prop.TEXT, nav.distText || "");
      this.state.instrText.setProperty(hmUI.prop.TEXT, nav.instruction || "");
      this.state.etaText.setProperty(hmUI.prop.TEXT, nav.eta || "");

      // Vibra solo cuando el celu marcó un aviso nuevo
      if (this.state.lastSeq !== null && nav.seq !== this.state.lastSeq) {
        this.alert(nav.distText === "YA");
      }
      this.state.lastSeq = nav.seq;
    },

    showArrow(name) {
      if (!this.state.arrows[name]) name = "unknown";
      if (this.state.currentArrow === name) return;
      if (this.state.currentArrow) {
        this.state.arrows[this.state.currentArrow].setProperty(hmUI.prop.VISIBLE, false);
      }
      this.state.arrows[name].setProperty(hmUI.prop.VISIBLE, true);
      this.state.currentArrow = name;
    },

    showMessage(text) {
      if (this.state.currentArrow) {
        this.state.arrows[this.state.currentArrow].setProperty(hmUI.prop.VISIBLE, false);
        this.state.currentArrow = null;
      }
      this.state.distText.setProperty(hmUI.prop.TEXT, "");
      this.state.instrText.setProperty(hmUI.prop.TEXT, "");
      this.state.etaText.setProperty(hmUI.prop.TEXT, "");
      this.state.msgText.setProperty(hmUI.prop.TEXT, text);
      this.state.msgText.setProperty(hmUI.prop.VISIBLE, true);
    },

    alert(isNow) {
      setPageBrightTime({ brightTime: BRIGHT_MS });
      try {
        vibrator.stop();
        vibrator.start();
        if (isNow) {
          // Doble vibración cuando hay que girar ya
          setTimeout(() => {
            vibrator.stop();
            vibrator.start();
          }, 600);
        }
      } catch (e) {
        // si el reloj no permite vibrar en este momento, seguimos igual
      }
    },

    onDestroy() {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
      try {
        vibrator.stop();
      } catch (e) {}
    },
  })
);
