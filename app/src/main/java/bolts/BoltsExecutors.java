package bolts;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Collection of {@link Executor}s to use in conjunction with {@link Task}.
 * <p/>
 * background异步线程
 * <p/>
 * scheduled 列表单线程
 * <p/>
 * immediate 当前线程顺序执行的线程
 * <p/>
 * 封装类
 */
final class BoltsExecutors {

    //
    private static final BoltsExecutors INSTANCE = new BoltsExecutors();

    /**
     * @return 判断运行环境是否为android(也可能单纯的为java环境)
     */
    private static boolean isAndroidRuntime() {
        String javaRuntimeName = System.getProperty("java.runtime.name");
        if (javaRuntimeName == null) {
            return false;
        }
        return javaRuntimeName.toLowerCase(Locale.US).contains("android");
    }

    /**
     * 线程池
     */
    // 异步线程(若为android环境，则核心数为cpu*2+1)
    private final ExecutorService background;
    // 单线程线程池
    private final ScheduledExecutorService scheduled;
    // 在当前线程执行任务，如果任务的深度超过最大深度，则放到background线程中执行
    private final Executor immediate;

    /**
     * 构造线程池
     */
    private BoltsExecutors() {
        // ---判断是否为Android，返回不同的线程池---
        background = !isAndroidRuntime()
                ? Executors.newCachedThreadPool()
                : AndroidExecutors.newCachedThreadPool();
        // ---单线程线程池---
        scheduled = Executors.newSingleThreadScheduledExecutor();
        // ------在当前线程执行任务，如果任务的深度超过最大深度，则放到background线程中执行-------
        immediate = new ImmediateExecutor();
    }

    /**
     * An {@link Executor} that executes tasks in parallel.
     * 后台异步线程
     */
    public static ExecutorService background() {
        return INSTANCE.background;
    }

    /*
     * package
     * 单线程线程池
     */
    static ScheduledExecutorService scheduled() {
        return INSTANCE.scheduled;
    }

    /**
     * An {@link Executor} that executes tasks in the current thread unless
     * the stack runs too deep, at which point it will delegate to {@link BoltsExecutors#background}
     * in order to trim the stack.
     */
    static Executor immediate() {
        return INSTANCE.immediate;
    }

    /**
     * An {@link Executor} that runs a runnable inline (rather than scheduling it
     * on a thread pool) as long as the recursion depth is less than MAX_DEPTH. If the executor has
     * recursed too deeply, it will instead delegate to the {@link Task#BACKGROUND_EXECUTOR} in order
     * to trim the stack.
     * 运行在当前线程中，如果深度超过最大深度，则放在background线程中执行
     */
    private static class ImmediateExecutor implements Executor {
        // 最大深度
        private static final int MAX_DEPTH = 15;
        // 最大深度的记录(不同线程使用该对象，并不影响该变量在相应线程中的值)
        private ThreadLocal<Integer> executionDepth = new ThreadLocal<>();

        /**
         * Increments the depth.
         * 增加深度
         *
         * @return the new depth value.
         */
        private int incrementDepth() {
            Integer oldDepth = executionDepth.get();
            if (oldDepth == null) {
                oldDepth = 0;
            }
            int newDepth = oldDepth + 1;
            executionDepth.set(newDepth);
            return newDepth;
        }

        /**
         * Decrements the depth.
         * 减小深度
         *
         * @return the new depth value.
         */
        private int decrementDepth() {
            Integer oldDepth = executionDepth.get();
            if (oldDepth == null) {
                oldDepth = 0;
            }
            int newDepth = oldDepth - 1;
            if (newDepth == 0) {
                executionDepth.remove();
            } else {
                executionDepth.set(newDepth);
            }
            return newDepth;
        }

        @Override
        public void execute(Runnable command) {
            // 增加深度
            int depth = incrementDepth();
            try {
                // 如果深度小于最大深度
                if (depth <= MAX_DEPTH) {
                    // 当前线程中执行Runnable
                    command.run();
                }
                // 超过指定深度，则运行在background线程中
                else {
                    BoltsExecutors.background().execute(command);
                }
            } finally {
                // 减小深度
                decrementDepth();
            }
        }
    }
}
