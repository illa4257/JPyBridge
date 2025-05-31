package illa4257.jpybridge;

import java.io.IOException;

public class PyObject {
    protected final JPyBridge bridge;
    protected final long id;

    public PyObject(final JPyBridge bridge, final long id) {
        this.bridge = bridge;
        this.id = id;
    }

    public Object callArr(final String name, final Object[] args) throws IOException, InterruptedException {
        return bridge.callArr(this, name, args);
    }

    public Object call(final String name, final Object... args) throws IOException, InterruptedException {
        return bridge.call(this, name, args);
    }

    public Object getVal(final String name) throws IOException, InterruptedException {
        return bridge.get(this, name);
    }

    public Object dictGetVal(final Object name) throws IOException, InterruptedException {
        return bridge.dictGet(this, name);
    }

    public Object setVal(final String name, final Object value) throws IOException, InterruptedException {
        return bridge.set(this, name, value);
    }

    public Object dictSetVal(final Object name, final Object value) throws IOException, InterruptedException {
        return bridge.dictSet(this, name, value);
    }

    @Override
    public String toString() {
        try {
            return (String) call("__str__");
        } catch (final IOException|InterruptedException ex) {
            return super.toString();
        }
    }
}