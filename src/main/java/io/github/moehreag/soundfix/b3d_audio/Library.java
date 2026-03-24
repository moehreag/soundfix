package io.github.moehreag.soundfix.b3d_audio;

import java.nio.IntBuffer;
import java.util.*;

import com.google.common.collect.Sets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class Library {
	static final Logger LOGGER = LogManager.getLogger("Library");
	static final String NO_DEVICE_NAME = "(None)";
	private static final int NO_DEVICE = 0;
	private static final int DEFAULT_CHANNEL_COUNT = 30;
	private long currentDevice;
	private long context;
	private boolean supportsDisconnections;
	@Nullable
	private String defaultDeviceName;
	private String currentDeviceName = NO_DEVICE_NAME;
	private static final Library.ChannelPool EMPTY = new Library.ChannelPool() {
		@Nullable
		@Override
		public Channel acquire() {
			return null;
		}

		@Override
		public boolean release(Channel channel) {
			return false;
		}

		@Override
		public void cleanup() {
		}

		@Override
		public int getMaxCount() {
			return 0;
		}

		@Override
		public int getUsedCount() {
			return 0;
		}
	};
	private Library.ChannelPool staticChannels = EMPTY;
	private Library.ChannelPool streamingChannels = EMPTY;
	private final Listener listener = new Listener();

	public Library() {
		this.defaultDeviceName = getDefaultDeviceName();
	}

	/**
	 * Initializes the OpenAL device and context.
	 *
	 * @param deviceSpecifier A string specifying the name of the audio device to use, or null to use the default device.
	 * @param enableHrtf      Whether to enable HRTF (head-related transfer function) for spatial audio.
	 * @throws IllegalStateException if an error occurs during initialization.
	 */
	public void init(@Nullable String deviceSpecifier, boolean enableHrtf) {
		currentDeviceName = NO_DEVICE_NAME;
		this.currentDevice = openDeviceOrFallback(deviceSpecifier, getDefaultDeviceName());
		currentDeviceName = queryDeviceName(this.currentDevice);
		this.supportsDisconnections = false;
		ALCCapabilities aLCCapabilities = ALC.createCapabilities(this.currentDevice);
		if (OpenAlUtil.checkALCError(this.currentDevice, "Get capabilities")) {
			throw new IllegalStateException("Failed to get OpenAL capabilities");
		} else if (!aLCCapabilities.OpenALC11) {
			throw new IllegalStateException("OpenAL 1.1 not supported");
		} else {
			this.setHrtf(aLCCapabilities.ALC_SOFT_HRTF && enableHrtf);

			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				IntBuffer intBuffer = memoryStack.callocInt(3).put(6554).put(1).put(0).flip();
				this.context = ALC10.alcCreateContext(this.currentDevice, intBuffer);
			}

			if (OpenAlUtil.checkALCError(this.currentDevice, "Create context")) {
				throw new IllegalStateException("Unable to create OpenAL context");
			} else {
				ALC10.alcMakeContextCurrent(this.context);
				int i = this.getChannelCount();
				int j = MathHelper.clamp((int) Math.sqrt((float) i), 2, 8);
				int k = MathHelper.clamp(i - j, 8, 255);
				this.staticChannels = new Library.CountingChannelPool(k);
				this.streamingChannels = new Library.CountingChannelPool(j);
				ALCapabilities aLCapabilities = AL.createCapabilities(aLCCapabilities);
				OpenAlUtil.checkALError("Initialization");
				if (!aLCapabilities.AL_EXT_source_distance_model) {
					throw new IllegalStateException("AL_EXT_source_distance_model is not supported");
				} else {
					AL10.alEnable(512);
					if (!aLCapabilities.AL_EXT_LINEAR_DISTANCE) {
						throw new IllegalStateException("AL_EXT_LINEAR_DISTANCE is not supported");
					} else {
						OpenAlUtil.checkALError("Enable per-source distance models");
						LOGGER.info("OpenAL initialized on device {}", new Object[]{this.currentDeviceName()});
						this.supportsDisconnections = ALC10.alcIsExtensionPresent(this.currentDevice, "ALC_EXT_disconnect");
					}
				}
			}
		}
	}

	/**
	 * Sets the HRTF (head-related transfer function) for spatial audio, if it is supported by the current device.
	 *
	 * @param enableHrtf Whether to enable HRTF.
	 */
	private void setHrtf(boolean enableHrtf) {
		int i = ALC10.alcGetInteger(this.currentDevice, 6548);
		if (i > 0) {
			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				IntBuffer intBuffer = memoryStack.callocInt(10).put(6546).put(enableHrtf ? 1 : 0).put(6550).put(0).put(0).flip();
				if (!SOFTHRTF.alcResetDeviceSOFT(this.currentDevice, intBuffer)) {
					LOGGER.warn("Failed to reset device: {}", new Object[]{ALC10.alcGetString(this.currentDevice, ALC10.alcGetError(this.currentDevice))});
				}
			}
		}
	}

	/**
	 * {@return the number of channels available for audio playback}
	 */
	private int getChannelCount() {
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			int i = ALC10.alcGetInteger(this.currentDevice, 4098);
			if (OpenAlUtil.checkALCError(this.currentDevice, "Get attributes size")) {
				throw new IllegalStateException("Failed to get OpenAL attributes");
			}

			IntBuffer intBuffer = memoryStack.mallocInt(i);
			ALC10.alcGetIntegerv(this.currentDevice, 4099, intBuffer);
			if (OpenAlUtil.checkALCError(this.currentDevice, "Get attributes")) {
				throw new IllegalStateException("Failed to get OpenAL attributes");
			}

			int j = 0;

			while (j < i) {
				int k = intBuffer.get(j++);
				if (k == 0) {
					break;
				}

				int l = intBuffer.get(j++);
				if (k == 4112) {
					return l;
				}
			}
		}

		return DEFAULT_CHANNEL_COUNT;
	}

	/**
	 * {@return the name of the default audio device, or {@code null} if it cannot be determined}
	 */
	public String currentDeviceName() {
		return currentDeviceName;
	}

	/**
	 * Checks if the default audio device has changed since the last time this method was called.
	 * <p>
	 * If the default device has changed, updates the stored default device name accordingly.
	 *
	 * @return {@code true} if the default device has changed since the last time this method was called, {@code false} otherwise
	 */
	public synchronized boolean hasDefaultDeviceChanged() {
		String string = getDefaultDeviceName();
		if (Objects.equals(this.defaultDeviceName, string)) {
			return false;
		} else {
			this.defaultDeviceName = string;
			return true;
		}
	}

	public static String getDefaultDeviceName() {
		if (!ALC10.alcIsExtensionPresent(0L, "ALC_ENUMERATE_ALL_EXT")) {
			return null;
		} else {
			ALUtil.getStringList(0L, 4115);
			return ALC10.alcGetString(0L, 4114);
		}
	}

	private static String queryDeviceName(final long deviceId) {
		String name = ALC10.alcGetString(deviceId, 4115);
		if (name == null) {
			name = ALC10.alcGetString(deviceId, 4101);
		}

		if (name == null) {
			name = "Unknown (0x" + HexFormat.of().toHexDigits(deviceId) + ")";
		}

		return name;
	}

	private static long openDeviceOrFallback(@Nullable String preferredDevice, String systemDefault) {
		OptionalLong optionalLong = OptionalLong.empty();
		if (preferredDevice != null) {
			optionalLong = tryOpenDevice(preferredDevice);
		}

		if (optionalLong.isEmpty()) {
			optionalLong = tryOpenDevice(systemDefault);
		}

		if (optionalLong.isEmpty()) {
			optionalLong = tryOpenDevice(null);
		}

		if (optionalLong.isEmpty()) {
			throw new IllegalStateException("Failed to open OpenAL device");
		} else {
			return optionalLong.getAsLong();
		}
	}

	/**
	 * Attempts to open the specified audio device.
	 *
	 * @param deviceSpecifier A string specifying the name of the audio device to open, or null to use the default device.
	 * @return an {@linkplain OptionalLong} containing the handle of the opened device if successful, or empty if the device could not be opened
	 */
	private static OptionalLong tryOpenDevice(@Nullable String deviceSpecifier) {
		long l = ALC10.alcOpenDevice(deviceSpecifier);
		return l != 0L && !OpenAlUtil.checkALCError(l, "Open device") ? OptionalLong.of(l) : OptionalLong.empty();
	}

	/**
	 * Cleans up all resources used by the library.
	 */
	public void cleanup() {
		this.staticChannels.cleanup();
		this.streamingChannels.cleanup();
		ALC10.alcDestroyContext(this.context);
		if (this.currentDevice != 0L) {
			ALC10.alcCloseDevice(this.currentDevice);
		}
	}

	public Listener getListener() {
		return this.listener;
	}

	/**
	 * Acquires a sound channel based on the given mode.
	 */
	@Nullable
	public Channel acquireChannel(Library.Pool pool) {
		return (pool == Library.Pool.STREAMING ? this.streamingChannels : this.staticChannels).acquire();
	}

	/**
	 * Releases a channel.
	 *
	 * @param channel The channel to release.
	 */
	public void releaseChannel(Channel channel) {
		if (!this.staticChannels.release(channel) && !this.streamingChannels.release(channel)) {
			throw new IllegalStateException("Tried to release unknown channel");
		}
	}

	public String getDebugString() {
		return String.format(
			Locale.ROOT,
			"Sounds: %d/%d + %d/%d",
			this.staticChannels.getUsedCount(),
			this.staticChannels.getMaxCount(),
			this.streamingChannels.getUsedCount(),
			this.streamingChannels.getMaxCount()
		);
	}

	/**
	 * {@return A list of strings representing the names of available sound devices, or an empty list if no devices are available.}
	 */
	public List<String> getAvailableSoundDevices() {
		List<String> list = ALUtil.getStringList(0L, 4115);
		return list == null ? Collections.emptyList() : list;
	}

	public boolean isCurrentDeviceDisconnected() {
		return this.supportsDisconnections && ALC11.alcGetInteger(this.currentDevice, 787) == 0;
	}

	@Environment(EnvType.CLIENT)
	interface ChannelPool {
		@Nullable
		Channel acquire();

		boolean release(Channel channel);

		void cleanup();

		int getMaxCount();

		int getUsedCount();
	}

	@Environment(EnvType.CLIENT)
	static class CountingChannelPool implements Library.ChannelPool {
		private final int limit;
		private final Set<Channel> activeChannels = Sets.newIdentityHashSet();

		public CountingChannelPool(int limit) {
			this.limit = limit;
		}

		@Nullable
		@Override
		public Channel acquire() {
			if (this.activeChannels.size() >= this.limit) {
				/*if (SharedConstants.IS_RUNNING_IN_IDE) {
					Library.LOGGER.warn("Maximum sound pool size {} reached", this.limit);
				}*/

				return null;
			} else {
				Channel channel = Channel.create();
				if (channel != null) {
					this.activeChannels.add(channel);
				}

				return channel;
			}
		}

		@Override
		public boolean release(Channel channel) {
			if (!this.activeChannels.remove(channel)) {
				return false;
			} else {
				channel.destroy();
				return true;
			}
		}

		@Override
		public void cleanup() {
			this.activeChannels.forEach(Channel::destroy);
			this.activeChannels.clear();
		}

		@Override
		public int getMaxCount() {
			return this.limit;
		}

		@Override
		public int getUsedCount() {
			return this.activeChannels.size();
		}
	}

	@Environment(EnvType.CLIENT)
	public enum Pool {
		STATIC,
		STREAMING
	}
}
