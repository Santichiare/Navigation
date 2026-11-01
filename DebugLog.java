package com.navireloj;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Guarda las últimas notificaciones de Maps tal como llegan.
 * Sirve para ver qué texto manda Maps y ajustar el filtro.
 */
public final class DebugLog {

    private DebugLog() {}

    private static final int MAX_LINES = 25;
    private static final String PREFS = "debug";
    private static final String KEY = "log";

    public static synchronized void add(Context ctx, String line) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        String current = p.getString(KEY, "");
        String updated = time + "  " + line + (current.isEmpty() ? "" : "\n" + current);

        String[] lines = updated.split("\n");
        if (lines.length > MAX_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LINES; i++) {
                if (i > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            updated = sb.toString();
        }
        p.edit().putString(KEY, updated).apply();
    }

    public static String read(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
