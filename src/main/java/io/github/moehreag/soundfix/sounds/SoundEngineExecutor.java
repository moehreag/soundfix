package io.github.moehreag.soundfix.sounds;

import java.util.concurrent.locks.LockSupport;

import io.github.moehreag.soundfix.util.BlockableEventLoop;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The SoundEngineExecutor class is responsible for executing sound-related tasks in a separate thread.
 * <p>
 * It extends the BlockableEventLoop class, providing an event loop for managing and executing tasks.
 */
@Environment(EnvType.CLIENT)
public class SoundEngineExecutor extends BlockableEventLoop<Runnable> {
	private Thread thread = this.createThread();
	private volatile boolean shutdown;

	public SoundEngineExecutor() {
		super("Sound executor");
	}

	/**
	 * Creates and starts a new thread for executing sound-related tasks.
	 * <p>
	 * @return The created thread
	 */
	private Thread createThread() {
		Thread thread = new Thread(this::run);
		thread.setDaemon(true);
		thread.setName("Sound engine");
		thread.start();
		return thread;
	}

	@Override
	public Runnable wrapRunnable(Runnable runnable) {
		return runnable;
	}

	@Override
	protected boolean shouldRun(Runnable runnable) {
		return !this.shutdown;
	}

	@Override
	protected Thread getRunningThread() {
		return this.thread;
	}

	/**
	 * The main run loop of the SoundEngineExecutor.
	 * It continuously blocks until the shutdown state is true.
	 */
	private void run() {
		while (!this.shutdown) {
			this.managedBlock(() -> this.shutdown);
		}
	}

	@Override
	protected void waitForTasks() {
		LockSupport.park("waiting for tasks");
	}

	/**
	 * Flushes the SoundEngineExecutor by interrupting the thread, joining the thread, dropping all pending tasks, and recreating the thread.
	 * It sets the shutdown state to false to allow new tasks to be scheduled.
	 */
	public void flush() {
		this.shutdown = true;
		this.thread.interrupt();

		try {
			this.thread.join();
		} catch (InterruptedException var2) {
			Thread.currentThread().interrupt();
		}

		this.dropAllTasks();
		this.shutdown = false;
		this.thread = this.createThread();
	}
}
