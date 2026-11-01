package com.navireloj;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Mini servidor HTTP de solo lectura, accesible únicamente desde el mismo
 * celular (127.0.0.1). La parte de la app del reloj que corre dentro de Zepp
 * lo consulta para obtener la indicación actual.
 *
 *   GET http://127.0.0.1:27380/nav  ->  JSON de NavFilter.toJson()
 */
public final class NavServer {

    private NavServer() {}

    public static final int PORT = 27380;

    private static ServerSocket server;
    private static Thread thread;

    public static synchronized boolean isRunning() {
        return server != null && !server.isClosed();
    }

    public static synchronized void start() {
        if (isRunning()) return;
        try {
            ServerSocket s = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
            s.setReuseAddress(true);
            server = s;
        } catch (Exception e) {
            server = null;
            return;
        }
        final ServerSocket s = server;
        thread = new Thread(() -> loop(s), "NavServer");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        thread = null;
    }

    private static void loop(ServerSocket s) {
        while (!s.isClosed()) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(2000);
                handle(c);
            } catch (Exception ignored) {
                // conexión fallida o servidor cerrado: seguimos o salimos del while
            }
        }
    }

    private static void handle(Socket c) throws Exception {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = in.readLine();
        if (requestLine == null) return;
        // Leemos (y descartamos) los encabezados
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) { /* nada */ }

        String[] parts = requestLine.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";

        int status;
        String body;
        if (path.startsWith("/nav")) {
            status = 200;
            body = NavFilter.toJson();
        } else {
            status = 404;
            body = "{\"error\":\"not found\"}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = c.getOutputStream();
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }
}
