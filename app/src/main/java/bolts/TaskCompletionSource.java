package bolts;

/**
 * 创建TaskCompletionSource时，会创建一个task；
 * <p>
 * Task生产者的结果，通过该类，传递给Task的消费者(Task的消费者通过getTask()获取生产者的运行结果)
 * <p>
 * 可通过该类进行task的任务取消
 *
 * Allows safe orchestration of a task's completion, preventing the consumer from prematurely
 * completing the task. Essentially, it represents the producer side of a Task<TResult>, providing
 * access to the consumer side through the getTask() method while isolating the Task's completion
 * mechanisms from the consumer.
 */
public class TaskCompletionSource<TResult> {

    // 包含一个task
    private final Task<TResult> task;

    /**
     * 创建一个task
     * Creates a TaskCompletionSource that orchestrates a Task. This allows the creator of a task to
     * be solely responsible for its completion.
     */
    public TaskCompletionSource() {
        task = new Task<>();
    }

    /**
     * 返回一个task
     *
     * @return the Task associated with this TaskCompletionSource.
     */
    public Task<TResult> getTask() {
        return task;
    }

    /**
     * 尝试取消一个task
     * Sets the cancelled flag on the Task if the Task hasn't already been completed.
     */
    public boolean trySetCancelled() {
        return task.trySetCancelled();
    }

    /**
     * 当前任务结束后，设置返回数据
     * Sets the result on the Task if the Task hasn't already been completed.
     */
    public boolean trySetResult(TResult result) {
        return task.trySetResult(result);
    }

    /**
     * 将当前任务的错误信息赋值给当前的task
     * Sets the error on the Task if the Task hasn't already been completed.
     */
    public boolean trySetError(Exception error) {
        return task.trySetError(error);
    }

    /**
     * 取消当前任务
     * Sets the cancelled flag on the task, throwing if the Task has already been completed.
     */
    public void setCancelled() {
        if (!trySetCancelled()) {
            throw new IllegalStateException("Cannot cancel a completed task.");
        }
    }

    /**
     * 当前任务执行后的返回数据
     * Sets the result of the Task, throwing if the Task has already been completed.
     */
    public void setResult(TResult result) {
        if (!trySetResult(result)) {
            throw new IllegalStateException("Cannot set the result of a completed task.");
        }
    }

    /**
     * 将当前任务的错误情况赋值给当前的task
     * Sets the error of the Task, throwing if the Task has already been completed.
     */
    public void setError(Exception error) {
        if (!trySetError(error)) {
            throw new IllegalStateException("Cannot set the error on a completed task.");
        }
    }
}
