package com.navireloj;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import java.util.HashSet;
import java.util.Set;

/**
 * Decide cuándo vale la pena avisar al reloj.
 *
 * Reglas:
 *  - Cuando Maps pasa a una maniobra nueva: un aviso.
 *  - Cuando la distancia cruza 500 m, 200 m y 50 m: un aviso en cada umbral.
 *  - Cualquier otra actualización de Maps (230 m, 220 m, 210 m...) se ignora.
 */
public final class NavFilter {

    private NavFilter() {}

    /** Umbrales en metros en los que vibra el reloj. */
    static final int[] THRESHOLDS = {500, 200, 50};

    static final String CHANNEL_ID = "indicaciones";
    static final int NOTIF_ID = 4242;

    private static String currentKey = null;
    private static int lastDistance = -1;
    private static String lastRaw = null;
    private static final Set<Integer> fired = new HashSet<>();

    public static synchronized void reset() {
        currentKey = null;
        lastDistance = -1;
        lastRaw = null;
        fired.clear();
    }

    public static synchronized void onMapsUpdate(Context ctx, String title, String text) {
        title = NavParser.clean(title);
        text = NavParser.clean(text);

        String raw = title + " | " + text;
        if (raw.equals(lastRaw)) return;
        lastRaw = raw;
        DebugLog.add(ctx, raw);

        String key = NavParser.stepKey(title, text);
        if (key.isEmpty()) return;

        int dist = NavParser.parseMeters(title);
        if (dist < 0) dist = NavParser.parseMeters(text);

        boolean alert = false;

        if (!key.equals(currentKey)) {
            // Maniobra nueva
            currentKey = key;
            fired.clear();
            markReached(dist);
            alert = true;
        } else if (dist >= 0) {
            // Si la distancia sube mucho, probablemente recalculó la ruta
            if (lastDistance >= 0 && dist > lastDistance + 150) {
                fired.clear();
                markReached(dist);
            }
            for (int t : THRESHOLDS) {
                if (dist <= t && !fired.contains(t)) {
                    alert = true;
                    break;
                }
            }
            if (alert) markReached(dist);
        }

        if (dist >= 0) lastDistance = dist;

        if (alert) post(ctx, title, text, dist);
    }

    public static synchronized void onNavEnded(Context ctx) {
        reset();
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    /** Marca como ya avisados todos los umbrales iguales o mayores a la distancia actual. */
    private static void markReached(int dist) {
        if (dist < 0) return;
        for (int t : THRESHOLDS) {
            if (t >= dist) fired.add(t);
        }
    }

    private static void post(Context ctx, String title, String text, int dist) {
        String full = title + " " + text;
        String dir = NavParser.direction(full);

        String distPart;
        if (dist >= 0 && dist <= 50) distPart = "YA";
        else distPart = NavParser.formatDistance(dist);

        StringBuilder head = new StringBuilder();
        if (!dir.isEmpty()) head.append(dir);
        if (!distPart.isEmpty()) {
            if (head.length() > 0) head.append(" · ");
            head.append(distPart);
        }
        if (head.length() == 0) head.append("Navegación");

        String body = NavParser.withoutDistances(full);

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(nm);

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(head.toString())
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setShowWhen(false)
                .build();

        nm.notify(NOTIF_ID, n);
    }

    static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Indicaciones para el reloj", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Avisos filtrados de Google Maps que se mandan al reloj");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }
}
