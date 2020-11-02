package Net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

public class TerminalThread extends Thread {
    private final int terminalByteBufferSize = 512;

    @Override
    public void run() {
        BufferedReader terminalReader = new BufferedReader(new InputStreamReader(System.in), terminalByteBufferSize);
        byte[] inputTerminalBuffer = new byte[terminalByteBufferSize];
        int result;

        try {
            while (true) {
                if (terminalReader.ready()) {
                    result = System.in.read(inputTerminalBuffer, 0, terminalByteBufferSize);
                    if (result <= 0) {
                        continue;
                    }
                    broadcast(inputTerminalBuffer);
                    Arrays.fill(inputTerminalBuffer, (byte)0);
                }
            }
        } catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
        finally {
            try {
                terminalReader.close();
            }
            catch (IOException exc) {
                System.err.println(exc.getMessage());
            }
        }
    }

    private void broadcast(byte[] buffer) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        synchronized (Node.neighbours) {
            for (Pair<InetSocketAddress, DatagramChannel> info : Node.neighbours.keySet()) {
                if (info.getFirst() != null && info.getSecond() != null) {
                    info.getSecond().send(byteBuffer, info.getFirst());
                    byteBuffer.rewind();
                }
            }
        }
    }
}
