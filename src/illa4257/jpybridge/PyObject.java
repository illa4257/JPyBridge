package illa4257.jpybridge;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;

public interface PyObject {
    JPyBridge getBridge();
    long getId();

    default String getTypeStr() {
        return (String) getBridge().get(
                getBridge().call(null, "type", null, 0, this),
                "__name__"
        );
    }

    default Object callArr(final String name, final Map<String, Object> kwargs, final int offset, final Object[] args) {
        return getBridge().callArr(this, name, kwargs, offset, args);
    }

    default Object call(final String name, final Map<String, Object> kwargs, final int offset, final Object... args) {
        return getBridge().call(this, name, kwargs, offset, args);
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
                new PyObjectInvocationHandler(this, interfaceType)
        );
    }

    @SuppressWarnings("unchecked")
    default <T> T proxy(final Object def, final Class<T> interfaceType) {
        final PyObjectInvocationHandler invocationHandler = new PyObjectInvocationHandler(this, interfaceType);
        invocationHandler.def = def;
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class[] { interfaceType },
                invocationHandler
        );
    }

    @SuppressWarnings("unchecked")
    default <T> T proxy(final Function<T, Object> def, final Class<T> interfaceType) {
        final PyObjectInvocationHandler invocationHandler = new PyObjectInvocationHandler(this, interfaceType);
        final T p = (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class[] { interfaceType },
                invocationHandler
        );
        invocationHandler.def = def.apply(p);
        return p;
    }

    default void release() {}
}