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

    // "... hacia X", "... con dirección a X", "... en dirección a X", "... por X", "onto X"
    private static final Pattern STREET_AFTER = Pattern.compile(
            "^.*?(?:\\b(?:con|en)\\s+)?\\b(?:hacia|direcci[oó]n\\s+a|por|onto|toward|towards)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // "2.ª salida", "la 2a salida", "segunda salida", "exit 12"
    private static final Pattern EXIT_NUM = Pattern.compile(
            "(\\d+)\\s*\\.?\\s*[ªaº°]?\\s*salida|salida\\s+(\\d+)|exit\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Solo el nombre de la calle, o "" si la indicación no nombra ninguna. */
    public static String streetOnly(String instruction) {
        Matcher m = STREET_AFTER.matcher(clean(instruction));
        if (!m.matches()) return "";
        return trimPunct(m.group(1));
    }

    /**
     * Texto corto para el reloj: la flecha ya dice el giro, así que queda la
     * calle ("Pedro Lozano"). En rotondas y salidas agrega el número de salida
     * ("2.ª salida · Pedro Lozano").
     */
    public static String shortInstruction(String instruction) {
        return shortInstruction(instruction, "");
    }

    public static String shortInstruction(String instruction, String dirCode) {
        String s = clean(instruction);
        String street = streetOnly(s);
        if ("roundabout".equals(dirCode) || "exit".equals(dirCode)) {
            Matcher m = EXIT_NUM.matcher(s);
            String exit = "";
            if (m.find()) {
                String n = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
                exit = "roundabout".equals(dirCode) ? n + ".ª salida" : "Salida " + n;
            }
            if (!exit.isEmpty() && !street.isEmpty()) return exit + " · " + street;
            if (!exit.isEmpty()) return exit;
        }
        if (!street.isEmpty()) return street;
        return trimPunct(s);
    }

    private static String trimPunct(String s) {
        return s.replaceAll("[\\s.,;:]+$", "").trim();
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

    /**
     * Código de la maniobra: left, right, slight_left, slight_right, uturn,
     * roundabout, exit, straight, arrive, o "" si no se reconoce.
     * El reloj usa este código para elegir la flecha.
     */
    public static String dirCode(String rawText) {
        String s = normalize(rawText);
        boolean left = has(s, "izquierda|\\bleft\\b");
        boolean right = has(s, "derecha|\\bright\\b");
        boolean slight = has(s, "ligeramente|levemente|\\bslight|mantene|mantente|\\bkeep\\b");

        if (has(s, "media vuelta|vuelta en u|cambio de sentido|u-turn|\\bu turn")) return "uturn";
        if (has(s, "rotonda|roundabout")) return "roundabout";
        if (has(s, "destino|llegaste|llegada|\\barrive|destination")) return "arrive";
        if (slight && left) return "slight_left";
        if (slight && right) return "slight_right";
        if (left) return "left";
        if (right) return "right";
        if (has(s, "salida|\\bexit\\b")) return "exit";
        if (has(s, "continua|segui|sigue|recto|dirigete|dirigite|\\bcontinue\\b|\\bhead\\b|\\bstraight\\b")) return "straight";
        return "";
    }

    /** Texto corto para mostrar debajo de la flecha. */
    public static String dirLabel(String code) {
        switch (code) {
            case "left": return "IZQUIERDA";
            case "right": return "DERECHA";
            case "slight_left": return "LEVE IZQ.";
            case "slight_right": return "LEVE DER.";
            case "uturn": return "VUELTA EN U";
            case "roundabout": return "ROTONDA";
            case "exit": return "SALIDA";
            case "straight": return "SEGUÍ";
            case "arrive": return "DESTINO";
            default: return "";
        }
    }

    /** Flecha + palabra (se usa en la pantalla de diagnóstico del celu). */
    public static String direction(String rawText) {
        String code = dirCode(rawText);
        switch (code) {
            case "left": return "← IZQUIERDA";
            case "right": return "→ DERECHA";
            case "slight_left": return "↖ IZQ.";
            case "slight_right": return "↗ DER.";
            case "uturn": return "↩ VUELTA EN U";
            case "roundabout": return "↻ ROTONDA";
            case "exit": return "↗ SALIDA";
            case "straight": return "↑ SEGUÍ";
            case "arrive": return "◎ DESTINO";
            default: return "";
        }
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
