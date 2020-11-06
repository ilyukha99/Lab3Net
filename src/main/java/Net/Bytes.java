package Net;

import java.util.Arrays;

public class Bytes {
    byte[] byteArray;

    public Bytes(byte[] bytes) {
        byteArray = new byte[bytes.length];
        System.arraycopy(bytes, 0, byteArray, 0, byteArray.length);
    }

    @Override
    public String toString() {
        return Arrays.toString(byteArray);
    }

    @Override
    public int hashCode() {
        return Arrays.toString(byteArray).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Bytes) {
            Bytes bytes = (Bytes) obj;
            if (byteArray.length == bytes.byteArray.length) {
                for (int it = 0; it < byteArray.length; ++it) {
                    if (byteArray[it] != bytes.byteArray[it]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
