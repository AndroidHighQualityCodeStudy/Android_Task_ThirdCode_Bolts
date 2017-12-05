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

import java.io.Closeable;

/**
 * postDelay，但未执行的 远端ScheduledFuture
 * <p/>
 * 会通过该类，进行一个个取消 关闭
 * <p/>
 * 每一ScheduledFuture都会通过Runnable.runAction取消
 * <p/>
 * Represents a callback delegate that has been registered with a {@link CancellationToken}.
 *
 * @see CancellationToken#register(Runnable)
 */
public class CancellationTokenRegistration implements Closeable {
    private static final String TAG = CancellationTokenRegistration.class.getSimpleName();

    private final Object lock = new Object();

    // 这里保留CancellationTokenSource的引用，为了是取消CancellationTokenRegistration的注册
    private CancellationTokenSource tokenSource;
    // 取消 远端任务的Runnable
    private Runnable action;

    // postDelay，但未执行的 远端ScheduledFuture关闭的标识
    private boolean closed;

    /**
     * @param tokenSource
     * @param action
     */
    CancellationTokenRegistration(CancellationTokenSource tokenSource, Runnable action) {
        this.tokenSource = tokenSource;
        this.action = action;
    }

    /**
     * runAction方法来调用
     * <p/>
     * Unregisters the callback runnable from the cancellation token.
     */
    @Override
    public void close() {
        LogUtils.d(TAG, "---close---");
        synchronized (lock) {
            if (closed) {
                return;
            }
            // 关闭 CancellationTokenRegistration
            closed = true;
            // 取消注册
            tokenSource.unregister(this);

            tokenSource = null;
            action = null;
        }
    }

    /**
     * 取消 postDelay尚未执行，但需要cancel的远程任务
     */
    void runAction() {
        //
        synchronized (lock) {
            // 判断是否关闭
            throwIfClosed();
            // 取消 远程ScheduledFuture
            action.run();
            // 显示调用close
            close();
        }
    }

    /**
     * 抛出异常
     */
    private void throwIfClosed() {
        if (closed) {
            throw new IllegalStateException("Object already closed");
        }
    }

}
