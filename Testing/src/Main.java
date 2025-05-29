import illa4257.jpybridge.JPyBridge;

public class Main {
    public static void main(final String[] args) throws Exception {
        final ProcessBuilder pb = new ProcessBuilder("python3", "bridge.py");
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        final Process p = pb.start();
        final JPyBridge b = new JPyBridge(p.getInputStream(), p.getOutputStream());

        System.out.println(b.run("return 4 ** 2").getClass());
    }
}