package io.quarkiverse.fleettest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * One HTTP/1.1 GET on a fresh connection, with no retry of any kind: what the load balancer does to the connection
 * is what the caller sees.
 * <p>
 * Ordinary clients, RestAssured's and the JDK's included, hide exactly the events a fleet test is about. They keep
 * connections alive, so a sequence of their requests sticks to one instance instead of being balanced, and they
 * retry a request whose connection failed, so a request dropped by a stopping instance is reported as a success.
 * This client opens one connection per request, sends the request once, and reports the status code or the
 * {@link IOException}, whichever the connection produced.
 */
public final class RawHttp {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private RawHttp() {
    }

    /**
     * The status code of a GET for {@code path} at {@code base}, with a timeout of five seconds.
     *
     * @throws IOException if the connection was refused, closed or reset, or if nothing arrived within the timeout
     */
    public static int get(URI base, String path) throws IOException {
        return get(base, path, DEFAULT_TIMEOUT);
    }

    /**
     * The status code of a GET for {@code path} at {@code base}.
     *
     * @throws IOException if the connection was refused, closed or reset, or if nothing arrived within the timeout
     */
    public static int get(URI base, String path, Duration timeout) throws IOException {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            socket.setSoTimeout((int) timeout.toMillis());
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();
            if (statusLine == null) {
                throw new IOException("connection closed before any response");
            }
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }
}
