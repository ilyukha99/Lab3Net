package Net;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Node {
    //map contains neighbour's and his assistant's InetSocketAddresses
    final ConcurrentHashMap<InetSocketAddress, InetSocketAddress> neighbours = new ConcurrentHashMap<>();
    //map contains message and list of nodes, that didn't send ACK
    final ConcurrentHashMap<Bytes, ArrayList<InetSocketAddress>>
            controlMap = new ConcurrentHashMap<>();
    final DatagramChannel inetChannel = DatagramChannel.open();
    final InetSocketAddress fakeAssistantAddress = new InetSocketAddress("localhost", 0);
    final InetSocketAddress localSocketAddress = new InetSocketAddress(InetAddress.getLocalHost(), Parser.port);
    private Selector selector;
    private final int mtuSaveSize = 1400;
    InetSocketAddress assistant = null;

    public Node() throws IOException {}

    public void start() throws IOException {

        TerminalThread terminalThread;
        Manager manager;

        DatagramChannel neighboursChannel = DatagramChannel.open();
        if (Parser.neighbourIP != null) {
            InetSocketAddress address = new InetSocketAddress(Parser.neighbourIP, Parser.neighbourPort);
            neighboursChannel.connect(address);
            neighbours.put(address, fakeAssistantAddress);
        }

        try {
            selector = Selector.open();

            inetChannel.bind(localSocketAddress);
            inetChannel.configureBlocking(false);
            SelectionKey key = inetChannel.register(selector, SelectionKey.OP_READ);
            key.attach(new Attributes());

            terminalThread = new TerminalThread(this);
            terminalThread.setName("Terminal");
            terminalThread.start();

            manager = new Manager(this);
            manager.setName("Manager");
            manager.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
//                    terminalThread.join();
//                    System.out.println("TerminalThread was joined.");
//                    manager.join();
//                    System.out.println("Manager was joined.");

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

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey curKey = keyIterator.next();
                    keyIterator.remove();
                    if (curKey.isValid() && curKey.isReadable()) {
                        if ((int) (Math.random() * 100) > Parser.lossPercent) {
                            read(curKey);
                            processMessage(curKey);
                            clearRcvBuffer(curKey);
                            //controlMap.keySet().stream().map(Bytes::toString).forEach(System.out::println);
                            //controlMap.values().stream().flatMap(ArrayList::stream).forEach(System.out::println);
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
    }

    private void processMessage(SelectionKey key) throws IOException {
        Attributes attributes = (Attributes) key.attachment();
        byte[] message = attributes.rcvBuffer.array(), data, uuid;

        data = Arrays.copyOfRange(message, 16, message.length);
        uuid = Arrays.copyOfRange(message, 0, 16);

        if (isNotZero(uuid)) {
            if (isNotZero(data)) { //MESSAGE
                if (!controlMap.containsKey(new Bytes(message))) { //put new message in map first time
                    controlMap.put(new Bytes(message), copyNeighboursWithout(attributes.socketAddress));
                    System.out.println(/*"From: " + attributes.socketAddress + ", Uuid: " + Arrays.toString(uuid) +
                            ", " +*/ StandardCharsets.UTF_8.decode(ByteBuffer.wrap(data)).toString().trim());
                }

                if (!neighbours.containsKey(attributes.socketAddress)) {
                    processNewNeighbour(attributes.socketAddress); //add new neighbour and send him assistant's info
                }

                distribute(attributes.rcvBuffer, attributes.socketAddress);
                synchronized (inetChannel) {
                    inetChannel.send(ByteBuffer.wrap(uuid), attributes.socketAddress); //sending ACK
                }
            }
            else { //ACK
                /*System.out.println("From: " + attributes.socketAddress + ", Uuid: " + Arrays.toString(uuid) +
                        ", Data: " + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(data)).toString().trim());*/
                try {
                    controlMap.get(findMessage(uuid)).remove(attributes.socketAddress);
                }
                catch (MessageNotFoundException ignored) {}
            }
        }
        else { //INFO
            /*System.out.println("From: " + attributes.socketAddress + ", Uuid: " + Arrays.toString(uuid) +
                    ", Data: " + Arrays.toString(Arrays.copyOf(data, 7)));*/
            manageInfo(message, data, attributes.socketAddress);
        }
    }

    private ArrayList<InetSocketAddress> copyNeighboursWithout(InetSocketAddress address) {
        ArrayList<InetSocketAddress> list = new ArrayList<>(neighbours.keySet());
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

    private void distribute(ByteBuffer byteBuffer, InetSocketAddress socketAddress) throws IOException {
        byteBuffer.rewind();
        synchronized (inetChannel) {
            for (InetSocketAddress tempAddr : neighbours.keySet()) {
                if (!socketAddress.equals(tempAddr)) {
                    inetChannel.send(byteBuffer, tempAddr);
                    byteBuffer.rewind();
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
        synchronized (inetChannel) {
            for (InetSocketAddress address : neighbours.keySet()) {
                inetChannel.send(byteBuffer, address);
            }
        }
    }

    //returns ByteBuffer with "zero uuid" and ip + port bytes for ipv4 and ipv6 addresses
    ByteBuffer inetSocketAddressToBytes(InetSocketAddress socketAddress) {
        byte[] bytes = new byte[mtuSaveSize];
        if (socketAddress.getAddress() instanceof Inet4Address) { //ipv4
            System.arraycopy(socketAddress.getAddress().getAddress(), 0, bytes, 16, 4);
            System.arraycopy(ByteBuffer.allocate(4).putInt(socketAddress.getPort()).array(), 2, bytes, 20, 2);
        }
        else { //ipv6
            System.arraycopy(socketAddress.getAddress().getAddress(), 0, bytes, 0, 16);
            System.arraycopy(ByteBuffer.allocate(4).putInt(socketAddress.getPort()).array(), 2, bytes, 32,2);
        }
        return ByteBuffer.wrap(bytes);
    }

    void processNewNeighbour(InetSocketAddress inetSocketAddress) throws IOException {
        neighbours.put(inetSocketAddress, fakeAssistantAddress);
        if (assistant == null) { //decide, which node will be an assistant
            synchronized (localSocketAddress) {
                assistant = (Parser.neighbourIP == null) ? inetSocketAddress :
                        new InetSocketAddress(Parser.neighbourIP, Parser.neighbourPort);
            }
        }

        synchronized (inetChannel) { //sending assistant's info
            inetChannel.send(inetSocketAddressToBytes(assistant), inetSocketAddress);
        }

        Bytes bytes = new Bytes(inetSocketAddressToBytes(assistant).array());
        if (!controlMap.containsKey(bytes)) { //implementation of verification control
            controlMap.put(bytes,
                    Stream.of(inetSocketAddress).collect(Collectors.toCollection(ArrayList::new)));
        }
        else {
            controlMap.get(bytes).add(inetSocketAddress);
        }
    }

    private void manageInfo(byte[] message, byte[] data, InetSocketAddress socketAddress) throws IOException {
        if ((data[4] | data[5] | data[16] | data[17]) == 0) { //if port in INFO is 0 (ipv4 and ipv6)
            if (!neighbours.containsKey(socketAddress)) {
                return;
            }
            InetSocketAddress newNeighbour = neighbours.get(socketAddress);
            neighbours.remove(socketAddress);
            System.out.println("Terminated: " + socketAddress);
            synchronized (localSocketAddress) {
                if (socketAddress.equals(assistant) && neighbours.size() != 0) {
                    changeAssistant();
                }
            }
            if (!newNeighbour.equals(fakeAssistantAddress) && !newNeighbour.equals(localSocketAddress)) {
                processNewNeighbour(newNeighbour);
            }
        }

        else if ((data[16] | data[17]) != 0) { //neighbour's assistant info, ipv6 address (not zero port)
            if (data[18] == -1) { //an optional check for the correctness of the address can be inserted (ACK)
                message[34] = 0; //data[18] == message[34]
                Bytes bytes = new Bytes(message);
                if (controlMap.containsKey(bytes)) {
                    controlMap.get(bytes).remove(socketAddress); //ACK accepted
                }
                return;
            }
            neighbours.putIfAbsent(socketAddress, fakeAssistantAddress);
            InetAddress ipv6Address = InetAddress.getByAddress(Arrays.copyOfRange(data, 0, 16));
            int port = ByteBuffer.wrap(new byte[]{0,0,data[16], data[17]}).getInt();
            neighbours.put(socketAddress, new InetSocketAddress(ipv6Address, port)); //verification control
            message[34] = -1;
            synchronized (inetChannel) {
                inetChannel.send(ByteBuffer.wrap(message), socketAddress); //sending ACK
            }
        }

        else if ((data[4] | data[5]) != 0) { //can only be ipv4 (not zero port)
            if (data[6] == -1) { //an optional check for the correctness of the address can be inserted (ACK)
                message[22] = 0; //data[6] == message[22]
                Bytes bytes = new Bytes(message);
                if (controlMap.containsKey(bytes)) {
                    controlMap.get(bytes).remove(socketAddress); //ACK accepted
                }
                return;
            }
            InetAddress ipv4Address = InetAddress.getByAddress(Arrays.copyOfRange(data, 0, 4));
            int port = ByteBuffer.wrap(new byte[]{0,0,data[4], data[5]}).getInt();
            neighbours.put(socketAddress, new InetSocketAddress(ipv4Address, port)); //verification control
            message[22] = -1;
            synchronized (inetChannel) {
                inetChannel.send(ByteBuffer.wrap(message), socketAddress); //sending ACK
            }
        }
    }

    private void broadcast(ByteBuffer byteBuffer) throws IOException {
        synchronized (inetChannel) {
            for (InetSocketAddress address : neighbours.keySet()) {
                inetChannel.send(byteBuffer, address);
                byteBuffer.rewind();
            }
        }
    }

    void changeAssistant() throws IOException {
        assistant = neighbours.keys().nextElement();
        ByteBuffer buffer = inetSocketAddressToBytes(assistant);
        broadcast(buffer);
        try {
            controlMap.remove(findMessage(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
        }
        catch (MessageNotFoundException ignored) {}
        controlMap.put(new Bytes(buffer.array()), new ArrayList<>(neighbours.keySet()));
    }
}
