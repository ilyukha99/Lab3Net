package Net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.util.Iterator;

import java.util.LinkedHashMap;
import java.util.Map;

public class AsyncEchoServer {
    private Selector selector;
    private DatagramChannel serverChannel;
    protected final int byteSize = 512;
    private final int port;

    LinkedHashMap<InetSocketAddress, Long> map = new LinkedHashMap<>();

    AsyncEchoServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try {
            System.out.println("Server is up on address: " + InetAddress.getLocalHost() + ", port: " + port);
            selector = Selector.open();
            serverChannel = DatagramChannel.open();
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), port);
            serverChannel.bind(address);
            serverChannel.configureBlocking(false);
            SelectionKey key = serverChannel.register(selector, SelectionKey.OP_READ);
            key.attach(new Attributes());
            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey curKey = iterator.next();
                    iterator.remove();
                    if (!curKey.isValid()) {
                        continue;
                    }

                    if (curKey.isReadable()) {
                        read(curKey);
                        curKey.interestOps(SelectionKey.OP_WRITE);
                    } else if (curKey.isWritable()) {
                        write(curKey);
                        curKey.interestOps(SelectionKey.OP_READ);
                    }
                }
            }
        }
        finally {
            close();
        }
    }

    public class Attributes {
        ByteBuffer recvBuffer;
        ByteBuffer sendBuffer;
        SocketAddress socketAddress;

        Attributes() {
            recvBuffer = ByteBuffer.allocate(byteSize);
        }
    }

    void read(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Attributes attributes = (Attributes) key.attachment();
        attributes.socketAddress = channel.receive(attributes.recvBuffer);
        String str = new String(attributes.recvBuffer.array());
        System.out.println(str);
        attributes.sendBuffer = StandardCharsets.UTF_8.encode(str.toUpperCase());
        attributes.recvBuffer.clear();
        attributes.recvBuffer.put(new byte[byteSize]);
        attributes.recvBuffer.rewind();

        updateMap(attributes.socketAddress);
        printMap();
    }

    void updateMap(SocketAddress address) {
        for (InetSocketAddress addr : map.keySet()) {
            if (addr.equals(address)) {
                map.put(addr, System.currentTimeMillis());
                return;
            }
        }

        map.put((InetSocketAddress) address, System.currentTimeMillis());
    }

    void printMap() {
        for (Map.Entry<InetSocketAddress, Long> entry : map.entrySet()) {
            System.out.println(entry);
        }
    }

    void write(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Attributes attributes = (Attributes) key.attachment();
        channel.send(attributes.sendBuffer, attributes.socketAddress);
        attributes.sendBuffer.rewind();
    }

    void close() throws IOException {
        if(selector.isOpen()) {
            selector.close();
        }
        if (serverChannel.isOpen()) {
            serverChannel.close();
        }
    }

    public static void main(String[] args) {
        try {
            AsyncEchoServer server = new AsyncEchoServer(Integer.parseInt(args[0]));
            server.start();
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }
}
