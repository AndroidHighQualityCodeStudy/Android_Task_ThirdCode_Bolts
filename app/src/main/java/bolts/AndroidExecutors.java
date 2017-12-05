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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Android的UI线程与cache线程池的封装类
 * CORE_POOL_SIZE = CPU_COUNT + 1
 * MAX_POOL_SIZE = CPU_COUNT * 2 + 1
 * <p/>
 * <p/>
 * This was created because the helper methods in {@link java.util.concurrent.Executors} do not work
 * as people would normally expect.
 * <p/>
 * Normally, you would think that a cached thread pool would create new threads when necessary,
 * queue them when the pool is full, and kill threads when they've been inactive for a certain
 * period of time. This is not how {@link java.util.concurrent.Executors#newCachedThreadPool()}
 * works.
 * <p/>
 * Instead, {@link java.util.concurrent.Executors#newCachedThreadPool()} executes all tasks on
 * a new or cached thread immediately because corePoolSize is 0, SynchronousQueue is a queue with
 * size 0 and maxPoolSize is Integer.MAX_VALUE. This is dangerous because it can create an unchecked
 * amount of threads.
 */
final class AndroidExecutors {

    private static final AndroidExecutors INSTANCE = new AndroidExecutors();

    private final Executor uiThread;

    private AndroidExecutors() {
        uiThread = new UIThreadExecutor();
    }

    /**
     * Nexus 5: Quad-Core
     * Moto X: Dual-Core
     * <p/>
     * AsyncTask:
     * CORE_POOL_SIZE = CPU_COUNT + 1
     * MAX_POOL_SIZE = CPU_COUNT * 2 + 1
     * <p/>
     * https://github.com/android/platform_frameworks_base/commit/719c44e03b97e850a46136ba336d729f5fbd1f47
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // cpu核心数+1
    static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    // cpu核心数*2+1
    static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    // 存活时间
    static final long KEEP_ALIVE_TIME = 1L;

    /**
     * Creates a proper Cached Thread Pool. Tasks will reuse cached threads if available
     * or create new threads until the core pool is full. tasks will then be queued. If an
     * task cannot be queued, a new thread will be created unless this would exceed max pool
     * size, then the task will be rejected. Threads will time out after 1 second.
     * <p/>
     * Core thread timeout is only available on android-9+.
     *
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        // 允许核心线程超时
        allowCoreThreadTimeout(executor, true);

        return executor;
    }

    /**
     * Creates a proper Cached Thread Pool. Tasks will reuse cached threads if available
     * or create new threads until the core pool is full. tasks will then be queued. If an
     * task cannot be queued, a new thread will be created unless this would exceed max pool
     * size, then the task will be rejected. Threads will time out after 1 second.
     * <p/>
     * Core thread timeout is only available on android-9+.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        //
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                threadFactory);
        // 允许核心线程超时
        allowCoreThreadTimeout(executor, true);
        //
        return executor;
    }

    /**
     * Compatibility helper function for
     * {@link ThreadPoolExecutor#allowCoreThreadTimeOut(boolean)}
     * <p/>
     * Only available on android-9+.
     * 允许核心线程超时，API9以上
     *
     * @param executor the {@link ThreadPoolExecutor}
     * @param value    true if should time out, else false
     */
    @SuppressLint("NewApi")
    public static void allowCoreThreadTimeout(ThreadPoolExecutor executor, boolean value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            executor.allowCoreThreadTimeOut(value);
        }
    }

    /**
     * An {@link Executor} that executes tasks on the UI thread.
     * 获取UI线程池
     */
    public static Executor uiThread() {
        return INSTANCE.uiThread;
    }

    /**
     * An {@link Executor} that runs tasks on the UI thread.
     * 任务post到UI线程中
     */
    private static class UIThreadExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            new Handler(Looper.getMainLooper()).post(command);
        }
    }
}
