package illa4257.jpybridge;

import java.util.*;

public class Kwargs implements Map<String, Object> {
    private final Map<String, Object> map;

    public Kwargs(final Map<String, Object> kwargs) {
        this.map = kwargs;
    }

    public static Kwargs of(final Object... pairs) {
        if (pairs.length % 2 != 0)
            throw new IllegalArgumentException("Expected even number of kwargs (key/value pairs), got: " + pairs.length);
        final Map<String, Object> m = new HashMap<>(pairs.length / 2);
        for (int i = 0; i < pairs.length;)
            m.put(String.valueOf(pairs[i++]), pairs[i++]);
        return new Kwargs(m);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsValue(final Object o) {
        return map.containsValue(o);
    }

    @Override
    public Object get(final Object o) {
        return map.get(o);
    }

    @Override
    public Object put(final String s, final Object object) {
        return map.put(s, object);
    }

    @Override
    public Object remove(final Object o) {
        return map.remove(o);
    }

    @Override
    public void putAll(final Map<? extends String, ?> map) {
        this.map.putAll(map);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<Object> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(final Object o) {
        return map.equals(o);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }
}