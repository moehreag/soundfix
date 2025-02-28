package io.github.moehreag.soundfix.util;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.google.common.collect.Queues;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BlockableEventLoop<R extends Runnable> implements TaskScheduler<R>, Executor {
	public static final long BLOCK_TIME_NANOS = 100000L;
	private final String name;
	private static final Logger LOGGER = LogManager.getLogger("EventLoop");
	private final Queue<R> pendingRunnables = Queues.newConcurrentLinkedQueue();
	private int blockingCount;

	protected BlockableEventLoop(String name) {
		this.name = name;
	}

	protected abstract boolean shouldRun(R runnable);

	public boolean isSameThread() {
		return Thread.currentThread() == this.getRunningThread();
	}

	protected abstract Thread getRunningThread();

	protected boolean scheduleExecutables() {
		return !this.isSameThread();
	}

	public int getPendingTasksCount() {
		return this.pendingRunnables.size();
	}

	@Override
	public String name() {
		return this.name;
	}

	public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
		return this.scheduleExecutables() ? CompletableFuture.supplyAsync(supplier, this) : CompletableFuture.completedFuture(supplier.get());
	}

	private CompletableFuture<Void> submitAsync(Runnable task) {
		return CompletableFuture.supplyAsync(() -> {
			task.run();
			return null;
		}, this);
	}

	public CompletableFuture<Void> submit(Runnable task) {
		if (this.scheduleExecutables()) {
			return this.submitAsync(task);
		} else {
			task.run();
			return CompletableFuture.completedFuture(null);
		}
	}

	public void executeBlocking(Runnable task) {
		if (!this.isSameThread()) {
			this.submitAsync(task).join();
		} else {
			task.run();
		}
	}

	@Override
	public void schedule(R task) {
		this.pendingRunnables.add(task);
		LockSupport.unpark(this.getRunningThread());
	}

	public void execute(Runnable runnable) {
		if (this.scheduleExecutables()) {
			this.schedule(this.wrapRunnable(runnable));
		} else {
			runnable.run();
		}
	}

	public void executeIfPossible(Runnable task) {
		this.execute(task);
	}

	protected void dropAllTasks() {
		this.pendingRunnables.clear();
	}

	protected void runAllTasks() {
		while (this.pollTask()) {
		}
	}

	public boolean pollTask() {
		R runnable = this.pendingRunnables.peek();
		if (runnable == null) {
			return false;
		} else if (this.blockingCount == 0 && !this.shouldRun(runnable)) {
			return false;
		} else {
			this.doRunTask(this.pendingRunnables.remove());
			return true;
		}
	}

	/**
	 * Drive the executor until the given BooleanSupplier returns true
	 */
	public void managedBlock(BooleanSupplier isDone) {
		this.blockingCount++;

		try {
			while (!isDone.getAsBoolean()) {
				if (!this.pollTask()) {
					this.waitForTasks();
				}
			}
		} finally {
			this.blockingCount--;
		}
	}

	protected void waitForTasks() {
		Thread.yield();
		LockSupport.parkNanos("waiting for tasks", BLOCK_TIME_NANOS);
	}

	protected void doRunTask(R task) {
		task.run();
	}
}
