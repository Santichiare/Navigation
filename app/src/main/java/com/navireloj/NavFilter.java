package com.navireloj;

import java.util.HashSet;
import java.util.Set;

/**
 * Guarda la indicación actual de Google Maps y decide cuándo el reloj
 * tiene que vibrar. Es Java puro (sin Android) para poder probarlo.
 *
 * El reloj consulta este estado cada segundo. La distancia se actualiza
 * siempre, pero el contador "seq" (que hace vibrar al reloj) solo sube:
 *  - cuando Maps pasa a una maniobra nueva, y
 *  - cuando la distancia cruza 500 m, 200 m y 50 m.
 */
public final class NavFilter {

    private NavFilter() {}

    /** Recibe cada aviso (giro nuevo o umbral cruzado). Lo usa el celu para mandar el aviso breve. */
    public interface AlertListener {
        void onAlert(String title, String body);
    }

    private static AlertListener alertListener = null;
    private static boolean notifyEnabled = true;
    private static boolean vibrateEnabled = true;
    private static String screenMode = "always";

    /** "always" (siempre activa, se atenúa) o "auto" (se apaga sola). */
    public static synchronized void setScreenMode(String mode) {
        screenMode = "auto".equals(mode) ? "auto" : "always";
    }

    public static synchronized void setAlertListener(AlertListener l) {
        alertListener = l;
    }

    public static synchronized void setSettings(boolean notify, boolean vibrate) {
        notifyEnabled = notify;
        vibrateEnabled = vibrate;
    }

    /** Umbrales en metros en los que vibra el reloj. */
    static final int[] THRESHOLDS = {500, 200, 50};

    // Estado interno del filtro
    private static String currentKey = null;
    private static int lastDistance = -1;
    private static String lastRaw = null;
    private static final Set<Integer> fired = new HashSet<>();

    // Estado que se publica al reloj
    private static boolean active = false;
    private static int seq = 0;
    private static String dir = "";
    private static int dist = -1;
    private static String instruction = "";
    private static String eta = "";
    private static int remaining = -1;
    private static long updatedAt = 0;

    /** Vuelve a empezar (por ejemplo, antes de una simulación). */
    public static synchronized void reset() {
        currentKey = null;
        lastDistance = -1;
        lastRaw = null;
        fired.clear();
        active = false;
        dir = "";
        dist = -1;
        instruction = "";
        eta = "";
        remaining = -1;
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Procesa una notificación de navegación de Maps.
     * @return true si el contenido cambió (sirve para el registro de diagnóstico)
     */
    public static synchronized boolean onMapsUpdate(String title, String text, String subText) {
        return onMapsUpdate(title, text, subText, "");
    }

    /**
     * @param extraText otros campos de la notificación (texto largo, ticker...).
     *                  Solo se usan para buscar la distancia y la dirección si
     *                  no aparecen en el título ni en el texto.
     */
    public static synchronized boolean onMapsUpdate(String title, String text, String subText, String extraText) {
        title = NavParser.clean(title);
        text = NavParser.clean(text);
        String sub = NavParser.clean(subText);
        String extra = NavParser.clean(extraText);

        String raw = title + " | " + text + " | " + sub + " | " + extra;
        if (raw.equals(lastRaw)) return false;
        lastRaw = raw;

        String key = NavParser.stepKey(title, text);
        if (key.isEmpty()) return true;

        int d = NavParser.parseMeters(title);
        if (d < 0) d = NavParser.parseMeters(text);
        if (d < 0) d = NavParser.parseMeters(extra);

        boolean alert = false;

        if (!key.equals(currentKey)) {
            // Maniobra nueva
            currentKey = key;
            fired.clear();
            markReached(d);
            alert = true;
        } else if (d >= 0) {
            // Si la distancia sube mucho, probablemente recalculó la ruta
            if (lastDistance >= 0 && d > lastDistance + 150) {
                fired.clear();
                markReached(d);
            }
            for (int t : THRESHOLDS) {
                if (d <= t && !fired.contains(t)) {
                    alert = true;
                    break;
                }
            }
            if (alert) markReached(d);
        }
        if (d >= 0) lastDistance = d;

        String full = title + " " + text;
        active = true;
        dir = NavParser.dirCode(full);
        if (dir.isEmpty()) dir = NavParser.dirCode(extra);
        dist = d;
        String fullInstruction = NavParser.withoutDistances(full);
        // En rotondas y salidas se deja el texto completo ("tomá la 2.ª salida")
        instruction = ("roundabout".equals(dir) || "exit".equals(dir))
                ? fullInstruction
                : NavParser.shortInstruction(fullInstruction);
        eta = sub;
        // Distancia hasta el destino: suele venir junto a la hora de llegada
        // ("12 min · 3,4 km · 23:45")
        remaining = NavParser.parseMeters(sub);
        updatedAt = System.currentTimeMillis();
        if (alert) {
            seq++;
            if (notifyEnabled && alertListener != null) {
                String arrow = NavParser.direction(full);
                String distText = (dist >= 0 && dist <= 50) ? "YA" : NavParser.formatDistance(dist);
                String t = (arrow + (arrow.isEmpty() || distText.isEmpty() ? "" : " · ") + distText).trim();
                if (t.isEmpty()) t = "Navegación";
                try {
                    alertListener.onAlert(t, instruction);
                } catch (Exception ignored) {
                }
            }
        }
        return true;
    }

    /** Maps cerró la navegación. */
    public static synchronized void onNavEnded() {
        reset();
    }

    /** Marca como ya avisados todos los umbrales iguales o mayores a la distancia actual. */
    private static void markReached(int d) {
        if (d < 0) return;
        for (int t : THRESHOLDS) {
            if (t >= d) fired.add(t);
        }
    }

    /** Estado actual en JSON, tal como lo lee el reloj. */
    public static synchronized String toJson() {
        String distText = (dist >= 0 && dist <= 50) ? "YA" : NavParser.formatDistance(dist);
        long age = updatedAt == 0 ? -1 : System.currentTimeMillis() - updatedAt;
        return "{"
                + "\"active\":" + active
                + ",\"seq\":" + seq
                + ",\"dir\":" + quote(dir)
                + ",\"label\":" + quote(NavParser.dirLabel(dir))
                + ",\"dist\":" + dist
                + ",\"distText\":" + quote(distText)
                + ",\"instruction\":" + quote(instruction)
                + ",\"eta\":" + quote(eta)
                + ",\"remaining\":" + remaining
                + ",\"remainingText\":" + quote(NavParser.formatDistance(remaining))
                + ",\"age\":" + age
                + ",\"notify\":" + notifyEnabled
                + ",\"vibrate\":" + vibrateEnabled
                + ",\"screen\":" + quote(screenMode)
                + "}";
    }

    static String quote(String s) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
