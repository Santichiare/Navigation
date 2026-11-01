package com.navireloj;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * Aviso breve: en cada aviso de navegación publica una notificación que
 * Zepp reenvía al reloj (prende la pantalla y vibra aunque la app del reloj
 * esté congelada) y la borra sola enseguida.
 */
public final class Notifier implements NavFilter.AlertListener {

    /** Cuánto dura la notificación antes de borrarse sola (ms). */
    static final long DURATION_MS = 1000;

    static final String CHANNEL_ID = "aviso_breve";
    private static final int BASE_ID = 7000;
    private static final String PREFS = "ajustes";

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int counter = 0;

    private static Notifier instance;

    /** Registra el aviso breve y carga los ajustes guardados. */
    public static synchronized void install(Context context) {
        Context app = context.getApplicationContext();
        if (instance == null) instance = new Notifier(app);
        NavFilter.setAlertListener(instance);
        // El aviso breve quedó descartado (el reloj muestra la notificación demasiado tiempo)
        NavFilter.setSettings(false, isVibrateEnabled(app));
        NavFilter.setScreenMode(getScreenMode(app));
    }

    private Notifier(Context ctx) {
        this.ctx = ctx;
    }

    // ---- ajustes ----

    static boolean isNotifyEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("notify", false);
    }

    static boolean isVibrateEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("vibrate", true);
    }

    static String getScreenMode(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("screen", "always");
    }

    static void saveScreenMode(Context c, String mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("screen", mode).apply();
        NavFilter.setScreenMode(mode);
    }

    static void save(Context c, boolean notify, boolean vibrate) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putBoolean("notify", notify).putBoolean("vibrate", vibrate).apply();
        NavFilter.setSettings(false, vibrate);
    }

    // ---- aviso ----

    @Override
    public void onAlert(String title, String body) {
        handler.post(() -> post(title, body));
    }

    private void post(String title, String body) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(nm);

        // ID nuevo en cada aviso: Zepp ignora las actualizaciones de una notificación existente
        counter = (counter + 1) % 1000;
        final int id = BASE_ID + counter;

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setTimeoutAfter(DURATION_MS)
                .build();
        nm.notify(id, n);
        // Por las dudas, la borramos también a mano
        handler.postDelayed(() -> nm.cancel(id), DURATION_MS);
    }

    static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Aviso breve al reloj", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Notificación de 1 segundo que prende la pantalla del reloj en cada aviso");
        ch.enableVibration(false); // que no vibre el celu
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }
}
