/** Initializes headless Java2D before loading any Swing classes. */
public final class Launcher {
    public static void main(String[] args) {
        if(args.length>0 && (args[0].equals("--self-test") || args[0].equals("--feature-test") || args[0].equals("--geometry-test") || args[0].equals("--export") || args[0].equals("--export-crop")))
            System.setProperty("java.awt.headless", "true");
        GeoSketch.main(args);
    }
}
