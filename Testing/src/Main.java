import illa4257.i4Utils.logger.AnsiColoredPrintStreamLogHandler;
import illa4257.i4Utils.logger.i4Logger;
import illa4257.jpybridge.JPyBridge;
import illa4257.jpybridge.PyError;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static illa4257.i4Utils.logger.Level.*;

public class Main {
    public static final i4Logger L = new i4Logger("JPyBridge");

    public static void main(final String[] args) throws Exception {
        L.registerHandler(new AnsiColoredPrintStreamLogHandler(System.out));
        i4Logger.INSTANCE.unregisterAllHandlers().registerHandler(L);

        System.setOut(L.newPrintStream(INFO));
        System.setErr(L.newPrintStream(ERROR));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> L.log(e));

        final JPyBridge b;
        try (final ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            final ProcessBuilder pb = new ProcessBuilder("python3", "bridge.py", "--con", Integer.toString(server.getLocalPort()))
                    .inheritIO();
            pb.environment().put("PYTHONUNBUFFERED", "true");
            final Process p = pb.start();
            final Socket s = server.accept();
            b = new JPyBridge(s.getInputStream(), s.getOutputStream(), StandardCharsets.UTF_8, p::destroy);
        }

        final Object o = new Object() {
            @Override
            public String toString() {
                return "TEST=" + b.run("return 1 + 1");
            }
        };

        try (final JPyBridge bridge = b) {
            try {
                System.out.println(b.call(o, "__str__"));
            } catch (final Exception ex) {
                if (ex instanceof PyError)
                    System.out.println(((PyError) ex).formatException());
                L.log(ex);
            }
        }
    }
}