package Net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

public class Node {
    //list contains neighbour's ip, port
    static final LinkedList<InetSocketAddress> neighbours = new LinkedList<>();
    //map contains message and list of nodes, that didn't send ACK
    static final LinkedHashMap<ByteBuffer, LinkedList<InetSocketAddress>>
            controlMap = new LinkedHashMap<>();
    private Selector selector;
    static DatagramChannel inetChannel;
    private final int mtuSaveSize = 1400;


    public void start() throws IOException {

        DatagramChannel neighboursChannel = DatagramChannel.open();
        if (Parser.neighbourIP != null) {
            InetSocketAddress address = new InetSocketAddress(Parser.neighbourIP, Parser.neighbourPort);
            neighboursChannel.connect(address);
            neighbours.add(address);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (selector.isOpen()) {
                    selector.close();
                }
                if (inetChannel.isOpen()) {
                    inetChannel.close();
                }
                if (neighboursChannel.isOpen()) {
                    neighboursChannel.close();
                }
                System.out.println("Terminated by signal.");
            }
            catch (Exception exc) {
                System.err.println("Terminated by signal with exception: " + exc.getMessage());
            }
        }));

        try {
            selector = Selector.open();

            inetChannel = DatagramChannel.open();
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), Parser.port);
            inetChannel.bind(address);
            inetChannel.configureBlocking(false);
            SelectionKey key = inetChannel.register(selector, SelectionKey.OP_READ);
            key.attach(new Attributes());

            TerminalThread thread = new TerminalThread();
            thread.start();

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey curKey = keyIterator.next();
                    keyIterator.remove();
                    if (curKey.isValid() && curKey.isReadable()) {
                        if ((int) (Math.random() * 100) >= Parser.lossPercent) {
                            read(curKey);
                            processMessage(curKey);
                            clearRcvBuffer(curKey);
                        }
                    }
                }
            }
        }
        finally {
            if (selector.isOpen()) {
                selector.close();
            }
            if (inetChannel.isOpen()) {
                inetChannel.close();
            }
            if (neighboursChannel.isOpen()) {
                neighboursChannel.close();
            }
        }
    }

    class Attributes {
        ByteBuffer rcvBuffer;
        InetSocketAddress socketAddress;

        public Attributes() {
            rcvBuffer = ByteBuffer.allocate(mtuSaveSize);
        }
    }

    private void read(SelectionKey key) throws IOException {
        DatagramChannel curChannel = (DatagramChannel) key.channel();
        Attributes attributes = (Attributes) key.attachment();
        attributes.socketAddress = (InetSocketAddress) curChannel.receive(attributes.rcvBuffer);

        neighboursInfoUpdate(attributes.socketAddress);
        printMap();
    }

    private void processMessage(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        byte[] message = attributes.rcvBuffer.array();

        byte[] data = Arrays.copyOfRange(message, 16, message.length);
        byte[] uuid = Arrays.copyOfRange(message, 0, 16);

        //System.out.println("From: " + attributes.socketAddress + ", message: " + new String(data)
        //        + ", uuid: " + new String(uuid));

        if (!isEmpty(uuid)) {
            if (!isEmpty(data)) { //MESSAGE
                synchronized (controlMap) {
                    if (!controlMap.containsKey(attributes.rcvBuffer)) { //first time message received
                        controlMap.put(attributes.rcvBuffer, copyNeighboursWithout(attributes.socketAddress));
                        distribute(key);
                    }
                    synchronized (neighbours) {
                        inetChannel.send(ByteBuffer.wrap(uuid), attributes.socketAddress); //sending ACK anyway
                    }
                }
            }
            else { //ACK
                synchronized (controlMap) {
                    LinkedList<InetSocketAddress> list = controlMap.get(findMessage(uuid));
                    list.remove(attributes.socketAddress);
                }
            }
        }
        else { //INFO

        }
    }

    ByteBuffer findMessage(byte[] uuid) {
        ByteBuffer bytesUUID = ByteBuffer.wrap(uuid);
        for (ByteBuffer key : controlMap.keySet()) {
            key.rewind().limit(16);
            if (bytesUUID.equals(key)) {
                return key.clear();
            }
        }
        return null;
    }

    LinkedList<InetSocketAddress> copyNeighboursWithout(InetSocketAddress inetSocketAddress) {
        LinkedList<InetSocketAddress> result = new LinkedList<>();
        synchronized (neighbours) {
            for (InetSocketAddress address : neighbours) {
                if (!address.equals(inetSocketAddress)) {
                    result.add(address);
                }
            }
        }
        return result;
    }

    boolean isEmpty(byte[] uuid) {
        for (byte b : uuid) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private void neighboursInfoUpdate(InetSocketAddress address) {
        synchronized (neighbours) {
            if (!neighbours.contains(address)) {
                neighbours.add(address);
            }
        }
    }

    private void printMap() {
        synchronized (neighbours) {
            for (InetSocketAddress addr : neighbours) {
                System.out.println(addr);
            }
        }
    }

    private void distribute(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        synchronized (neighbours) {
            for (InetSocketAddress tempAddr : neighbours) {
                if (!attributes.socketAddress.equals(tempAddr)) {
                    inetChannel.send(attributes.rcvBuffer, tempAddr);
                    attributes.rcvBuffer.rewind();
                }
            }
        }
    }

    private void clearRcvBuffer(SelectionKey key) {
        Attributes attributes = (Attributes) key.attachment();
        attributes.rcvBuffer.clear();
        attributes.rcvBuffer.put(new byte[mtuSaveSize]);
        attributes.rcvBuffer.clear();
    }
}
