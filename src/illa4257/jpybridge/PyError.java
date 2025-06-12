package illa4257.jpybridge;

public class PyError extends RuntimeException implements PyObject {
    private final JPyBridge bridge;
    private final long id;
    public final PyObject original;

    public PyError() { original = null; bridge = null; id = -1; }
    public PyError(final String msg) { super(msg); original = null; bridge = null; id = -1; }
    public PyError(final Throwable cause) { super(cause); original = null; bridge = null; id = -1; }
    public PyError(final JPyBridge bridge, final long id) { original = null; this.bridge = bridge; this.id = id; JPyBridge.monitor(this); }
    public PyError(final PyObject object) { original = object; bridge = object.getBridge(); id = object.getId(); }

    public static PyError of(final Object object) {
        if (object == null)
            return new PyError();
        if (object instanceof Throwable)
            return new PyError((Throwable) object);
        if (object instanceof PyObject)
            return new PyError((PyObject) object);
        return new PyError(String.valueOf(object));
    }

    @Override public JPyBridge getBridge() { return bridge; }
    @Override public long getId() { return id; }

    public String formatException() {
        return bridge != null && id != -1 ?
                (String) bridge.call("", "join", bridge.call(null, "traceback.format_exception", this))
                : null;
    }

    @Override
    public String getMessage() {
        if (bridge != null && id != -1)
            return bridge.get(bridge.call(null, "type", this), "__name__") + ": " +
                    bridge.call(null, "str", this);
        return super.getMessage();
    }
}