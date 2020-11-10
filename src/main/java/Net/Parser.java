package Net;

import java.net.InetAddress;
import java.net.UnknownHostException;

//parses name, port, loss percent, optional: neighbour's ip + port
public class Parser {
    static String name;
    static int port;
    static int lossPercent;
    static InetAddress neighbourIP = null;
    static int neighbourPort;

    public static void parseArgs(String[] args) throws IllegalArgumentException, UnknownHostException {
        if (args.length != 3 && args.length != 5) {
            throw new IllegalArgumentException("Incorrect number of arguments.");
        }

        name = args[0];
        port = Integer.parseInt(args[1]);
        lossPercent = Integer.parseInt(args[2]);

        if (name.length() > 50) {
            throw new IllegalArgumentException("Node's name is too long.");
        }

        if (port < 1 || port > 65535 || lossPercent < 0 || lossPercent > 100) {
            throw new IllegalArgumentException("Bad port or percent found.");
        }

        System.out.print("Name of your node is: " + name + ", IP: " + InetAddress.getLocalHost().getHostAddress() +
                ", port: " + port + ", losses: " + lossPercent + "%.");

        if (args.length == 5) {
            neighbourIP = InetAddress.getByName(args[3]);
            neighbourPort = Integer.parseInt(args[4]);
            if (neighbourPort < 1 || neighbourPort > 65535) {
                throw new IllegalArgumentException("Bad neighbour's port.");
            }
            System.out.print(" Neighbour's IP: " + neighbourIP.getHostAddress() + ", port: " + neighbourPort + ".");
        }
        System.out.println();
    }
}
