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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 取消任务的入口
 * <p>
 * Signals to a {@link CancellationToken} that it should be canceled. To create a
 * {@code CancellationToken} first create a {@code CancellationTokenSource} then call
 * {@link #getToken()} to retrieve the token for the source.
 *
 * @see CancellationToken
 * @see CancellationTokenSource#getToken()
 */
public class CancellationTokenSource implements Closeable {

    //---------线程池---------
    // postDelay时，用的是该线程池executor.schedule可返回一个ScheduledFuture用于取消
    private final ScheduledExecutorService executor = BoltsExecutors.scheduled();

    //-----------------
    //变量锁
    private final Object lock = new Object();
    //-----------------
    // 这个里边存放的是postDelay尚未执行，但需要cancel的远程任务
    private final List<CancellationTokenRegistration> registrations = new ArrayList<>();
    // delay一段时间，取消任务的方法返回的远程
    private ScheduledFuture<?> scheduledCancellation;
    // 外界，调用cancel()取消任务后，cancellationRequested置true
    private boolean cancellationRequested;
    // 这个什么时候关闭，目前尚不清楚??????????????????????
    private boolean closed;

    /**
     * Create a new {@code CancellationTokenSource}.
     */
    public CancellationTokenSource() {
    }

    /**
     * 任务是否取消的标识
     *
     * @return {@code true} if cancellation has been requested for this {@code CancellationTokenSource}.
     */
    public boolean isCancellationRequested() {
        synchronized (lock) {
            throwIfClosed();
            return cancellationRequested;
        }
    }

    /**
     * 获取CancellationToken
     *
     * @return the token that can be passed to asynchronous method to control cancellation.
     */
    public CancellationToken getToken() {
        synchronized (lock) {
            throwIfClosed();
            return new CancellationToken(this);
        }
    }

    /**
     * 暴露在外的，取消任务的接口
     * (这个一般是主线程调用吧，就当是主线程调用来读)
     * <p>
     * 用法
     * CancellationTokenSource cts = new CancellationTokenSource();
     * Task<Boolean> stringTask = runOnBackgroundThread(cts.getToken());
     * cts.cancel();
     * <p>
     * Cancels the token if it has not already been cancelled.
     */
    public void cancel() {
        List<CancellationTokenRegistration> registrations;
        // 主线程获取对象锁
        synchronized (lock) {
            // 如果已经关闭，抛出异常
            throwIfClosed();
            // 如果任务已取消，返回
            if (cancellationRequested) {
                return;
            }
            // 取消delay一段时间，取消任务的方法返回的远程
            cancelScheduledCancellation();
            // 取消任务
            cancellationRequested = true;
            // 取消 postDelay尚未执行，但需要cancel的远程任务
            registrations = new ArrayList<>(this.registrations);
        }
        // 取消 postDelay尚未执行，但需要cancel的远程任务
        notifyListeners(registrations);
    }

    /**
     * delay一段时间后，取消任务的接口
     * Schedules a cancel operation on this {@code CancellationTokenSource} after the specified number of milliseconds.
     *
     * @param delay The number of milliseconds to wait before completing the returned task. If delay is {@code 0}
     *              the cancel is executed immediately. If delay is {@code -1} any scheduled cancellation is stopped.
     */
    public void cancelAfter(final long delay) {
        cancelAfter(delay, TimeUnit.MILLISECONDS);
    }

    /**
     * delay一段时间后，取消任务的接口
     *
     * @param delay
     * @param timeUnit 这里给出的时间是毫秒
     */
    private void cancelAfter(long delay, TimeUnit timeUnit) {
        if (delay < -1) {
            throw new IllegalArgumentException("Delay must be >= -1");
        }
        // 如果delay的时间为0
        if (delay == 0) {
            // 取消任务
            cancel();
            return;
        }
        // 同步执行
        synchronized (lock) {
            // 如果请求已取消
            if (cancellationRequested) {
                return;
            }
            // 取消delay一段时间，取消任务的方法返回的远程
            cancelScheduledCancellation();
            // delay一段时间 取消任务
            if (delay != -1) {
                scheduledCancellation = executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        // 这里是异步线程，同步锁
                        synchronized (lock) {
                            scheduledCancellation = null;
                        }
                        // 取消任务
                        cancel();
                    }
                }, delay, timeUnit);
            }
        }
    }

    /**
     * 目前没有发现有可调用该类的地方，暂时忽略?????????????????????
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            cancelScheduledCancellation();
            for (CancellationTokenRegistration registration : registrations) {
                registration.close();
            }
            registrations.clear();
            closed = true;
        }
    }

    /**
     * 注册一个Runnable 用与取消ScheduledFuture
     *
     * @param action
     * @return
     */
    CancellationTokenRegistration register(Runnable action) {
        CancellationTokenRegistration ctr;
        synchronized (lock) {
            throwIfClosed();
            // 创建一个CancellationTokenRegistration
            ctr = new CancellationTokenRegistration(this, action);
            // 如果已经调用过cancel方法
            if (cancellationRequested) {
                // 直接取消
                ctr.runAction();
            } else {
                // 添加到CancellationTokenRegistration列表
                registrations.add(ctr);
            }
        }
        return ctr;
    }

    /**
     * @throws CancellationException if this token has had cancellation requested.
     *                               May be used to stop execution of a thread or runnable.
     */
    void throwIfCancellationRequested() throws CancellationException {
        synchronized (lock) {
            throwIfClosed();
            if (cancellationRequested) {
                throw new CancellationException();
            }
        }
    }

    /**
     * 取消Runnable注册( Runnable用与取消ScheduledFuture )
     *
     * @param registration
     */
    void unregister(CancellationTokenRegistration registration) {
        synchronized (lock) {
            throwIfClosed();
            registrations.remove(registration);
        }
    }

    /**
     * 取消 postDelay尚未执行，但需要cancel的远程任务
     * This method makes no attempt to perform any synchronization or state checks itself and once
     * invoked will notify all runnables unconditionally. As such if you require the notification event
     * to be synchronized with state changes you should provide external synchronization.
     * If this is invoked without external synchronization there is a probability the token becomes
     * cancelled concurrently.
     *
     * @param registrations
     */
    private void notifyListeners(List<CancellationTokenRegistration> registrations) {
        for (CancellationTokenRegistration registration : registrations) {
            registration.runAction();
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s@%s[cancellationRequested=%s]",
                getClass().getName(),
                Integer.toHexString(hashCode()),
                Boolean.toString(isCancellationRequested()));
    }

    /**
     * 判断是否已经停止
     * This method makes no attempt to perform any synchronization itself - you should ensure
     * accesses to this method are synchronized if you want to ensure correct behaviour in the
     * face of a concurrent invocation of the close method.
     */
    private void throwIfClosed() {
        if (closed) {
            throw new IllegalStateException("Object already closed");
        }
    }

    /**
     * 取消delay一段时间，取消任务的方法返回的远程
     * Performs no synchronization.
     */
    private void cancelScheduledCancellation() {
        if (scheduledCancellation != null) {
            scheduledCancellation.cancel(true);
            scheduledCancellation = null;
        }
    }
}
