package illa4257.jpybridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class PyObjectInvocationHandler implements InvocationHandler {
    public final PyObject object;
    public final Class<?> interfaceType;
    public Object def = null;

    public PyObjectInvocationHandler(final PyObject object, final Class<?> interfaceType) { this.object = object; this.interfaceType = interfaceType; }

    @Override
    public Object invoke(final Object o, final Method method, final Object... objects) throws Throwable {
        try {
            if (def != null && interfaceType.getMethod(method.getName(), method.getParameterTypes()).isDefault()) {
                final Method m = def.getClass().getMethod(method.getName(), method.getParameterTypes());
                if (m.isDefault())
                    m.invoke(def, objects);
            }
        } catch (NoSuchMethodException ignored) {}
        try {
            return PyObject.class.getMethod(method.getName(), method.getParameterTypes()).invoke(object, objects);
        } catch (NoSuchMethodException ignored) {}
        if (objects.length > 0 && objects[0] instanceof Kwargs)
            return object.call(method.getName(), (Kwargs) objects[0], 1, objects);
        else
            return object.call(method.getName(), null, 0, objects);
    }
}