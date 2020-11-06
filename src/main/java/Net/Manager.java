package Net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class Manager extends Thread {

    private static final int sendingInterval = 1000; //1 sec
    private static final int deathFactor = 5;

    @Override
    public void run() {
        long timeMark = System.currentTimeMillis(), deathTimeMark = timeMark;
        int sizeMark = Node.controlMap.size();
        try {
            while (true) {
                if (System.currentTimeMillis() - timeMark > sendingInterval) {
                    timeMark = System.currentTimeMillis();
                    sendNotifications();
                    if (System.currentTimeMillis() - deathTimeMark > deathFactor * sendingInterval) {
                        deathTimeMark = System.currentTimeMillis();
                        clearNodes(sizeMark);
                        sizeMark = Node.controlMap.size();
                    }
                }
            }
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }

    private void sendNotifications() throws IOException {
        for (Map.Entry<Bytes, LinkedList<InetSocketAddress>> entry : Node.controlMap.entrySet()) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(entry.getKey().byteArray);
            LinkedList<InetSocketAddress> addresses = entry.getValue();
            synchronized (Node.inetChannel) {
                for (InetSocketAddress address : addresses) {
                    Node.inetChannel.send(byteBuffer, address);
                }
            }
        }
    }

    private void clearNodes(int sizeMark) {
        List<Bytes> keys = new ArrayList<>(Node.controlMap.keySet());
        for (int it = 0; it < sizeMark; ++it) {
            Bytes key = keys.get(it);
            LinkedList<InetSocketAddress> list = Node.controlMap.get(key);
            for (InetSocketAddress address : list) {
                if (Node.neighbours.remove(address)) {
                    System.out.println("Removing: " + address);
                }
            }
            Node.controlMap.remove(key);
        }
    }
}