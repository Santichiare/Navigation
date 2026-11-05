package com.navireloj;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;

/**
 * Escucha las notificaciones del teléfono, le pasa al filtro solo las de
 * navegación de Google Maps, y mantiene encendido el servidor local que
 * consulta el reloj.
 */
public class MapsListener extends NotificationListenerService {

    static final String MAPS = "com.google.android.apps.maps";

    @Override
    public void onListenerConnected() {
        Notifier.install(this);
        NavServer.start();
    }

    @Override
    public void onListenerDisconnected() {
        NavServer.stop();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || !isNavigation(n)) return;

        Bundle e = n.extras;
        if (e == null) return;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));

        // Distancia al próximo giro: en Samsung viene en los campos de las
        // "Live notifications" (ongoingActivityNoti.primaryInfo = "200 m").
        // Primero esos; después otros campos donde algunas versiones la ponen.
        String extra = findByKeySuffix(e, "ongoingActivityNoti.primaryInfo")
                + " " + findByKeySuffix(e, "ongoingActivityNoti.chipExpandedText")
                + " " + findByKeySuffix(e, "ongoingActivityNoti.nowbarPrimaryInfo")
                + " " + str(e.getCharSequence(Notification.EXTRA_BIG_TEXT))
                + " " + str(e.getCharSequence(Notification.EXTRA_INFO_TEXT))
                + " " + str(e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
                + " " + str(n.tickerText);

        // Distancia total del recorrido: barra de progreso (metros)
        int progress = e.getInt(Notification.EXTRA_PROGRESS, -1);
        int progressMax = e.getInt(Notification.EXTRA_PROGRESS_MAX, -1);

        NavServer.start(); // por si el sistema lo cerró
        NavFilter.setProgress(progress, progressMax);
        if (NavFilter.onMapsUpdate(title, text, sub, extra)) {
            DebugLog.add(this, dumpAll(n, e));
        }
    }

    /** Valor de texto del primer campo cuya clave termina en el sufijo dado. */
    private static String findByKeySuffix(Bundle e, String suffix) {
        for (String key : e.keySet()) {
            if (!key.endsWith(suffix)) continue;
            try {
                Object v = e.get(key);
                if (v != null) return v.toString();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    /** Todos los campos de texto de la notificación, para diagnóstico. */
    private static String dumpAll(Notification n, Bundle e) {
        StringBuilder b = new StringBuilder();
        for (String key : e.keySet()) {
            Object v;
            try {
                v = e.get(key);
            } catch (Exception ex) {
                continue;
            }
            if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) {
                String s = v instanceof CharSequence ? withColors((CharSequence) v) : v.toString();
                if (s.isEmpty()) continue;
                if (b.length() > 0) b.append(" | ");
                b.append(key.replace("android.", "")).append('=').append(s);
            } else if (v instanceof CharSequence[]) {
                CharSequence[] arr = (CharSequence[]) v;
                if (arr.length == 0) continue;
                if (b.length() > 0) b.append(" | ");
                b.append(key.replace("android.", "")).append("=[");
                for (int i = 0; i < arr.length; i++) b.append(i > 0 ? "; " : "").append(withColors(arr[i]));
                b.append(']');
            }
        }
        if (n.tickerText != null) b.append(" | ticker=").append(n.tickerText);
        if (n.category != null) b.append(" | cat=").append(n.category);
        if (n.color != 0) b.append(" | color=").append(hex(n.color));
        return b.toString();
    }

    /**
     * Texto con los colores que trae marcados (ej. los minutos en rojo si hay
     * tránsito): cada tramo coloreado se muestra como {#RRGGBB:texto}.
     */
    private static String withColors(CharSequence cs) {
        if (cs == null) return "";
        if (!(cs instanceof Spanned)) return cs.toString();
        Spanned sp = (Spanned) cs;
        Object[] spans = sp.getSpans(0, sp.length(), Object.class);
        if (spans.length == 0) return cs.toString();
        StringBuilder out = new StringBuilder(cs.toString());
        StringBuilder marks = new StringBuilder();
        for (Object span : spans) {
            int start = sp.getSpanStart(span), end = sp.getSpanEnd(span);
            if (start < 0 || end <= start) continue;
            String part = cs.subSequence(start, end).toString();
            String what;
            if (span instanceof ForegroundColorSpan) {
                what = hex(((ForegroundColorSpan) span).getForegroundColor());
            } else if (span instanceof BackgroundColorSpan) {
                what = "fondo " + hex(((BackgroundColorSpan) span).getBackgroundColor());
            } else if (span instanceof TextAppearanceSpan
                    && ((TextAppearanceSpan) span).getTextColor() != null) {
                what = "estilo " + hex(((TextAppearanceSpan) span).getTextColor().getDefaultColor());
            } else {
                what = span.getClass().getSimpleName();
            }
            marks.append(" {").append(what).append(':').append(part).append('}');
        }
        return out.append(marks).toString();
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n != null && isNavigation(n)) {
            NavFilter.onNavEnded();
            DebugLog.add(this, "(navegación terminada)");
        }
    }

    /** La notificación de navegación de Maps es fija ("ongoing") o tiene categoría de navegación. */
    static boolean isNavigation(Notification n) {
        return "navigation".equals(n.category)
                || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
