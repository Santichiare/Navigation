package com.navireloj;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lógica pura (sin dependencias de Android) para interpretar
 * el texto de las notificaciones de navegación de Google Maps.
 */
public final class NavParser {

    private NavParser() {}

    // "200 m", "1,2 km", "350 metros", "2 kilómetros"
    private static final Pattern DIST = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(km|kil[oó]metros?|metros?|m)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Reemplaza los espacios raros que usa Maps por espacios comunes. */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ').replace(' ', ' ').trim();
    }

    /** Devuelve la distancia en metros, o -1 si no hay ninguna. */
    public static int parseMeters(String s) {
        Matcher m = DIST.matcher(clean(s));
        if (!m.find()) return -1;
        double value;
        try {
            value = Double.parseDouble(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        if (unit.startsWith("k")) value *= 1000;
        return (int) Math.round(value);
    }

    /** Quita las distancias del texto, para quedarse con la indicación. */
    public static String withoutDistances(String s) {
        return DIST.matcher(clean(s)).replaceAll("")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s·•,\\-–]+|[\\s·•,\\-–]+$", "")
                .trim();
    }

    /**
     * Identifica la maniobra actual. Cambia cuando Maps pasa a la
     * siguiente indicación, pero no cuando solo baja la distancia.
     */
    public static String stepKey(String title, String text) {
        return normalize(withoutDistances(clean(title) + " " + clean(text)));
    }

    /** Minúsculas y sin tildes, para comparar palabras. */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static boolean has(String s, String regex) {
        return Pattern.compile(regex).matcher(s).find();
    }

    /** Flecha + palabra según la maniobra, o "" si no se reconoce. */
    public static String direction(String rawText) {
        String s = normalize(rawText);
        boolean left = has(s, "izquierda|\\bleft\\b");
        boolean right = has(s, "derecha|\\bright\\b");
        boolean slight = has(s, "ligeramente|levemente|\\bslight|mantene|mantente|\\bkeep\\b");

        if (has(s, "media vuelta|vuelta en u|cambio de sentido|u-turn|\\bu turn")) return "↩ VUELTA EN U";
        if (has(s, "rotonda|roundabout")) return "↻ ROTONDA";
        if (has(s, "destino|llegaste|llegada|\\barrive|destination")) return "◎ DESTINO";
        if (slight && left) return "↖ IZQ.";
        if (slight && right) return "↗ DER.";
        if (left) return "← IZQUIERDA";
        if (right) return "→ DERECHA";
        if (has(s, "salida|\\bexit\\b")) return "↗ SALIDA";
        if (has(s, "continua|segui|sigue|recto|\\bcontinue\\b|\\bhead\\b|\\bstraight\\b")) return "↑ SEGUÍ";
        return "";
    }

    /** "200 m", "1,2 km". */
    public static String formatDistance(int meters) {
        if (meters < 0) return "";
        if (meters < 1000) {
            int rounded = (int) (Math.round(meters / 10.0) * 10);
            return rounded + " m";
        }
        double km = Math.round(meters / 100.0) / 10.0;
        String s = (km == Math.floor(km)) ? String.valueOf((int) km) : String.valueOf(km);
        return s.replace('.', ',') + " km";
    }
}
