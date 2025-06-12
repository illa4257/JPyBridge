package illa4257.jpybridge;

import java.io.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JPyBridge implements Closeable {
    /// Operations
    private static final byte RETURN = 0, THROW = 1, CONTAINS = 2, GET = 3, DICT_GET = 4, SET = 5, DICT_SET = 6, CALL = 7,
            EXEC = 8, RELEASE = 9;

    /// Types
    private static final byte NONE = 0, BOOL = 1,
            BYTE = 2, SHORT = 3, INT = 4, LONG = 5,
            FLOAT = 6, DOUBLE = 7, COMPLEX = 8,
            STRING = 9, LAMBDA = 10, JAVA_OBJECT = 11, PYTHON_OBJECT = 12, PYTHON_LIST = PYTHON_OBJECT + 1;

    private static final ThreadLocalRandom RND = ThreadLocalRandom.current();

    public static final Logger L = Logger.getLogger("JPyBridge");

    private static final ReferenceQueue<PyObject> monitor = new ReferenceQueue<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final ConcurrentLinkedQueue<PyObjectRef> refs = new ConcurrentLinkedQueue<>();

    private static final class PyObjectRef extends WeakReference<PyObject> {
        public final JPyBridge bridge;
        public final long id;

        public PyObjectRef(final PyObject object) {
            super(object, monitor);
            bridge = object.getBridge();
            id = object.getId();
            refs.add(this);
        }
    }

    public static void monitor(final PyObject object) { new PyObjectRef(object); }
    
    private static byte readByte(final InputStream is) throws IOException {
        final int r = is.read();
        if (r == -1)
            throw new EOFException("End of stream reached while trying to read a byte.");
        return (byte) r;
    }

    private static int readByteI(final InputStream is) throws IOException {
        final int r = is.read();
        if (r == -1)
            throw new EOFException("End of stream reached while trying to read a byte.");
        return r;
    }

    private static int readBEInteger(final InputStream stream) throws IOException {
        return (readByteI(stream) << 24) + (readByteI(stream) << 16) + (readByteI(stream) << 8) + readByteI(stream);
    }

    private static long readBELong(final InputStream stream) throws IOException {
        return ((long) readByteI(stream) << 56) +
                ((long) readByteI(stream) << 48) +
                ((long) readByteI(stream) << 40) +
                ((long) readByteI(stream) << 32) +
                ((long) readByteI(stream) << 24) +
                ((long) readByteI(stream) << 16) +
                ((long) readByteI(stream) <<  8) +
                (long) readByteI(stream);
    }

    private static byte[] readByteArray(final InputStream stream, final int length) throws IOException {
        final byte[] array = new byte[length];
        for (int i = 0; i < array.length; i++)
            array[i] = readByte(stream);
        return array;
    }

    private static void writeBEShort(final OutputStream stream, int number) throws IOException {
        stream.write(number >> 8);
        stream.write(number);
    }

    private static void writeBEInteger(final OutputStream stream, int number) throws IOException {
        stream.write(number >> 24);
        stream.write(number >> 16);
        stream.write(number >> 8);
        stream.write(number);
    }

    private static void writeBELong(final OutputStream stream, long number) throws IOException {
        stream.write((int) (number >> 56));
        stream.write((int) (number >> 48));
        stream.write((int) (number >> 40));
        stream.write((int) (number >> 32));
        stream.write((int) (number >> 24));
        stream.write((int) (number >> 16));
        stream.write((int) (number >> 8));
        stream.write((int) number);
    }

    static {
        new Thread() {
            {
                setName("JPyBridge GC");
                setDaemon(true);
            }

            @Override
            public void run() {
                try {
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        final PyObjectRef ref = (PyObjectRef) monitor.remove();
                        refs.remove(ref);
                        if (ref.bridge == null || ref.id == -1)
                            continue;
                        ref.bridge.releasePyObject(ref.id);
                    }
                } catch (final Throwable ex) {
                    L.log(Level.SEVERE, null, ex);
                }
            }
        }.start();
    }

    public final Charset charset;
    private final InputStream is;
    private final OutputStream os;
    public final Thread thread;
    private final Runnable shutdownHook;

    private final ConcurrentHashMap<Long, Object> locker = new ConcurrentHashMap<>(),
            mappedObjects = new ConcurrentHashMap<>();

    private final ThreadLocal<Boolean> isFirst = new ThreadLocal<>();

    public JPyBridge(final InputStream inputStream, final OutputStream outputStream, final Charset charset, final Runnable shutdownHook) {
        is = inputStream;
        os = outputStream;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        this.shutdownHook = shutdownHook;

        thread = new Thread(() -> {
            try {
                synchronized (is) {
                    while (true) {
                        final Object l = locker.get(readBELong(is));
                        synchronized (l) { l.notifyAll(); }
                        is.wait();
                    }
                }
            } catch (final Exception ex) {
                if (ex instanceof EOFException)
                    return;
                L.log(Level.SEVERE, null, ex);
            }
        });
        thread.start();
    }

    private Object readObject() throws IOException {
        final byte t = readByte(is);
        switch (t) {
            case NONE: return null;
            case BOOL: return readByte(is) == 1;
            case LONG: return readBELong(is);
            case STRING: return new String(readByteArray(is, readBEInteger(is)), charset);
            case JAVA_OBJECT: return mappedObjects.get(readBELong(is));
            case PYTHON_OBJECT: return new PyObjectImpl(this, readBELong(is));
            case PYTHON_LIST: return new PyList(this, readBELong(is));
            default:
                L.log(Level.WARNING, "Unknown type: " + t);
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
            writeBEShort(os, (short) object);
            return;
        }
        if (object instanceof Integer) {
            os.write(INT);
            writeBEInteger(os, (int) object);
            return;
        }
        if (object instanceof Long) {
            os.write(INT);
            writeBELong(os, (long) object);
            return;
        }
        if (object instanceof String) {
            os.write(STRING);
            final byte[] b = ((String) object).getBytes(charset);
            writeBEInteger(os, b.length);
            os.write(b);
            return;
        }
        if (object instanceof PyObject) {
            os.write(PYTHON_OBJECT);
            writeBELong(os, ((PyObject) object).getId());
            return;
        }
        while (true) {
            final long id = RND.nextLong();
            final Object o = mappedObjects.computeIfAbsent(id, k -> object);
            if (o != object)
                continue;
            os.write(JAVA_OBJECT);
            writeBELong(os, id);
            break;
        }
    }

    private void perform(final byte code) throws IOException {
        if (code == THROW)
            throw PyError.of(readObject());
        if (code == CALL) {
            final Object o = readObject();
            final String n = new String(readByteArray(is, readBEInteger(is)), charset);
            int argsLen = readBEInteger(is);
            final ArrayList<Object> args = new ArrayList<>();
            for (; argsLen > 0; argsLen--)
                args.add(readObject());
            synchronized (is) { is.notifyAll(); }
            try {
                Method m = null;
                for (final Method method : Objects.requireNonNull(o).getClass().getMethods())
                    if (
                            Modifier.isPublic(method.getModifiers()) &&
                                    method.getName().equals(n) &&
                                    method.getParameterCount() == args.size()
                    )
                        m = method;
                Objects.requireNonNull(m).setAccessible(true);
                final Object r = m.invoke(o);
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(RETURN);
                    writeObject(r);
                    os.flush();
                }
            } catch (final Throwable ex) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(THROW);
                    writeObject(ex);
                    os.flush();
                }
            }
            return;
        }
        throw new IOException("Unknown code: " + code);
    }

    private Object getLocker() {
        isFirst.set(false);
        return locker.computeIfAbsent(Thread.currentThread().getId(), ignored -> {
            isFirst.set(true);
            return new Object();
        });
    }

    public Object run(final String code) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(EXEC);
                    final byte[] buff = (
                            "def __rt():\n\t" + String.join("\n\t", code.split("\n")) + "\nresult = __rt()"
                    ).getBytes(charset);
                    writeBEInteger(os, buff.length);
                    os.write(buff);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public Object callArr(final Object object, final String name, final Object[] args) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(CALL);
                    writeObject(object);
                    final byte[] n = name.getBytes(charset);
                    writeBEInteger(os, n.length);
                    os.write(n);
                    if (args != null) {
                        writeBEInteger(os, args.length);
                        for (final Object a : args)
                            writeObject(a);
                    } else
                        writeBEInteger(os, 0);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public Object call(final Object object, final String name, Object... args) {
        return callArr(object, name, args);
    }

    public Object get(final Object object, final String name) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(GET);
                    writeObject(object);
                    final byte[] n = name.getBytes(charset);
                    writeBEInteger(os, n.length);
                    os.write(n);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public Object dictGet(final Object array, final Object name) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(DICT_GET);
                    writeObject(array);
                    writeObject(name);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public Object set(final Object object, final String name, final Object value) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(SET);
                    writeObject(object);
                    final byte[] n = name.getBytes(charset);
                    writeBEInteger(os, n.length);
                    os.write(n);
                    writeObject(value);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public Object dictSet(final Object array, final Object name, final Object value) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(DICT_SET);
                    writeObject(array);
                    writeObject(name);
                    writeObject(value);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            return readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    public boolean contains(final Object array, final Object value) {
        try {
            final Object l = getLocker();
            final boolean first = isFirst.get();
            synchronized (l) {
                synchronized (os) {
                    writeBELong(os, Thread.currentThread().getId());
                    os.write(CONTAINS);
                    writeObject(array);
                    writeObject(value);
                    os.flush();
                }
                try {
                    while (true) {
                        l.wait();
                        final byte c = readByte(is);
                        if (c == RETURN)
                            //noinspection DataFlowIssue
                            return (boolean) readObject();
                        perform(c);
                    }
                } finally {
                    synchronized (is) {
                        is.notifyAll();
                    }
                    if (first)
                        locker.remove(Thread.currentThread().getId());
                }
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    /// Don't use it
    public void releasePyObject(final long id) {
        try {
            synchronized (os) {
                writeBELong(os, Thread.currentThread().getId());
                os.write(RELEASE);
                writeBELong(os, id);
                os.flush();
            }
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() {
        if (shutdownHook != null)
            shutdownHook.run();
    }
}