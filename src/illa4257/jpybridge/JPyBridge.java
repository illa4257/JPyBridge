package illa4257.jpybridge;

import illa4257.i4Utils.io.IO;
import illa4257.i4Utils.logger.i4Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static illa4257.i4Utils.logger.Level.WARN;

public class JPyBridge {
    /// Operations
    private static final byte RETURN = 0, THROW = 1, GET = 2, DICT_GET = 3, SET = 4, DICT_SET = 5, CALL = 6,
            EXEC = 7, RELEASE = 8;

    /// Types
    private static final byte NONE = 0, BOOL = 1,
            BYTE = 2, SHORT = 3, INT = 4, LONG = 5,
            FLOAT = 6, DOUBLE = 7, COMPLEX = 8,
            STRING = 9, JAVA_OBJECT = 10, PYTHON_OBJECT = 11;

    private final InputStream is;
    private final OutputStream os;
    public final Thread thread;

    private final ConcurrentHashMap<Long, Object> locker = new ConcurrentHashMap<>(),
            mappedObjects = new ConcurrentHashMap<>();

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
            case JAVA_OBJECT: return mappedObjects.get(IO.readBELong(is));
            case PYTHON_OBJECT: return new PyObject(this, IO.readBELong(is));
            default:
                i4Logger.INSTANCE.log(WARN, "Unknown type: " + t);
                break;
        }
        return null;
    }

    private void writeObject(final Object object) throws IOException {
        if (object == null) {
            os.write(NONE);
            return;
        }
        if (object instanceof Boolean) {
            os.write(BOOL);
            os.write((boolean) object ? 1 : 0);
            return;
        }
        if (object instanceof Byte) {
            os.write(BYTE);
            os.write((byte) object);
            return;
        }
        if (object instanceof Short) {
            os.write(SHORT);
            IO.writeBEShort(os, (short) object);
            return;
        }
        if (object instanceof Integer) {
            os.write(INT);
            IO.writeBEInteger(os, (int) object);
            return;
        }
        if (object instanceof Long) {
            os.write(INT);
            IO.writeBELong(os, (long) object);
            return;
        }
        if (object instanceof String) {
            os.write(STRING);
            final byte[] b = ((String) object).getBytes(StandardCharsets.UTF_8);
            IO.writeBEInteger(os, b.length);
            os.write(b);
            return;
        }
        if (object instanceof PyObject) {
            os.write(PYTHON_OBJECT);
            IO.writeBELong(os, ((PyObject) object).id);
        }
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
    }

    public Object callArr(final Object object, final String name, final Object[] args) throws IOException, InterruptedException {
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
                os.write(CALL);
                writeObject(object);
                final byte[] n = name.getBytes(StandardCharsets.UTF_8);
                IO.writeBEInteger(os, n.length);
                os.write(n);
                if (args != null) {
                    IO.writeBEInteger(os, args.length);
                    for (final Object a : args)
                        writeObject(a);
                } else
                    IO.writeBEInteger(os, 0);
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
    }

    public Object call(final Object object, final String name, Object... args) throws IOException, InterruptedException {
        return callArr(object, name, args);
    }

    public Object get(final Object object, final String name) throws IOException, InterruptedException {
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
                os.write(GET);
                writeObject(object);
                final byte[] n = name.getBytes(StandardCharsets.UTF_8);
                IO.writeBEInteger(os, n.length);
                os.write(n);
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
    }

    public Object dictGet(final Object array, final Object name) throws IOException, InterruptedException {
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
                os.write(DICT_GET);
                writeObject(array);
                writeObject(name);
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
    }

    public Object set(final Object object, final String name, final Object value) throws IOException, InterruptedException {
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
                os.write(SET);
                writeObject(object);
                final byte[] n = name.getBytes(StandardCharsets.UTF_8);
                IO.writeBEInteger(os, n.length);
                os.write(n);
                writeObject(value);
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
    }

    public Object dictSet(final Object array, final Object name, final Object value) throws IOException, InterruptedException {
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
                os.write(DICT_SET);
                writeObject(array);
                writeObject(name);
                writeObject(value);
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
    }
}