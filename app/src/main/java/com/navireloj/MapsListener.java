package com.navireloj;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Escucha las notificaciones del teléfono y le pasa al filtro
 * solo las de navegación de Google Maps.
 */
public class MapsListener extends NotificationListenerService {

    static final String MAPS = "com.google.android.apps.maps";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || !isNavigation(n)) return;

        Bundle e = n.extras;
        if (e == null) return;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));

        NavFilter.onMapsUpdate(this, title, text);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n != null && isNavigation(n)) {
            NavFilter.onNavEnded(this);
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
