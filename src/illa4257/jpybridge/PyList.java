package illa4257.jpybridge;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class PyList extends PyObjectImpl implements List<Object> {
    public PyList(final JPyBridge bridge, final long id) { super(bridge, id); }

    @Override public boolean isEmpty() { return size() == 0; }
    @Override public int size() { return (int) getBridge().call(null, "len", this); }

    @Override
    public Iterator<Object> iterator() {
        final PyObject iter = (PyObject) getBridge().call(null, "iter", this);
        return new Iterator<Object>() {
            private boolean noHas = true, end = false;
            private Object val = null;

            @Override
            public boolean hasNext() {
                if (end)
                    return false;
                if (noHas)
                    try {
                        val = getBridge().call(null, "next", iter);
                        noHas = false;
                    } catch (final Exception ex) {
                        end = true;
                        if (ex instanceof PyError) {
                            if ("StopIteration".equalsIgnoreCase(((PyError) ex).getTypeStr()))
                                return false;
                            throw ex;
                        }
                        throw new RuntimeException(ex);
                    }
                return true;
            }

            @Override
            public Object next() {
                if (end)
                    throw new NoSuchElementException();
                if (noHas)
                    try {
                        return getBridge().call(null, "next", iter);
                    } catch (final Exception ex) {
                        end = true;
                        if (ex instanceof PyError) {
                            if ("StopIteration".equalsIgnoreCase(((PyError) ex).getTypeStr()))
                                throw new NoSuchElementException();
                            throw ex;
                        }
                        throw new RuntimeException(ex);
                    }
                final Object r = val;
                val = null;
                noHas = true;
                return r;
            }
        };
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(final T[] ts) {
        return ts;
    }

    @Override public boolean add(final Object object) { call("append", object); return true; }
    @Override public boolean remove(final Object o) { call("remove", o); return true; }

    @Override
    public boolean addAll(Collection collection) {
        return false;
    }

    @Override
    public boolean addAll(int i, Collection collection) {
        return false;
    }

    @Override public void clear() { call("clear"); }
    @Override public Object get(final int i) { return dictGetVal(i); }
    @Override public Object set(int i, Object object) { return dictSetVal(i, object); }

    @Override
    public void add(int i, Object object) {

    }

    @Override
    public Object remove(final int i) {
        call("pop", i);
        return true;
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return 0;
    }

    @Override
    public ListIterator<Object> listIterator() {
        return null;
    }

    @Override
    public ListIterator<Object> listIterator(int i) {
        return null;
    }

    @Override
    public List<Object> subList(int i, int i1) {
        return Collections.emptyList();
    }

    @Override
    public boolean retainAll(Collection collection) {
        return false;
    }

    @Override
    public boolean removeAll(Collection collection) {
        return false;
    }

    @Override
    public boolean containsAll(Collection collection) {
        return false;
    }
}