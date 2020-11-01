package Net;

public class Pair<T, U> {
    private final T first;
    private final U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Pair) {
            Pair<?, ?> tmp = (Pair<?, ?>)other;
            return this.first.equals(tmp.first) && this.second.equals(tmp.second);
        }

        return false;
    }

    public int hashCode() {
        return (first != null && second != null) ? first.hashCode() + second.hashCode() : 0;
    }

    public String toString() {
        return "[" + first.toString() + ", " + second.toString() + "]";
    }
}