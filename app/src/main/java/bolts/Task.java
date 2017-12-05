/*
 *  Copyright (c) 2014, Facebook, Inc.
 *  All rights reserved.
 *
 *  This source code is licensed under the BSD-style license found in the
 *  LICENSE file in the root directory of this source tree. An additional grant 
 *  of patent rights can be found in the PATENTS file in the same directory.
 *
 */
package bolts;

import com.toast.demo.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;



/*
 * add by xiaxveliang@163.com
 *
 *该Task为facebook开源框架：
 * https://github.com/BoltsFramework/Bolts-Android
 *
 * 为什么添加该Task?
 * 因为AsyncTask问题过多：
 * 1、是AsyncTask为非静态的内部类，会持有外部类的引用，有一定内存溢出的可能；
 * 2、是大家在用AsyncTask大多不自定义线程池，造成顺序等待执行的任务过多，从而造成任务不能及时执行。
 * （建议项目中不要再新增AsyncTask的使用）
 *
 * 该task如何使用：
 *
 *  //###############################第1部分###########################
    //运行在后台线程中(与“更新UI相关的网络请求”和“与UI相关的DB请求”用该线程池 )
    // 我还定义了一个日志上传线程池，所有的日志上传应该使用LOG_UPLOAD_EXECUTOR线程池，谢谢
    public Task<Boolean> runOnBackgroundThread() {
        //
        return Task.call(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // Background_Thread

                return true;
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    //运行在当前线程中
    public Task<Boolean> runOnCurrentThread() {
        //
        return Task.call(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // Current_Thread
                return true;
            }
        });
    }

    //运行在UI线程中
    public Task<Boolean> runOnUIThread() {
        //
        return Task.call(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // UI_Thread
                return true;
            }
        }, Task.UI_THREAD_EXECUTOR);
    }

    //###############################第2部分###########################
    //任务顺序执行
    public Task synchronousTask(CancellationToken cancellationToken) {

        return Task.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // UI_Thread
                LogUtils.d(TAG, "---1 UI_Thread---");
                return null;
            }
        }, Task.UI_THREAD_EXECUTOR, cancellationToken).onSuccess(new Continuation<Void, Boolean>() {
            @Override
            public Boolean then(Task<Void> task) throws Exception {
                // Background_Thread
                LogUtils.d(TAG, "---2 Background_Thread---");
                return true;
            }
        }, Task.BACKGROUND_EXECUTOR).continueWith(new Continuation<Boolean, Void>() {
            @Override
            public Void then(Task<Boolean> task) throws Exception {
                // UI_Thread
                LogUtils.d(TAG, "---3 UI_Thread---");
                return null;
            }
        }, Task.UI_THREAD_EXECUTOR);
    }

    //###############################第3部分###########################
    //多任务并行
    public void whenAll() {

        ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
        for (int i = 0; i < 3; i++) {
            final int index = i;
            // Start this delete immediately and add its task to the list.
            tasks.add(Task.call(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    // UI_Thread
                    LogUtils.d(TAG, "---###########################---");
                    LogUtils.d(TAG, "index: " + index);
                    return null;
                }
            }, Task.BACKGROUND_EXECUTOR));
        }

        Task.whenAll(tasks);

    }

    //###############################第4部分###########################
    //自定义线程池
    static final Executor NETWORK_EXECUTOR = Executors.newCachedThreadPool();
    static final Executor DISK_EXECUTOR = Executors.newCachedThreadPool();

    //
    public void changeThreadPool() {
        Task.call(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // NETWORK_Thread
                return null;
            }
        }, NETWORK_EXECUTOR).continueWith(new Continuation<Boolean, String>() {
            @Override
            public String then(Task<Boolean> task) throws Exception {
                // NETWORK_Thread
                return null;
            }
        }).continueWith(new Continuation<String, Integer>() {
            @Override
            public Integer then(Task<String> task) throws Exception {
                // DISK_Thread
                return null;
            }
        }, DISK_EXECUTOR);
    }

    //###############################第5部分###########################
    //取消任务
    public void cancelTask() {
        CancellationTokenSource cts = new CancellationTokenSource();
        Task<Boolean> stringTask = runOnBackgroundThread(cts.getToken());
        cts.cancel();
    }

    //运行在后台线程中
    public Task<Boolean> runOnBackgroundThread(CancellationToken cancellationToken) {
        //
        return Task.call(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // Background_Thread
                return true;
            }
        }, Task.BACKGROUND_EXECUTOR, cancellationToken);
    }
 *
 */


/**
 * Represents the result of an asynchronous operation.
 *
 * @param <TResult> The type of the result of the task.
 */
public class Task<TResult> {
    private static final String TAG = Task.class.getSimpleName();
    /**
     * An {@link Executor} that executes tasks in parallel.
     * 后台异步线程
     */
    public static final ExecutorService BACKGROUND_EXECUTOR = BoltsExecutors.background();

    /**
     * An {@link Executor} that executes tasks in the current thread unless
     * the stack runs too deep, at which point it will delegate to {@link Task#BACKGROUND_EXECUTOR} in
     * order to trim the stack.
     * 在当前线程执行当前任务，如果任务超过最大深度，则在后台线程执行该任务
     */
    private static final Executor IMMEDIATE_EXECUTOR = BoltsExecutors.immediate();

    /**
     * An {@link Executor} that executes tasks on the UI thread.
     * 运行在UI线程的线程池
     */
    public static final Executor UI_THREAD_EXECUTOR = AndroidExecutors.uiThread();

    /**
     * Interface for handlers invoked when a failed {@code Task} is about to be
     * finalized, but the exception has not been consumed.
     * <p>
     * <p>The handler will execute in the GC thread, so if the handler needs to do
     * anything time consuming or complex it is a good idea to fire off a {@code Task}
     * to handle the exception.
     * <p>
     * 未被检测Exception的回调,UnobservedExceptionHandler GC时会被回调
     *
     * @see #getUnobservedExceptionHandler
     * @see #setUnobservedExceptionHandler
     */
    public interface UnobservedExceptionHandler {
        /**
         * Method invoked when the given task has an unobserved exception.
         * <p>Any exception thrown by this method will be ignored.
         *
         * @param t the task
         * @param e the exception
         */
        void unobservedException(Task<?> t, UnobservedTaskException e);
    }

    // 未被检测Exception的回调
    // null unless explicitly set
    private static volatile UnobservedExceptionHandler unobservedExceptionHandler;

    /**
     * Returns the handler invoked when a task has an unobserved
     * exception or {@code null}.
     * 获取未被检测Exception的回调
     */
    public static UnobservedExceptionHandler getUnobservedExceptionHandler() {
        return unobservedExceptionHandler;
    }

    /**
     * Set the handler invoked when a task has an unobserved exception.
     * 设置未被检测Exception的回调
     *
     * @param eh the object to use as an unobserved exception handler. If
     *           <tt>null</tt> then unobserved exceptions will be ignored.
     */
    public static void setUnobservedExceptionHandler(UnobservedExceptionHandler eh) {
        unobservedExceptionHandler = eh;
    }

    /**
     * // 提供了一个可以阻塞当前线程，直到Task运行结束的方法 waitForCompletion
     */
    private final Object lock = new Object();
    private boolean complete;
    private boolean cancelled;

    // 当前Task对象的运行结果
    private TResult result;
    private Exception error;

    // -------未被观测到的Exception--------
    // 标记Exception是否被检测到的标志位
    private boolean errorHasBeenObserved;
    // 未被检测到Exception的报告者(当前Task对象)
    private UnobservedErrorNotifier unobservedErrorNotifier;


    //--------continuation队列----------
    // 每一个Task对象都有一个自己的Continuation队列(continuations非全局，这样不同task的continuation就不会混淆)
    private List<Continuation<TResult, Void>> continuations = new ArrayList<>();


    Task() {
    }

    private Task(TResult result) {
        trySetResult(result);
    }

    private Task(boolean cancelled) {
        if (cancelled) {
            trySetCancelled();
        } else {
            trySetResult(null);
        }
    }


    /**
     * @return {@code true} if the task completed (has a result, an error, or was cancelled.
     * {@code false} otherwise.
     */
    public boolean isCompleted() {
        synchronized (lock) {
            return complete;
        }
    }

    /**
     * @return {@code true} if the task was cancelled, {@code false} otherwise.
     */
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /**
     * @return {@code true} if the task has an error, {@code false} otherwise.
     */
    public boolean isFaulted() {
        synchronized (lock) {
            return getError() != null;
        }
    }

    /**
     * @return The result of the task, if set. {@code null} otherwise.
     */
    public TResult getResult() {
        synchronized (lock) {
            return result;
        }
    }

    /**
     * @return The error for the task, if set. {@code null} otherwise.
     */
    public Exception getError() {
        synchronized (lock) {
            if (error != null) {

                // -----标记该错误已被用户获取----
                errorHasBeenObserved = true;
                // 置空错误的观察者
                if (unobservedErrorNotifier != null) {
                    unobservedErrorNotifier.setObserved();
                    unobservedErrorNotifier = null;
                }
            }
            return error;
        }
    }

    /**
     * Blocks until the task is complete.
     * <p>
     * 阻塞当前线程，直到任务结束
     */
    public void waitForCompletion() throws InterruptedException {
        synchronized (lock) {
            if (!isCompleted()) {
                lock.wait();
            }
        }
    }

    /**
     * Blocks until the task is complete or times out.
     *
     * @return {@code true} if the task completed (has a result, an error, or was cancelled).
     * {@code false} otherwise.
     */
    public boolean waitForCompletion(long duration, TimeUnit timeUnit) throws InterruptedException {
        synchronized (lock) {
            if (!isCompleted()) {
                lock.wait(timeUnit.toMillis(duration));
            }
            return isCompleted();
        }
    }

    /**
     * 创建一个运行结束的task
     * Creates a completed task with the given value.
     */
    @SuppressWarnings("unchecked")
    public static <TResult> Task<TResult> forResult(TResult value) {
        if (value == null) {
            return (Task<TResult>) TASK_NULL;
        }
        if (value instanceof Boolean) {
            return (Task<TResult>) ((Boolean) value ? TASK_TRUE : TASK_FALSE);
        }
        // 创建一个task,将结果赋值给task
        TaskCompletionSource<TResult> tcs = new TaskCompletionSource<>();
        //
        tcs.setResult(value);
        // 返回task
        return tcs.getTask();
    }

    /**
     * 创建一个出错的task
     * Creates a faulted task with the given error.
     */
    public static <TResult> Task<TResult> forError(Exception error) {
        // 创建一个task，将错误赋值给task
        TaskCompletionSource<TResult> tcs = new TaskCompletionSource<>();
        //
        tcs.setError(error);
        // 返回task
        return tcs.getTask();
    }

    /**
     * 创建一个取消的task
     * Creates a cancelled task.
     */
    @SuppressWarnings("unchecked")
    public static <TResult> Task<TResult> cancelled() {
        return (Task<TResult>) TASK_CANCELLED;
    }

    /**
     * 延时一段时间创建一个task
     * Creates a task that completes after a time delay.
     *
     * @param delay The number of milliseconds to wait before completing the returned task. Zero and
     *              negative values are treated as requests for immediate execution.
     */
    public static Task<Void> delay(long delay) {
        return delay(delay, BoltsExecutors.scheduled(), null);
    }

    /**
     * 创建一个可取消的延时task
     * Creates a task that completes after a time delay.
     *
     * @param delay             The number of milliseconds to wait before completing the returned task. Zero and
     *                          negative values are treated as requests for immediate execution.
     * @param cancellationToken The optional cancellation token that will be checked prior to
     *                          completing the returned task.
     */
    public static Task<Void> delay(long delay, CancellationToken cancellationToken) {

        return delay(delay, BoltsExecutors.scheduled(), cancellationToken);
    }

    /**
     * delay一定的时间运行
     *
     * @param delay
     * @param executor
     * @param cancellationToken 这里传入了BoltsExecutors.scheduled()
     * @return
     */
    static Task<Void> delay(long delay, ScheduledExecutorService executor, final CancellationToken cancellationToken) {
        // 如果任务取消了
        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
            return Task.cancelled();
        }
        // 返回一个任务
        if (delay <= 0) {
            return Task.forResult(null);
        }
        // 创建一个任务
        final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        // 返回了一个ScheduledFuture
        final ScheduledFuture<?> scheduled = executor.schedule(new Runnable() {
            @Override
            public void run() {
                // 延迟一段时间后，再执行task任务
                tcs.trySetResult(null);
            }
        }, delay, TimeUnit.MILLISECONDS);

        if (cancellationToken != null) {
            cancellationToken.register(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d(TAG, "--register Runnable-run--");
                    scheduled.cancel(true);
                    tcs.trySetCancelled();
                }
            });
        }

        return tcs.getTask();
    }

    /**
     * Makes a fluent cast of a Task's result possible, avoiding an extra continuation just to cast
     * the type of the result.
     */
    public <TOut> Task<TOut> cast() {
        @SuppressWarnings("unchecked")
        Task<TOut> task = (Task<TOut>) this;
        return task;
    }

    /**
     * Turns a Task<T> into a Task<Void>, dropping any result.
     */
    public Task<Void> makeVoid() {
        return this.continueWithTask(new Continuation<TResult, Task<Void>>() {
            @Override
            public Task<Void> then(Task<TResult> task) throws Exception {
                if (task.isCancelled()) {
                    return Task.cancelled();
                }
                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                }
                return Task.forResult(null);
            }
        });
    }

    /**
     * 运行在后台线程池当中
     * Invokes the callable on a background thread, returning a Task to represent the operation.
     * If you want to cancel the resulting Task throw a {@link CancellationException} from the callable.
     *
     * @param callable
     * @param <TResult>
     * @return 返回一个task
     */
    public static <TResult> Task<TResult> callInBackground(Callable<TResult> callable) {
        return call(callable, BACKGROUND_EXECUTOR, null);
    }

    /**
     * 运行在后台线程池当中
     * Invokes the callable on a background thread, returning a Task to represent the operation.
     */
    public static <TResult> Task<TResult> callInBackground(Callable<TResult> callable, CancellationToken ct) {
        return call(callable, BACKGROUND_EXECUTOR, ct);
    }

    /**
     * 运行在给定的线程池当中
     * Invokes the callable using the given executor, returning a Task to represent the operation.
     * <p>
     * If you want to cancel the resulting Task throw a {@link CancellationException}
     * from the callable.
     */
    public static <TResult> Task<TResult> call(final Callable<TResult> callable, Executor executor) {
        return call(callable, executor, null);
    }

    /**
     * Invokes the callable using the given executor, returning a Task to represent the operation.
     *
     * @param callable
     * @param executor
     * @param ct
     * @param <TResult>
     * @return 返回任务
     */
    public static <TResult> Task<TResult> call(final Callable<TResult> callable, Executor executor,
                                               final CancellationToken ct) {
        // Task生产者的运行结果
        final TaskCompletionSource<TResult> tcs = new TaskCompletionSource<>();
        //
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // 判断是否任务已取消
                    if (ct != null && ct.isCancellationRequested()) {
                        tcs.setCancelled();
                        return;
                    }
                    // 运行当前Task相关
                    try {
                        // 执行call中的任务，将结果返回到setResult中
                        tcs.setResult(callable.call());
                    } catch (CancellationException e) {
                        tcs.setCancelled();
                    } catch (Exception e) {
                        tcs.setError(e);
                    }
                }
            });
        } catch (Exception e) {
            tcs.setError(new ExecutorException(e));
        }
        // 返回一个task
        return tcs.getTask();
    }

    /**
     * 运行在当前线程中
     * Invokes the callable on the current thread, producing a Task.
     * <p>
     * If you want to cancel the resulting Task throw a {@link CancellationException}
     * from the callable.
     */
    public static <TResult> Task<TResult> call(final Callable<TResult> callable) {
        return call(callable, IMMEDIATE_EXECUTOR, null);
    }

    /**
     * 运行在当前线程中
     * Invokes the callable on the current thread, producing a Task.
     */
    public static <TResult> Task<TResult> call(final Callable<TResult> callable, CancellationToken ct) {
        return call(callable, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * Creates a task that will complete when any of the supplied tasks have completed.
     * <p>
     * The returned task will complete when any of the supplied tasks has completed. The returned task
     * will always end in the completed state with its result set to the first task to complete. This
     * is true even if the first task to complete ended in the canceled or faulted state.
     *
     * @param tasks The tasks to wait on for completion.
     * @return A task that represents the completion of one of the supplied tasks.
     * The return task's result is the task that completed.
     */
    public static <TResult> Task<Task<TResult>> whenAnyResult(Collection<? extends Task<TResult>> tasks) {
        if (tasks.size() == 0) {
            return Task.forResult(null);
        }
        // 创建一个task
        final TaskCompletionSource<Task<TResult>> firstCompleted = new TaskCompletionSource<>();
        //
        final AtomicBoolean isAnyTaskComplete = new AtomicBoolean(false);

        for (Task<TResult> task : tasks) {
            task.continueWith(new Continuation<TResult, Void>() {
                @Override
                public Void then(Task<TResult> task) {
                    if (isAnyTaskComplete.compareAndSet(false, true)) {
                        firstCompleted.setResult(task);
                    } else {
                        Throwable ensureObserved = task.getError();
                    }
                    return null;
                }
            });
        }
        return firstCompleted.getTask();
    }

    /**
     * Creates a task that will complete when any of the supplied tasks have completed.
     * <p>
     * The returned task will complete when any of the supplied tasks has completed. The returned task
     * will always end in the completed state with its result set to the first task to complete. This
     * is true even if the first task to complete ended in the canceled or faulted state.
     *
     * @param tasks The tasks to wait on for completion.
     * @return A task that represents the completion of one of the supplied tasks.
     * The return task's Result is the task that completed.
     */
    @SuppressWarnings("unchecked")
    public static Task<Task<?>> whenAny(Collection<? extends Task<?>> tasks) {
        if (tasks.size() == 0) {
            return Task.forResult(null);
        }

        final TaskCompletionSource<Task<?>> firstCompleted = new TaskCompletionSource<>();
        final AtomicBoolean isAnyTaskComplete = new AtomicBoolean(false);

        for (Task<?> task : tasks) {
            ((Task<Object>) task).continueWith(new Continuation<Object, Void>() {
                @Override
                public Void then(Task<Object> task) {
                    if (isAnyTaskComplete.compareAndSet(false, true)) {
                        firstCompleted.setResult(task);
                    } else {
                        Throwable ensureObserved = task.getError();
                    }
                    return null;
                }
            });
        }
        return firstCompleted.getTask();
    }

    /**
     * Creates a task that completes when all of the provided tasks are complete.
     * <p>
     * If any of the supplied tasks completes in a faulted state, the returned task will also complete
     * in a faulted state, where its exception will resolve to that {@link Exception} if a
     * single task fails or an {@link AggregateException} of all the {@link Exception}s
     * if multiple tasks fail.
     * <p>
     * If none of the supplied tasks faulted but at least one of them was cancelled, the returned
     * task will end as cancelled.
     * <p>
     * If none of the tasks faulted and none of the tasks were cancelled, the resulting task will end
     * completed. The result of the returned task will be set to a list containing all of the results
     * of the supplied tasks in the same order as they were provided (e.g. if the input tasks collection
     * contained t1, t2, t3, the output task's result will return an {@code List&lt;TResult&gt;}
     * where {@code list.get(0) == t1.getResult(), list.get(1) == t2.getResult(), and
     * list.get(2) == t3.getResult()}).
     * <p>
     * If the supplied collection contains no tasks, the returned task will immediately transition to
     * a completed state before it's returned to the caller.
     * The returned {@code List&lt;TResult&gt;} will contain 0 elements.
     *
     * @param tasks The tasks that the return value will wait for before completing.
     * @return A Task that will resolve to {@code List&lt;TResult&gt;} when all the tasks are resolved.
     */
    public static <TResult> Task<List<TResult>> whenAllResult(final Collection<? extends Task<TResult>> tasks) {
        return whenAll(tasks).onSuccess(new Continuation<Void, List<TResult>>() {
            @Override
            public List<TResult> then(Task<Void> task) throws Exception {
                if (tasks.size() == 0) {
                    return Collections.emptyList();
                }

                List<TResult> results = new ArrayList<>();
                for (Task<TResult> individualTask : tasks) {
                    results.add(individualTask.getResult());
                }
                return results;
            }
        });
    }

    /**
     * Creates a task that completes when all of the provided tasks are complete.
     * <p>
     * If any of the supplied tasks completes in a faulted state, the returned task will also complete
     * in a faulted state, where its exception will resolve to that {@link Exception} if a
     * single task fails or an {@link AggregateException} of all the {@link Exception}s
     * if multiple tasks fail.
     * <p>
     * If none of the supplied tasks faulted but at least one of them was cancelled, the returned
     * task will end as cancelled.
     * <p>
     * If none of the tasks faulted and none of the tasks were canceled, the resulting task will
     * end in the completed state.
     * <p>
     * If the supplied collection contains no tasks, the returned task will immediately transition
     * to a completed state before it's returned to the caller.
     *
     * @param tasks The tasks that the return value will wait for before completing.
     * @return A Task that will resolve to {@code Void} when all the tasks are resolved.
     */
    public static Task<Void> whenAll(Collection<? extends Task<?>> tasks) {
        if (tasks.size() == 0) {
            return Task.forResult(null);
        }

        final TaskCompletionSource<Void> allFinished = new TaskCompletionSource<>();
        final ArrayList<Exception> causes = new ArrayList<>();
        final Object errorLock = new Object();
        final AtomicInteger count = new AtomicInteger(tasks.size());
        final AtomicBoolean isCancelled = new AtomicBoolean(false);

        for (Task<?> task : tasks) {
            @SuppressWarnings("unchecked")
            Task<Object> t = (Task<Object>) task;
            t.continueWith(new Continuation<Object, Void>() {
                @Override
                public Void then(Task<Object> task) {
                    if (task.isFaulted()) {
                        synchronized (errorLock) {
                            causes.add(task.getError());
                        }
                    }

                    if (task.isCancelled()) {
                        isCancelled.set(true);
                    }

                    if (count.decrementAndGet() == 0) {
                        if (causes.size() != 0) {
                            if (causes.size() == 1) {
                                allFinished.setError(causes.get(0));
                            } else {
                                Exception error = new AggregateException(
                                        String.format("There were %d exceptions.", causes.size()),
                                        causes);
                                allFinished.setError(error);
                            }
                        } else if (isCancelled.get()) {
                            allFinished.setCancelled();
                        } else {
                            allFinished.setResult(null);
                        }
                    }
                    return null;
                }
            });
        }

        return allFinished.getTask();
    }

    /**
     * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is
     * a task continuation.
     */
    public Task<Void> continueWhile(Callable<Boolean> predicate,
                                    Continuation<Void, Task<Void>> continuation) {
        return continueWhile(predicate, continuation, IMMEDIATE_EXECUTOR, null);
    }

    /**
     * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is
     * a task continuation.
     */
    public Task<Void> continueWhile(Callable<Boolean> predicate,
                                    Continuation<Void, Task<Void>> continuation, CancellationToken ct) {
        return continueWhile(predicate, continuation, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is
     * a task continuation.
     */
    public Task<Void> continueWhile(final Callable<Boolean> predicate,
                                    final Continuation<Void, Task<Void>> continuation, final Executor executor) {
        return continueWhile(predicate, continuation, executor, null);
    }

    /**
     * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is a task continuation.
     */
    public Task<Void> continueWhile(final Callable<Boolean> predicate,
                                    final Continuation<Void, Task<Void>> continuation, final Executor executor,
                                    final CancellationToken ct) {
        final Capture<Continuation<Void, Task<Void>>> predicateContinuation = new Capture<>();
        predicateContinuation.set(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) throws Exception {
                if (ct != null && ct.isCancellationRequested()) {
                    return Task.cancelled();
                }

                if (predicate.call()) {
                    return Task.<Void>forResult(null).onSuccessTask(continuation, executor)
                            .onSuccessTask(predicateContinuation.get(), executor);
                }
                return Task.forResult(null);
            }
        });
        return makeVoid().continueWithTask(predicateContinuation.get(), executor);
    }

    /**
     * Adds a continuation that will be scheduled using the executor, returning a new task that
     * completes after the continuation has finished running. This allows the continuation to be
     * scheduled on different thread.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWith(
            final Continuation<TResult, TContinuationResult> continuation, final Executor executor) {
        return continueWith(continuation, executor, null);
    }

    /**
     * 非静态方法，需要上一个task执行后的结果对象来调用
     * Adds a continuation that will be scheduled using the executor, returning a new task that
     * completes after the continuation has finished running. This allows the continuation to be
     * scheduled on different thread.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWith(
            final Continuation<TResult, TContinuationResult> continuation, final Executor executor,
            final CancellationToken ct) {

        // 上次task的完成情况
        boolean completed;
        // 构建一个新的task
        final TaskCompletionSource<TContinuationResult> tcs = new TaskCompletionSource<>();
        //
        synchronized (lock) {
            // 上次task的完成情况
            completed = this.isCompleted();
            // 如果上次task 未完成，添加到task列表
            if (!completed) {
                // 向continuations队列中添加一个“需提交到不同线程池执行的task任务”
                this.continuations.add(new Continuation<TResult, Void>() {
                    @Override
                    public Void then(Task<TResult> task) {
                        //
                        completeImmediately(tcs, continuation, task, executor, ct);
                        return null;
                    }
                });
            }
        }
        // 上次任务完成,当前线程执行
        if (completed) {
            completeImmediately(tcs, continuation, this, executor, ct);
        }
        //
        return tcs.getTask();
    }

    /**
     * 非静态方法，需要上一个task执行后的结果对象来调用
     * Adds a synchronous continuation to this task, returning a new task that completes after the
     * continuation has finished running.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWith(
            Continuation<TResult, TContinuationResult> continuation) {
        return continueWith(continuation, IMMEDIATE_EXECUTOR, null);
    }

    /**
     * Adds a synchronous continuation to this task, returning a new task that completes after the
     * continuation has finished running.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWith(
            Continuation<TResult, TContinuationResult> continuation, CancellationToken ct) {
        return continueWith(continuation, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * 非静态方法，返回一个task
     * Adds an Task-based continuation to this task that will be scheduled using the executor,
     * returning a new task that completes after the task returned by the continuation has completed.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
            // Continuation的引用
            final Continuation<TResult, Task<TContinuationResult>> continuation,
            // 线程池
            final Executor executor) {
        //
        return continueWithTask(continuation, executor, null);
    }

    /**
     * 非静态方法，上一任务的task结果来调用
     * Adds an Task-based continuation to this task that will be scheduled using the executor,
     * returning a new task that completes after the task returned by the continuation has completed.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
            final Continuation<TResult, Task<TContinuationResult>> continuation, final Executor executor,
            final CancellationToken ct) {

        // 当前task的完成情况
        boolean completed;
        // 创建一个新的task
        final TaskCompletionSource<TContinuationResult> tcs = new TaskCompletionSource<>();
        //
        synchronized (lock) {
            // 当前task 完成情况，当前task未完成，则添加task
            completed = this.isCompleted();
            // 当前task未完成，添加task
            if (!completed) {
                //
                this.continuations.add(new Continuation<TResult, Void>() {
                    @Override
                    public Void then(Task<TResult> task) {
                        // 完成当前task后，执行
                        completeAfterTask(tcs, continuation, task, executor, ct);
                        return null;
                    }
                });
            }
        }
        if (completed) {
            //
            completeAfterTask(tcs, continuation, this, executor, ct);
        }
        //
        return tcs.getTask();
    }

    /**
     * Adds an asynchronous continuation to this task, returning a new task that completes after the
     * task returned by the continuation has completed.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
            Continuation<TResult, Task<TContinuationResult>> continuation) {
        return continueWithTask(continuation, IMMEDIATE_EXECUTOR, null);
    }

    /**
     * Adds an asynchronous continuation to this task, returning a new task that completes after the
     * task returned by the continuation has completed.
     */
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
            Continuation<TResult, Task<TContinuationResult>> continuation, CancellationToken ct) {
        return continueWithTask(continuation, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * 上一任务执行成功，才会执行该任务
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception} or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccess(
            final Continuation<TResult, TContinuationResult> continuation, Executor executor) {

        return onSuccess(continuation, executor, null);
    }

    /**
     * 非静态方法，必须通过一个task对象，才能运行
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception} or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccess(
            // Continuation 引用
            final Continuation<TResult, TContinuationResult> continuation,
            // 线程池
            Executor executor,
            // 任务取消情况的判断
            final CancellationToken ct) {
        // continueWithTask也是非静态方法，通过上一任务的task对象来运行
        return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
            @Override
            public Task<TContinuationResult> then(Task<TResult> task) {
                // 取消任务的判断
                if (ct != null && ct.isCancellationRequested()) {
                    return Task.cancelled();
                }
                // 任务是否出错的判断
                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                } else if (task.isCancelled()) {
                    return Task.cancelled();
                } else {
                    // 当前线程，执行该任务
                    return task.continueWith(continuation);
                }
            }
        }, executor);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccess(
            final Continuation<TResult, TContinuationResult> continuation) {
        return onSuccess(continuation, IMMEDIATE_EXECUTOR, null);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccess(
            final Continuation<TResult, TContinuationResult> continuation, CancellationToken ct) {
        return onSuccess(continuation, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
            final Continuation<TResult, Task<TContinuationResult>> continuation, Executor executor) {
        return onSuccessTask(continuation, executor, null);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
            final Continuation<TResult, Task<TContinuationResult>> continuation, Executor executor,
            final CancellationToken ct) {
        return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
            @Override
            public Task<TContinuationResult> then(Task<TResult> task) {
                if (ct != null && ct.isCancellationRequested()) {
                    return Task.cancelled();
                }

                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                } else if (task.isCancelled()) {
                    return Task.cancelled();
                } else {
                    return task.continueWithTask(continuation);
                }
            }
        }, executor);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
            final Continuation<TResult, Task<TContinuationResult>> continuation) {
        return onSuccessTask(continuation, IMMEDIATE_EXECUTOR);
    }

    /**
     * Runs a continuation when a task completes successfully, forwarding along
     * {@link Exception}s or cancellation.
     */
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
            final Continuation<TResult, Task<TContinuationResult>> continuation,
            CancellationToken ct) {
        return onSuccessTask(continuation, IMMEDIATE_EXECUTOR, ct);
    }

    /**
     * Handles the non-async (i.e. the continuation doesn't return a Task) continuation case, passing
     * the results of the given Task through to the given continuation and using the results of that
     * call to set the result of the TaskContinuationSource.
     *
     * @param tcs          The TaskContinuationSource that will be orchestrated by this call.
     * @param continuation The non-async continuation.  要执行的continuation任务
     * @param task         The task being completed.    上一次的运行结果task
     * @param executor     The executor to use when running the continuation (allowing the continuation to be
     *                     scheduled on a different thread). 用户设置的执行该任务的 线程池
     */
    private static <TContinuationResult, TResult> void completeImmediately(
            // Continuation的task封装
            final TaskCompletionSource<TContinuationResult> tcs,
            // Continuation引用
            final Continuation<TResult, TContinuationResult> continuation,
            // 上一次的运行结果
            final Task<TResult> task,
            // 线程池
            Executor executor,
            // 线程是否取消的判断
            final CancellationToken ct) {

        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // 如果任务被取消
                    if (ct != null && ct.isCancellationRequested()) {
                        tcs.setCancelled();
                        return;
                    }
                    //
                    try {
                        // 执行continuation任务
                        TContinuationResult result = continuation.then(task);
                        // 当前任务执行后的返回数据(通过该方法，继续执行下一任务)
                        tcs.setResult(result);
                    } catch (CancellationException e) {
                        tcs.setCancelled();
                    } catch (Exception e) {
                        tcs.setError(e);
                    }
                }
            });
        } catch (Exception e) {
            tcs.setError(new ExecutorException(e));
        }
    }

    /**
     * 完成当前task后，执行的方法
     * <p>
     * Handles the async (i.e. the continuation does return a Task) continuation case, passing the
     * results of the given Task through to the given continuation to get a new Task. The
     * TaskCompletionSource's results are only set when the new Task has completed, unwrapping the
     * results of the task returned by the continuation.
     *
     * @param tcs          The TaskContinuationSource that will be orchestrated by this call.
     * @param continuation The async continuation.
     * @param task         The task being completed.
     * @param executor     The executor to use when running the continuation (allowing the continuation to be
     *                     scheduled on a different thread).
     */
    private static <TContinuationResult, TResult> void completeAfterTask(
            // 新的task封装
            final TaskCompletionSource<TContinuationResult> tcs,
            // continuation引用
            final Continuation<TResult, Task<TContinuationResult>> continuation,
            // 上一task的运行结果
            final Task<TResult> task,
            // 线程池
            final Executor executor,
            // task是否已经被手动取消
            final CancellationToken ct) {
        try {
            //
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // 是否取消当前task的判断
                    if (ct != null && ct.isCancellationRequested()) {
                        tcs.setCancelled();
                        return;
                    }
                    //
                    try {
                        // 传入上次task的完成数据，执行 新构建continuation 的them方法
                        Task<TContinuationResult> result = continuation.then(task);
                        if (result == null) {
                            tcs.setResult(null);
                        } else {
                            //
                            result.continueWith(new Continuation<TContinuationResult, Void>() {
                                @Override
                                public Void then(Task<TContinuationResult> task) {
                                    // 判断任务是否被手动取消
                                    if (ct != null && ct.isCancellationRequested()) {
                                        tcs.setCancelled();
                                        return null;
                                    }
                                    // 是否取消的判断
                                    if (task.isCancelled()) {
                                        tcs.setCancelled();
                                    }
                                    // 是否出错的判断
                                    else if (task.isFaulted()) {
                                        tcs.setError(task.getError());
                                    } else {
                                        // 运行结果
                                        tcs.setResult(task.getResult());
                                    }
                                    return null;
                                }
                            });
                        }
                    } catch (CancellationException e) {
                        tcs.setCancelled();
                    } catch (Exception e) {
                        tcs.setError(e);
                    }
                }
            });
        } catch (Exception e) {
            tcs.setError(new ExecutorException(e));
        }
    }

    /**
     * 循环执行continuations任务
     * <p>
     * 问题: Continuation并没有区分执行线程呀?
     * 答：没一个Continuation又是一个封装的Continuation.then(),方法中将任务提交到的不同线程池
     */
    private void runContinuations() {
        synchronized (lock) {
            // 循环运行当前continuations列表任务
            for (Continuation<TResult, ?> continuation : continuations) {
                try {
                    // 执行Continuation任务
                    continuation.then(this);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            // 当前Task的continuations队列置空
            continuations = null;
        }
    }

    /**
     * 取消当前正在执行的任务
     * Sets the cancelled flag on the Task if the Task hasn't already been completed.
     */
    boolean trySetCancelled() {
        synchronized (lock) {
            if (complete) {
                return false;
            }
            // 任务完成
            complete = true;
            // 任务取消
            cancelled = true;
            //
            lock.notifyAll();
            // 循环执行continuations任务
            runContinuations();
            return true;
        }
    }

    /**
     * 当前task，执行结束后的返回数据赋值
     * Sets the result on the Task if the Task hasn't already been completed.
     */
    boolean trySetResult(TResult result) {

        synchronized (lock) {
            // 任务运行结束判断
            if (complete) {
                return false;
            }
            // 任务运行结束
            complete = true;
            // 任务结果赋值
            Task.this.result = result;
            // 通知lock锁被释放
            lock.notifyAll();
            // 循环执行continuations任务
            runContinuations();
            return true;
        }
    }

    /**
     * 设置错误
     * Sets the error on the Task if the Task hasn't already been completed.
     */
    boolean trySetError(Exception error) {
        synchronized (lock) {
            // 任务运行结束判断
            if (complete) {
                return false;
            }
            // 任务运行结束
            complete = true;
            // 错误赋值
            Task.this.error = error;
            // 标记Exception是暂未被观测到
            errorHasBeenObserved = false;
            // 通知lock锁被释放
            lock.notifyAll();
            // 循环执行continuations任务
            runContinuations();
            // 错误未被观测到，并且设置了错误未被观测到的通知者
            if (!errorHasBeenObserved && getUnobservedExceptionHandler() != null) {
                // 创建未被观测到错误的通知者
                unobservedErrorNotifier = new UnobservedErrorNotifier(this);
            }

            return true;
        }
    }

    private static Task<?> TASK_NULL = new Task<>(null);
    private static Task<Boolean> TASK_TRUE = new Task<>((Boolean) true);
    private static Task<Boolean> TASK_FALSE = new Task<>((Boolean) false);
    private static Task<?> TASK_CANCELLED = new Task(true);
}
