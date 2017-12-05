package bolts;

/**
 * This class is used to retain a faulted task until either its error is observed or it is
 * finalized. If it is finalized with a task, then the uncaught exception handler is exected
 * with an UnobservedTaskException.
 * <p>
 * 1、用户在Task中设置了UnobservedExceptionHandler的回调
 * 2、存在用户未观测到的异常
 * 3、Task中UnobservedErrorNotifier的对象被回收时，会回调
 * 4、如果用户主动获取了该异常信息，则不再回调UnobservedExceptionHandler的unobservedException方法
 */
class UnobservedErrorNotifier {
    private Task<?> task;

    public UnobservedErrorNotifier(Task<?> task) {
        this.task = task;
    }

    /**
     * http://www.cnblogs.com/hyjiacan/archive/2012/02/17/java-finalize.html
     * UnobservedErrorNotifier的对象被gc时，会被调用
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            Task faultedTask = this.task;
            if (faultedTask != null) {
                Task.UnobservedExceptionHandler ueh = Task.getUnobservedExceptionHandler();
                if (ueh != null) {
                    ueh.unobservedException(faultedTask, new UnobservedTaskException(faultedTask.getError()));
                }
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * 如果用户主动获取了该异常信息，则不再回调unobservedException
     */
    public void setObserved() {
        task = null;
    }
}
