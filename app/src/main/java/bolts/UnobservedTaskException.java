package bolts;

/**
 * Used to signify that a Task's error went unobserved.
 * <p/>
 * 未被用户捕获的异常
 */
public class UnobservedTaskException extends RuntimeException {
    public UnobservedTaskException(Throwable cause) {
        super(cause);
    }
}
