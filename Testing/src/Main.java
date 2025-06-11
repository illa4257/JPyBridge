import illa4257.jpybridge.JPyBridge;
import illa4257.jpybridge.PyError;
import illa4257.jpybridge.PyList;
import illa4257.jpybridge.PyObject;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(final String[] args) throws Exception {
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
                {
                    final PyList l = (PyList) b.run("return []");

                    l.add("test");
                    l.add(123);

                    System.out.println(l);

                    for (final Object e : l)
                        System.out.println(e);

                    Thread.sleep(5000);
                }
                System.out.println("Test 2");
                {
                    final PyList l = (PyList) b.run("return []");

                    l.add("test");
                    l.add(123);

                    Thread.sleep(5000);
                }
                System.out.println("Test 3");
                System.gc();

                Thread.sleep(10000);
            } catch (final Exception ex) {
                if (ex instanceof PyError)
                    System.out.println(((PyError) ex).formatException());
                ex.printStackTrace();
            }
        }
    }
}