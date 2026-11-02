package com.navireloj;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Pantalla principal: estado, permisos, opciones, prueba y diagnóstico. */
public class MainActivity extends Activity {

    // ---------- colores ----------

    private static final class Pal {
        int bg, card, divider, text, text2, header, headerText, headerChip,
                primary, onPrimary, segBg, segSel, ok, okBg, warn, warnBg, link, swOff, trackOff;
    }

    private static Pal light() {
        Pal p = new Pal();
        p.bg = 0xFFF4F6F8; p.card = 0xFFFFFFFF; p.divider = 0xFFEDF0F3;
        p.text = 0xFF1C1F23; p.text2 = 0xFF5B6470;
        p.header = 0xFF1A73E8; p.headerText = 0xFFFFFFFF; p.headerChip = 0x29FFFFFF;
        p.primary = 0xFF1A73E8; p.onPrimary = 0xFFFFFFFF;
        p.segBg = 0xFFE4E8EC; p.segSel = 0xFFFFFFFF;
        p.ok = 0xFF188038; p.okBg = 0xFFE6F4EA; p.warn = 0xFFB06000; p.warnBg = 0xFFFEF7E0;
        p.link = 0xFF1A73E8; p.swOff = 0xFFBDC1C6; p.trackOff = 0xFFDADCE0;
        return p;
    }

    private static Pal dark() {
        Pal p = new Pal();
        p.bg = 0xFF121417; p.card = 0xFF1E2125; p.divider = 0xFF2A2E33;
        p.text = 0xFFE8EAED; p.text2 = 0xFF9AA0A6;
        p.header = 0xFF1E3A5F; p.headerText = 0xFFFFFFFF; p.headerChip = 0x29FFFFFF;
        p.primary = 0xFF8AB4F8; p.onPrimary = 0xFF0B1A33;
        p.segBg = 0xFF2A2E33; p.segSel = 0xFF3C4148;
        p.ok = 0xFF81C995; p.okBg = 0xFF1E3A2A; p.warn = 0xFFFDD663; p.warnBg = 0xFF3D3320;
        p.link = 0xFF8AB4F8; p.swOff = 0xFF9AA0A6; p.trackOff = 0xFF5F6368;
        return p;
    }

    private static final String PREFS = "ajustes";

    private Pal c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean simulating = false;
    private boolean logOpen = false;

    // vistas que se actualizan
    private TextView headerIcon, headerTitle, headerSub;
    private TextView notifIcon, notifStatus, battIcon, battStatus;
    private TextView[] modeSeg;
    private TextView themeBtn;
    private TextView modeDesc, testBtn, logToggle, logText;
    private LinearLayout logBox;

    // ================= ciclo de vida =================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.install(this);
        c = isDark() ? dark() : light();
        setupWindow();
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ================= tema =================

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String themePref() {
        return prefs().getString("theme", "system");
    }

    private boolean isDark() {
        String t = themePref();
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setTheme(String t) {
        if (t.equals(themePref())) return;
        prefs().edit().putString("theme", t).apply();
        recreate();
    }

    private String themeSymbol() {
        String t = themePref();
        return "light".equals(t) ? "☀" : "dark".equals(t) ? "☾" : "◐";
    }

    private String themeName() {
        return themeName(themePref());
    }

    private String themeName(String t) {
        return "light".equals(t) ? "claro" : "dark".equals(t) ? "oscuro" : "automático";
    }

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        getWindow().setStatusBarColor(c.header);
        getWindow().setNavigationBarColor(c.bg);
        int flags = 0;
        if (!isDark()) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ================= interfaz =================

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c.bg);

        root.addView(buildHeader());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(20), dp(18), dp(28));
        root.addView(body);

        // --- Permisos ---
        body.addView(sectionLabel("Permisos"));
        LinearLayout perms = card();
        LinearLayout notifRow = statusRow("Acceso a notificaciones", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        notifIcon = (TextView) notifRow.getChildAt(0);
        notifStatus = (TextView) notifRow.getChildAt(2);
        perms.addView(notifRow);
        perms.addView(divider());
        LinearLayout battRow = statusRow("Restricción de batería", v ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        battIcon = (TextView) battRow.getChildAt(0);
        battStatus = (TextView) battRow.getChildAt(2);
        perms.addView(battRow);
        body.addView(perms);
        body.addView(gap(18));

        // --- Pantalla del reloj ---
        body.addView(sectionLabel("Pantalla del reloj"));
        modeSeg = new TextView[2];
        body.addView(segmented(new String[]{"Siempre activa", "Normal"}, modeSeg, i ->
                saveScreenMode(i == 0 ? "always" : "auto")));
        modeDesc = text("", 13, c.text2, false);
        modeDesc.setLineSpacing(0, 1.25f);
        modeDesc.setPadding(dp(4), dp(8), dp(4), 0);
        body.addView(modeDesc);
        body.addView(gap(18));

        // --- Vibración ---
        LinearLayout vibCard = card();
        Switch vib = new Switch(this);
        vib.setText("Vibrar en los avisos");
        vib.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        vib.setTypeface(Typeface.DEFAULT_BOLD);
        vib.setTextColor(c.text);
        vib.setMinHeight(dp(58));
        vib.setPadding(dp(16), 0, dp(16), 0);
        vib.setChecked(Notifier.isVibrateEnabled(this));
        tintSwitch(vib);
        vib.setOnCheckedChangeListener((b, on) -> Notifier.save(this, false, on));
        vibCard.addView(vib);
        body.addView(vibCard);
        body.addView(gap(18));

        body.addView(gap(8));

        // --- Probar ---
        testBtn = text("Probar con el reloj", 16, c.onPrimary, true);
        testBtn.setGravity(Gravity.CENTER);
        testBtn.setBackground(rounded(c.primary, 16));
        testBtn.setMinHeight(dp(54));
        testBtn.setClickable(true);
        testBtn.setOnClickListener(v -> simulate());
        body.addView(testBtn, matchWrap());

        // --- Diagnóstico ---
        logToggle = text("Ver registro de diagnóstico", 14, c.link, true);
        logToggle.setGravity(Gravity.CENTER);
        logToggle.setMinHeight(dp(48));
        logToggle.setClickable(true);
        logToggle.setOnClickListener(v -> {
            logOpen = !logOpen;
            refresh();
        });
        body.addView(logToggle, matchWrap());

        logBox = card();
        logBox.setPadding(dp(14), dp(12), dp(14), dp(8));
        logText = text("", 11, c.text2, false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logBox.addView(logText);
        LinearLayout logActions = new LinearLayout(this);
        logActions.setGravity(Gravity.END);
        logActions.addView(smallAction("Copiar", v -> copyLog()));
        logActions.addView(smallAction("Actualizar", v -> refresh()));
        logActions.addView(smallAction("Borrar", v -> {
            DebugLog.clear(this);
            refresh();
        }));
        logBox.addView(logActions);
        body.addView(logBox);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(c.bg);
        scroll.setFillViewport(true);
        scroll.addView(root);
        return scroll;
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(22), dp(26), dp(22), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(c.header);
        float r = dp(28);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        h.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("NaviReloj", 26, c.headerText, true);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView version = text(versionName(), 12, 0xCCFFFFFF, false);
        version.setPadding(0, 0, dp(10), 0);
        top.addView(version);

        // Tema: un toque va rotando automático → claro → oscuro
        themeBtn = text(themeSymbol(), 18, c.headerText, false);
        themeBtn.setGravity(Gravity.CENTER);
        themeBtn.setBackground(oval(c.headerChip));
        themeBtn.setClickable(true);
        themeBtn.setContentDescription("Tema: " + themeName());
        themeBtn.setOnClickListener(v -> {
            String t = themePref();
            String next = "system".equals(t) ? "light" : "light".equals(t) ? "dark" : "system";
            Toast.makeText(this, "Tema: " + themeName(next), Toast.LENGTH_SHORT).show();
            setTheme(next);
        });
        top.addView(themeBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        h.addView(top);

        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(rounded(c.headerChip, 14));
        chip.setPadding(dp(14), dp(12), dp(14), dp(12));
        headerIcon = text("", 16, c.header, true);
        headerIcon.setGravity(Gravity.CENTER);
        headerIcon.setBackground(oval(0xFFFFFFFF));
        chip.addView(headerIcon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        headerTitle = text("", 15, c.headerText, true);
        headerSub = text("", 13, 0xE6FFFFFF, false);
        texts.addView(headerTitle);
        texts.addView(headerSub);
        chip.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(14);
        h.addView(chip, lp);
        return h;
    }

    // ================= estado =================

    private void refresh() {
        if (headerTitle == null) return;
        boolean listener = listenerEnabled();
        boolean server = NavServer.isRunning();
        boolean battery = batteryUnrestricted();

        String title, sub;
        boolean good;
        if (!listener) {
            title = "Falta un permiso"; sub = "Activá el acceso a notificaciones"; good = false;
        } else if (!server) {
            title = "Reconectando…"; sub = "Desactivá y volvé a activar el acceso a notificaciones"; good = false;
        } else if (simulating) {
            title = "Simulación en curso"; sub = "Mirá el reloj"; good = true;
        } else {
            title = "Todo listo"; sub = "Abrí NaviReloj en el reloj y arrancá Maps"; good = true;
        }
        headerTitle.setText(title);
        headerSub.setText(sub);
        headerIcon.setText(good ? "✓" : "!");

        setStatus(notifIcon, notifStatus, listener, "Activo", "Activar");
        setStatus(battIcon, battStatus, battery, "Sin restricción", "Revisar");

        boolean always = !"auto".equals(Notifier.getScreenMode(this));
        selectSeg(modeSeg, always ? 0 : 1);
        modeDesc.setText(always
                ? "No se apaga al bajar la muñeca. A los 10 s baja el brillo y en cada aviso vuelve y vibra. Tapándola con la mano se apaga."
                : "Se apaga sola a los pocos segundos o al bajar la muñeca. Mirás el reloj cuando querés (no avisa de los giros).");


        testBtn.setText(simulating ? "Simulando… (1 min)" : "Probar con el reloj");
        testBtn.setAlpha(simulating ? 0.6f : 1f);

        logToggle.setText(logOpen ? "Ocultar registro de diagnóstico" : "Ver registro de diagnóstico");
        logBox.setVisibility(logOpen ? View.VISIBLE : View.GONE);
        if (logOpen) {
            String l = DebugLog.read(this);
            logText.setText(l.isEmpty() ? "(todavía nada — iniciá una navegación en Google Maps)" : l);
        }
    }

    private void setStatus(TextView icon, TextView status, boolean ok, String yes, String no) {
        icon.setText(ok ? "✓" : "!");
        icon.setTextColor(ok ? c.ok : c.warn);
        icon.setBackground(oval(ok ? c.okBg : c.warnBg));
        status.setText(ok ? yes : no);
        status.setTextColor(ok ? c.ok : c.warn);
    }

    private void saveScreenMode(String mode) {
        Notifier.saveScreenMode(this, mode);
        refresh();
    }

    private boolean listenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private boolean batteryUnrestricted() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private String versionName() {
        try {
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void copyLog() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("NaviReloj", DebugLog.read(this)));
        Toast.makeText(this, "Registro copiado", Toast.LENGTH_SHORT).show();
    }

    /** Simula una navegación con varios giros para probar la app del reloj. */
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

    // ================= piezas =================

    private interface IntAction {
        void run(int i);
    }

    private LinearLayout segmented(String[] labels, TextView[] out, IntAction onPick) {
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(rounded(c.segBg, 16));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView b = text(labels[i], 14, c.text2, false);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(dp(46));
            b.setClickable(true);
            b.setOnClickListener(v -> onPick.run(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.leftMargin = dp(4);
            seg.addView(b, lp);
            out[i] = b;
        }
        return seg;
    }

    private void selectSeg(TextView[] seg, int selected) {
        for (int i = 0; i < seg.length; i++) {
            boolean on = i == selected;
            seg[i].setBackground(on ? rounded(c.segSel, 12) : null);
            seg[i].setTextColor(on ? c.text : c.text2);
            seg[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            seg[i].setElevation(on && !isDark() ? dp(1) : 0);
        }
    }

    private LinearLayout statusRow(String label, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setClickable(true);
        row.setOnClickListener(onClick);

        TextView icon = text("", 14, c.ok, true);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView name = text(label, 15, c.text, true);
        name.setPadding(dp(12), 0, dp(8), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = text("", 13, c.ok, true);
        row.addView(status);
        return row;
    }

    private TextView smallAction(String label, View.OnClickListener l) {
        TextView t = text(label, 13, c.link, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 13, c.text2, true);
        t.setPadding(dp(4), 0, 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(c.card, 18));
        l.setClipToOutline(true);
        return l;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(c.divider);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        return v;
    }

    private View gap(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void tintSwitch(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{c.primary, c.swOff}));
        int trackOn = (c.primary & 0x00FFFFFF) | 0x66000000;
        s.setTrackTintList(new ColorStateList(states, new int[]{trackOn, c.trackOff}));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
