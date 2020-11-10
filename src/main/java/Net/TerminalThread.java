package Net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.UUID;

public class TerminalThread extends Thread {
    private final Node node;
    private final byte[] name = Parser.name.concat(":").getBytes(StandardCharsets.UTF_8);

    public TerminalThread(Node node) {
        this.node = node;
    }

    @Override
    public void run() {
        final int mtuSaveSize = 1400;
        byte[] inputTerminalBuffer = new byte[mtuSaveSize];
        int result;

        try {
            while (true) {
                Arrays.fill(inputTerminalBuffer, (byte)0);
                System.arraycopy(name, 0, inputTerminalBuffer, 16, name.length);
                result = System.in.read(inputTerminalBuffer, name.length + 16, mtuSaveSize - 16 - name.length);
                if (result <= 0) {
                    continue;
                }

                inputTerminalBuffer = new String(inputTerminalBuffer).getBytes(StandardCharsets.UTF_8);
                fillByteArray(inputTerminalBuffer, generateUUIDArray());

                broadcast(ByteBuffer.wrap(inputTerminalBuffer));
                node.controlMap.put(new Bytes(inputTerminalBuffer), new ArrayList<>(node.neighbours.keySet()));
            }
        } catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }

    private void broadcast(ByteBuffer byteBuffer) throws IOException {
        synchronized (node.inetChannel) {
            for (InetSocketAddress address : node.neighbours.keySet()) {
                node.inetChannel.send(byteBuffer, address);
                byteBuffer.rewind();
            }
        }
    }

    //returns 128-bit big endian integer from UUID as byte array
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