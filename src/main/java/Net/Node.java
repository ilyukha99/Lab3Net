package Net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Node {
    //map contains neighbour's channel, ip, port and time of last ACK from neighbour
    static final LinkedHashMap<Pair<InetSocketAddress, DatagramChannel>, Long>
            neighbours = new LinkedHashMap<>();
    private Selector selector;
    private DatagramChannel inetChannel;
    private final int socketByteBufferSize = 512;

    public void start() throws IOException {
        TerminalThread thread = new TerminalThread();
        thread.start();

        DatagramChannel neighboursChannel = DatagramChannel.open();
        if (Parser.neighbourIP != null) {
            InetSocketAddress address = new InetSocketAddress(Parser.neighbourIP, Parser.neighbourPort);
            neighboursChannel.connect(address);
            synchronized (neighbours) {
                neighbours.put(new Pair<>(address, neighboursChannel), 0L);
            }
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

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey curKey = keyIterator.next();
                    keyIterator.remove();
                    if (curKey.isValid() && curKey.isReadable()) {
                        if ((int) (Math.random() * 100) >= Parser.lossPercent) {
                            read(curKey);
                            distribute(curKey);
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
        ByteBuffer sendBuffer;
        SocketAddress socketAddress;

        public Attributes() {
            rcvBuffer = ByteBuffer.allocate(socketByteBufferSize);
        }
    }

    private void read(SelectionKey key) throws IOException {
        DatagramChannel curChannel = (DatagramChannel) key.channel();
        Attributes attributes = (Attributes) key.attachment();
        attributes.socketAddress = curChannel.receive(attributes.rcvBuffer);
        String string = new String(attributes.rcvBuffer.array());
        System.out.println("From " + attributes.socketAddress + ", message: " + string.trim());
        attributes.sendBuffer = StandardCharsets.UTF_8.encode(string);

        neighboursInfoUpdate(curChannel, (InetSocketAddress) attributes.socketAddress);
        printMap();

        attributes.rcvBuffer.clear();
        attributes.rcvBuffer.put(new byte[socketByteBufferSize]);
        attributes.rcvBuffer.clear();
    }

    private void checkMessage(String string) {

    }

    private void neighboursInfoUpdate(DatagramChannel channel, InetSocketAddress address) {
        synchronized (neighbours) {
            for (Pair<InetSocketAddress, DatagramChannel> info : neighbours.keySet()) {
                if (address.equals(info.getFirst())) {
                    neighbours.put(info, System.currentTimeMillis());
                    return;
                }
            }

            neighbours.put(new Pair<>(address, channel), System.currentTimeMillis());
        }
    }
    //if (address.getPort() == info.getFirst().getPort() && address.getAddress().equals(info.getFirst().getAddress())) {

    private void printMap() {
        synchronized (neighbours) {
            for (Map.Entry<Pair<InetSocketAddress, DatagramChannel>, Long> entry : neighbours.entrySet()) {
                System.out.println(entry);
            }
        }
    }

    private void distribute(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        synchronized (neighbours) {
            for (Pair<InetSocketAddress, DatagramChannel> info : neighbours.keySet()) {
                if (!attributes.socketAddress.equals(info.getFirst())) {
                    info.getSecond().send(attributes.sendBuffer, info.getFirst());
                    attributes.sendBuffer.rewind();
                }
            }
        }
    }
}
