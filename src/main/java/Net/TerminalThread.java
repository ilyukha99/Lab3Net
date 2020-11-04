package Net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

public class TerminalThread extends Thread {
    private final int mtuSaveSize = 1400;

    @Override
    public void run() {
        byte[] inputTerminalBuffer = new byte[mtuSaveSize];
        int result;

        try {
            while (true) {
                Arrays.fill(inputTerminalBuffer, (byte)0);
                byte[] bytes = generateUUIDArray();
                fillByteArray(inputTerminalBuffer, bytes);
                result = System.in.read(inputTerminalBuffer, 16, mtuSaveSize - 16);
                if (result <= 0) {
                    continue;
                }
                ByteBuffer byteBuffer = ByteBuffer.wrap(inputTerminalBuffer);
                broadcast(byteBuffer);
                synchronized (Node.controlMap) {
                    synchronized (Node.neighbours) {
                        Node.controlMap.put(byteBuffer, new LinkedList<>(Node.neighbours));
                    }
                }
            }
        } catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }

    private void broadcast(ByteBuffer byteBuffer) throws IOException {
        synchronized (Node.neighbours) {
            for (InetSocketAddress addr : Node.neighbours) {
                if (addr != null) {
                    Node.inetChannel.send(byteBuffer, addr);
                    byteBuffer.rewind();
                }
            }
        }
    }

    //returns 128-bit big endian int from UUID as byte array
    private byte[] generateUUIDArray() {
        UUID uuid = UUID.randomUUID();
        byte[] uuidBytes = new byte[16];

        ByteBuffer.wrap(uuidBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits());

        return uuidBytes;
    }

    private void fillByteArray(byte[] dest, byte[] source) {
        for (int it = 0; it < dest.length && it < source.length; ++it) {
            dest[it] = source[it];
        }
    }
}
