package com.example.walletkeep_demo.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程级备份任务串行执行器。
 *
 * <p>备份/恢复/预览/清空共享同一个线程与 busy 标记，避免 Activity 旋转后新旧实例
 * 各起一个执行器导致并发写库、并发删票据或主线程等待写锁。</p>
 */
public final class BackupTaskRunner {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private BackupTaskRunner() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /**
     * 原子地占用任务槽；返回 false 表示已有任务在执行。
     */
    public static boolean tryStart() {
        return BUSY.compareAndSet(false, true);
    }

    public static void finish() {
        BUSY.set(false);
    }

    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }
}
