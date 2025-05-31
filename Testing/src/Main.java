import illa4257.jpybridge.JPyBridge;
import illa4257.jpybridge.PyObject;

public class Main {
    public static void main(final String[] args) throws Exception {
        final ProcessBuilder pb = new ProcessBuilder("python3", "bridge.py");
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        final Process p = pb.start();
        final JPyBridge b = new JPyBridge(p.getInputStream(), p.getOutputStream());

        final PyObject o = (PyObject) b.call(null, "{}");
        System.out.println(o);

        System.out.println(o.dictSetVal("GG", "VAL"));
        System.out.println(o.dictGetVal("GG"));
    }
}