package Net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class Manager extends Thread {

    private static final int sendingInterval = 1000; //1 sec
    private static final int deathFactor = 5;
    private final Node node;

    public Manager(Node node) {
        this.node = node;
    }

    @Override
    public void run() {
        long timeMark = System.currentTimeMillis(), deathTimeMark = timeMark;
        Set<Bytes> keys = node.controlMap.keySet();
        try {
            while (true) {
                if (System.currentTimeMillis() - timeMark > sendingInterval) {
                    sendNotifications();
                    timeMark = System.currentTimeMillis();
                    if (System.currentTimeMillis() - deathTimeMark > deathFactor * sendingInterval) {
                        deathTimeMark = System.currentTimeMillis();
                        clearNodes(keys);
                        keys = node.controlMap.keySet();
                    }
                }
            }
        } catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }

    private void sendNotifications() throws IOException {
        for (Map.Entry<Bytes, ArrayList<InetSocketAddress>> entry : node.controlMap.entrySet()) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(entry.getKey().byteArray);
            ArrayList<InetSocketAddress> addresses = entry.getValue();
            synchronized (node.inetChannel) {
                for (InetSocketAddress address : addresses) {
                    node.inetChannel.send(byteBuffer, address);
                }
            }
        }
    }

    private void clearNodes(Set<Bytes> previousSet) throws IOException {
        for (Bytes bytes : previousSet) {
            ArrayList<InetSocketAddress> addresses = node.controlMap.get(bytes);
            if (addresses == null) {
                continue;
            }
            for (InetSocketAddress socketAddress : addresses) {
                InetSocketAddress newNeighbour = node.neighbours.get(socketAddress);
                if (node.neighbours.remove(socketAddress) != null) {
                    System.out.println("Removing: " + socketAddress);
                    synchronized (node.localSocketAddress) {
                        if (socketAddress.equals(node.assistant) && node.neighbours.size() != 0) {
                            node.changeAssistant();
                        }
                    }
                    if (!newNeighbour.equals(node.fakeAssistantAddress) && !newNeighbour.equals(node.localSocketAddress)) {
                        node.processNewNeighbour(newNeighbour);
                    }
                }
            }
            node.controlMap.entrySet().removeIf(entry -> previousSet.contains(entry.getKey()));
        }
    }
}