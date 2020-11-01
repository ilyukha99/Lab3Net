import Net.Node;
import Net.Parser;

public class Main {
    public static void main(String[] args) {
        try {
            Parser.parseArgs(args);
            Node node = new Node();
            node.start();
        }
        catch (Exception exc) {
            System.err.println("\n" + exc.getMessage());
        }
    }
}
