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
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Procesa una notificación de navegación de Maps.
     * @return true si el contenido cambió (sirve para el registro de diagnóstico)
     */
    public static synchronized boolean onMapsUpdate(String title, String text, String subText) {
        title = NavParser.clean(title);
        text = NavParser.clean(text);
        String sub = NavParser.clean(subText);

        String raw = title + " | " + text + " | " + sub;
        if (raw.equals(lastRaw)) return false;
        lastRaw = raw;

        String key = NavParser.stepKey(title, text);
        if (key.isEmpty()) return true;

        int d = NavParser.parseMeters(title);
        if (d < 0) d = NavParser.parseMeters(text);

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
        dist = d;
        String fullInstruction = NavParser.withoutDistances(full);
        // En rotondas y salidas se deja el texto completo ("tomá la 2.ª salida")
        instruction = ("roundabout".equals(dir) || "exit".equals(dir))
                ? fullInstruction
                : NavParser.shortInstruction(fullInstruction);
        eta = sub;
        updatedAt = System.currentTimeMillis();
        if (alert) seq++;
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
                + ",\"age\":" + age
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
