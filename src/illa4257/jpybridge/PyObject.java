package illa4257.jpybridge;

import java.lang.reflect.Proxy;

public interface PyObject {
    JPyBridge getBridge();
    long getId();

    default String getTypeStr() {
        return (String) getBridge().get(
                getBridge().call(null, "type", this),
                "__name__"
        );
    }

    default Object callArr(final String name, final Object[] args) {
        return getBridge().callArr(this, name, args);
    }

    default Object call(final String name, final Object... args) {
        return getBridge().call(this, name, args);
    }

    default Object getVal(final String name) {
        return getBridge().get(this, name);
    }

    default Object dictGetVal(final Object name) {
        return getBridge().dictGet(this, name);
    }

    default Object setVal(final String name, final Object value) {
        return getBridge().set(this, name, value);
    }

    default Object dictSetVal(final Object name, final Object value) {
        return getBridge().dictSet(this, name, value);
    }

    default boolean contains(final Object o) {
        return getBridge().contains(this, o);
    }

    @SuppressWarnings("unchecked")
    default <T> T proxy(final Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class[] { interfaceType },
                new PyObjectInvocationHandler(this)
        );
    }

    default void release() {}
}