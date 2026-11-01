import { BaseSideService } from "@zeppos/zml/base-side";

// Corre dentro de la app Zepp, en el celular.
// Consulta el servidor local de la app NaviReloj de Android y le pasa
// la indicación actual al reloj.
const NAV_URL = "http://127.0.0.1:27380/nav";

function getNav(res) {
  fetch({
    url: `${NAV_URL}?t=${Date.now()}`,
    method: "GET",
  })
    .then((response) => {
      const body =
        typeof response.body === "string"
          ? JSON.parse(response.body)
          : response.body;
      res(null, { ok: true, nav: body });
    })
    .catch((error) => {
      res(null, { ok: false, error: String(error) });
    });
}

AppSideService(
  BaseSideService({
    onInit() {},

    onRequest(req, res) {
      if (req.method === "GET_NAV") {
        getNav(res);
      } else {
        res(null, { ok: false, error: "unknown method" });
      }
    },

    onRun() {},

    onDestroy() {},
  })
);
