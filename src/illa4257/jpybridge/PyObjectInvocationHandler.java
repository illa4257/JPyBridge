package illa4257.jpybridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class PyObjectInvocationHandler implements InvocationHandler {
    public final PyObject object;

    public PyObjectInvocationHandler(final PyObject object) { this.object = object; }

    @Override
    public Object invoke(final Object o, final Method method, final Object... objects) throws Throwable {
        try {
            return PyObject.class.getMethod(method.getName(), method.getParameterTypes()).invoke(object, objects);
        } catch (NoSuchMethodException ignored) {}
        return object.call(method.getName(), objects);
    }
}