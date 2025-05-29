package illa4257.jpybridge;

import illa4257.i4Utils.Str;
import illa4257.i4Utils.io.IO;
import illa4257.i4Utils.logger.Level;
import illa4257.i4Utils.logger.i4Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static illa4257.i4Utils.logger.Level.WARN;

public class JPyBridge {
    /// Operations
    private static final byte RETURN = 0, THROW = 1, GET = 2, SET = 3, CALL = 4, EXEC = 5;

    /// Types
    private static final byte NONE = 0, BOOL = 1,
            BYTE = 2, SHORT = 3, INT = 4, LONG = 5,
            FLOAT = 6, DOUBLE = 7,
            STRING = 8, OBJECT = 9;

    private final InputStream is;
    private final OutputStream os;
    public final Thread thread;

    private final ConcurrentHashMap<Long, Object> locker = new ConcurrentHashMap<>();

    public JPyBridge(final InputStream inputStream, final OutputStream outputStream) {
        is = inputStream;
        os = outputStream;

        thread = new Thread(() -> {
            try {
                synchronized (inputStream) {
                    while (true) {
                        final Object l = locker.get(IO.readBELong(inputStream));
                        synchronized (l) { l.notifyAll(); }
                        inputStream.wait();
                    }
                }
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        thread.start();
    }

    private Object readObject() throws IOException {
        final byte t = IO.readByte(is);
        switch (t) {
            case NONE: return null;
            case BOOL: return IO.readByte(is) == 1;
            case LONG: return IO.readBELong(is);
            case STRING: return new String(IO.readByteArray(is, IO.readBEInteger(is)), StandardCharsets.UTF_8);
            default:
                i4Logger.INSTANCE.log(WARN, "Unknown type: " + t);
                break;
        }
        return null;
    }

    public Object run(final String code) throws IOException, InterruptedException {
        Object l = locker.get(Thread.currentThread().getId());
        final boolean first;
        if (l == null) {
            first = true;
            locker.put(Thread.currentThread().getId(), l = new Object());
        } else
            first = false;
        synchronized (l) {
            synchronized (os) {
                IO.writeBELong(os, Thread.currentThread().getId());
                os.write(EXEC);
                final byte[] buff = (
                        "def __rt():\n\t" + String.join("\n\t", code.split("\n")) + "\nresult = __rt()"
                    ).getBytes(StandardCharsets.UTF_8);
                IO.writeBEInteger(os, buff.length);
                os.write(buff);
                os.flush();
            }
            try {
                while (true) {
                    l.wait();
                    final byte c = IO.readByte(is);
                    if (c == RETURN)
                        return readObject();
                    if (c == THROW)
                        throw new RuntimeException(String.valueOf(readObject()));
                    throw new IOException("Unknown code: " + c);
                }
            } finally {
                synchronized (is) { is.notifyAll(); }
                if (first)
                    locker.remove(Thread.currentThread().getId());
            }
        }
        //return null;
    }
}