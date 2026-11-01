package com.navireloj;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Escucha las notificaciones del teléfono, le pasa al filtro solo las de
 * navegación de Google Maps, y mantiene encendido el servidor local que
 * consulta el reloj.
 */
public class MapsListener extends NotificationListenerService {

    static final String MAPS = "com.google.android.apps.maps";

    @Override
    public void onListenerConnected() {
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

        NavServer.start(); // por si el sistema lo cerró
        if (NavFilter.onMapsUpdate(title, text, sub)) {
            DebugLog.add(this, title + " | " + text + " | " + sub);
        }
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
