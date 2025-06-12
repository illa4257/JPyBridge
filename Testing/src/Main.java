import illa4257.jpybridge.JPyBridge;
import illa4257.jpybridge.PyError;
import illa4257.jpybridge.PyObject;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static final Logger L = Logger.getLogger("JPyBridge");

    public interface ProxyTest {
        void test();
    }

    public static void main(final String[] args) throws Exception {
        L.log(Level.INFO, "Starting ...");
        try (final ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            final ProcessBuilder pb = new ProcessBuilder("python3", "bridge.py", "--con", Integer.toString(server.getLocalPort()))
                    .inheritIO();
            pb.environment().put("PYTHONUNBUFFERED", "true");
            final Process p = pb.start();
            final Socket s = server.accept();
            try (final JPyBridge b = new JPyBridge(s.getInputStream(), s.getOutputStream(), StandardCharsets.UTF_8, p::destroy)) {
                final Object o = new Object() {
                    @Override
                    public String toString() {
                        return "TEST=" + b.run("return 1 + 1");
                    }
                };

                try {
                    L.log(Level.INFO, String.valueOf(b.call(o, "__str__"))); // TEST=2

                    b.run("global TestClass\nclass TestClass:\n\tdef test(self):\n\t\tprint('Hello, world!')"); // Declare a class

                    final ProxyTest test = ((PyObject) b.run("return TestClass()")).proxy(ProxyTest.class); // Make proxy
                    test.test(); // Prints Hello, world!

                    b.run("raise BaseException('Hello, world!')"); // throws BaseException
                } catch (final Exception ex) {
                    if (ex instanceof PyError)
                        L.log(Level.INFO, ((PyError) ex).formatException());
                    L.log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}