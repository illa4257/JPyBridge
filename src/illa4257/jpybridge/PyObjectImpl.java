package illa4257.jpybridge;

public class PyObjectImpl implements PyObject {
    private final JPyBridge bridge;
    private final long id;

    public PyObjectImpl(final JPyBridge bridge, final long id) {
        this.bridge = bridge;
        this.id = id;
        JPyBridge.monitor(this);
    }

    @Override public boolean contains(Object o) { return PyObject.super.contains(o); }
    @Override public JPyBridge getBridge() { return bridge; }
    @Override public long getId() { return id; }
    @Override public String toString() { return (String) call("__str__"); }
}