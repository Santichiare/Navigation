package com.navireloj;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Pantalla de configuración, prueba y diagnóstico. */
public class MainActivity extends Activity {

    private TextView status;
    private TextView log;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean simulating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.install(this);

        int pad = dp(16);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("NaviReloj");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Lee las indicaciones de Google Maps y se las pasa a la app NaviReloj "
                + "del reloj, que muestra la flecha y la distancia en vivo.\n\n"
                + "En la app Zepp: desactivá Google Maps en Alertas de apps y "
                + "activá NaviReloj (para el aviso breve).");
        intro.setPadding(0, dp(8), 0, dp(16));
        col.addView(intro);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 0, 0, dp(12));
        col.addView(status);

        col.addView(button("1. Dar acceso a notificaciones", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));

        col.addView(button("2. Permitir avisos (para el aviso breve)", v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } else {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
        }));

        col.addView(button("3. Quitar restricción de batería", v ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))));

        TextView optTitle = new TextView(this);
        optTitle.setText("\nOpciones");
        optTitle.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(optTitle);

        final Switch notifySw = new Switch(this);
        notifySw.setText("Aviso breve en el reloj\n(prende la pantalla 1 s en cada aviso)");
        notifySw.setChecked(Notifier.isNotifyEnabled(this));
        col.addView(notifySw);

        final Switch vibrateSw = new Switch(this);
        vibrateSw.setText("Vibración de la app del reloj\n(cuando la estás mirando)");
        vibrateSw.setChecked(Notifier.isVibrateEnabled(this));
        col.addView(vibrateSw);

        notifySw.setOnCheckedChangeListener((b, on) ->
                Notifier.save(this, on, vibrateSw.isChecked()));
        vibrateSw.setOnCheckedChangeListener((b, on) ->
                Notifier.save(this, notifySw.isChecked(), on));

        col.addView(button("Probar con el reloj (simulación de 1 minuto)", v -> simulate()));

        TextView logTitle = new TextView(this);
        logTitle.setText("\nÚltimos textos recibidos de Google Maps (diagnóstico):");
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(logTitle);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        log.setPadding(0, dp(8), 0, dp(8));
        col.addView(log);

        col.addView(button("Actualizar", v -> refresh()));
        col.addView(button("Borrar registro", v -> {
            DebugLog.clear(this);
            refresh();
        }));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean listener = listenerEnabled();
        boolean server = NavServer.isRunning();

        status.setText((listener ? "✔" : "✖") + " Acceso a notificaciones\n"
                + (server ? "✔" : "✖") + " Conexión para el reloj"
                + (listener && !server ? "  (desactivá y volvé a activar el acceso)" : "")
                + (simulating ? "\n▶ Simulación en curso…" : ""));

        String l = DebugLog.read(this);
        log.setText(l.isEmpty() ? "(todavía nada — iniciá una navegación en Google Maps)" : l);
    }

    private boolean listenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    /** Simula una navegación con dos giros para probar la app del reloj. */
    private void simulate() {
        if (simulating) return;
        simulating = true;
        handler.removeCallbacksAndMessages(null);
        NavServer.start();
        NavFilter.reset();

        final String[][] steps = {
                {"600 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"550 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"420 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"300 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"180 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"90 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"40 m", "Girá a la izquierda hacia Av. Corrientes"},
                {"1,2 km", "Girá a la derecha hacia Av. Callao"},
                {"700 m", "Girá a la derecha hacia Av. Callao"},
                {"450 m", "Girá a la derecha hacia Av. Callao"},
                {"120 m", "En la rotonda, tomá la 2.ª salida"},
                {"30 m", "Llegaste a destino"},
        };
        for (int i = 0; i < steps.length; i++) {
            final String[] s = steps[i];
            handler.postDelayed(() -> {
                NavFilter.onMapsUpdate(s[0], s[1], "Simulación");
                refresh();
            }, i * 5000L);
        }
        handler.postDelayed(() -> {
            NavFilter.onNavEnded();
            simulating = false;
            refresh();
        }, steps.length * 5000L + 3000L);
        refresh();
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
