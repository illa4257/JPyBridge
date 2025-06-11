package illa4257.jpybridge;

public class PyException extends PyError {
    public PyException() { super(); }
    public PyException(final String msg) { super(msg); }
    public PyException(final Throwable cause) { super(cause); }
    public PyException(final PyObject object) { super(object); }
}