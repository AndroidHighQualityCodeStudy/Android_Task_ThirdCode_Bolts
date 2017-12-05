package com.toast.demo;

import android.app.Activity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import bolts.CancellationToken;
import bolts.CancellationTokenSource;
import bolts.Continuation;
import bolts.Task;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CancellationTokenSource cts = new CancellationTokenSource();
        //
        synchronousTask(cts.getToken());
        //
        cts.cancel();


    }

    //###############################第1部分###########################

    /**
     * 运行在后台线程中
     */
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

    /**
     * 运行在当前线程中
     */
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

    /**
     * 运行在UI线程中
     */
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

    /**
     * 任务顺序执行
     */
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

    /**
     * 多任务并行
     */
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
    /**
     * 自定义线程池
     */
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

    /**
     * 取消任务
     */
    public void cancelTask() {
        CancellationTokenSource cts = new CancellationTokenSource();
        Task<Boolean> stringTask = runOnBackgroundThread(cts.getToken());
        cts.cancel();
    }

    /**
     * 运行在后台线程中
     */
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

}
