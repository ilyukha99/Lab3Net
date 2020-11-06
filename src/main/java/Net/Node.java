package Net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Node {
    //list contains neighbour's ip, port
    static final CopyOnWriteArrayList<InetSocketAddress> neighbours = new CopyOnWriteArrayList<>();
    //map contains message and list of nodes, that didn't send ACK
    static final ConcurrentHashMap<Bytes, LinkedList<InetSocketAddress>>
            controlMap = new ConcurrentHashMap<>();
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
                if (inetChannel.isOpen()) {
                    sayGoodbye();
                }

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
            thread.setName("Terminal");
            thread.start();

            Manager manager = new Manager();
            manager.setName("Manager");
            manager.start();

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
                            //controlMap.keySet().stream().map(Bytes::toString).forEach(System.out::println);
                            //controlMap.values().stream().flatMap(LinkedList::stream).forEach(System.out::println);
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

        neighbours.addIfAbsent(attributes.socketAddress);
    }

    private void processMessage(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        byte[] message = attributes.rcvBuffer.array(), data, uuid = {};

        if (message.length >= 16) {
            data = Arrays.copyOfRange(message, 16, message.length);
            uuid = Arrays.copyOfRange(message, 0, 16);
        }
        else {
            data = message;
        }

        System.out.println("From: " + attributes.socketAddress + /*", Uuid: " + Arrays.toString(uuid) +*/
                        ", Message: " + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(data)).toString().trim());

        if (isNotZero(uuid)) {
            if (isNotZero(data)) { //MESSAGE
                controlMap.putIfAbsent(new Bytes(message), copyNeighboursWithout(attributes.socketAddress));
                distribute(key);
                synchronized (inetChannel) {
                    inetChannel.send(ByteBuffer.wrap(uuid), attributes.socketAddress); //sending ACK in any case
                }
            }
            else { //ACK
                try {
                    controlMap.get(findMessage(uuid)).remove(attributes.socketAddress);
                }
                catch (MessageNotFoundException ignored) {}
            }
        }
        else { //INFO
            if (data[0] == 0) {
                neighbours.remove(attributes.socketAddress);
                System.out.println("Terminated: " + attributes.socketAddress);
            }
        }
    }

    private LinkedList<InetSocketAddress> copyNeighboursWithout(InetSocketAddress address) {
        LinkedList<InetSocketAddress> list = new LinkedList<>(neighbours);
        list.remove(address);
        return list;
    }

    Bytes findMessage(byte[] uuid) {
        for (Bytes message : controlMap.keySet()) {
            if (Arrays.compare(Arrays.copyOfRange(message.byteArray, 0, 16), uuid) == 0) {
                return message;
            }
        }
        throw new MessageNotFoundException("Message with uuid: " + Arrays.toString(uuid) + " not found.");
    }

    boolean isNotZero(byte[] uuid) {
        for (byte b : uuid) {
            if (b != 0) {
                return true;
            }
        }
        return false;
    }

    private void distribute(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        synchronized (inetChannel) {
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
        attributes.rcvBuffer.rewind();
        attributes.rcvBuffer.put(new byte[mtuSaveSize]);
        attributes.rcvBuffer.clear();
    }

    private void sayGoodbye() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0});
        for (InetSocketAddress address : neighbours) {
            inetChannel.send(byteBuffer, address);
        }
    }
}
