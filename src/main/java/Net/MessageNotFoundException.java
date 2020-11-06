package Net;

public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException(String exceptionText) {
        super(exceptionText);
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
