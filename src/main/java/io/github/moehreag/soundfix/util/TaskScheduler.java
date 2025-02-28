package io.github.moehreag.soundfix.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface TaskScheduler<R extends Runnable> extends AutoCloseable {
	String name();

	void schedule(R task);

	default void close() {
	}

	R wrapRunnable(Runnable runnable);

	default <Source> CompletableFuture<Source> scheduleWithResult(Consumer<CompletableFuture<Source>> resultConsumer) {
		CompletableFuture<Source> completableFuture = new CompletableFuture<>();
		this.schedule(this.wrapRunnable(() -> resultConsumer.accept(completableFuture)));
		return completableFuture;
	}

	static TaskScheduler<Runnable> wrapExecutor(String name, Executor executor) {
		return new TaskScheduler<Runnable>() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void schedule(Runnable task) {
				executor.execute(task);
			}

			@Override
			public Runnable wrapRunnable(Runnable runnable) {
				return runnable;
			}

			public String toString() {
				return name;
			}
		};
	}
}
