package io.github.moehreag.soundfix.sounds;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.collect.*;
import io.github.moehreag.soundfix.SoundFix;
import io.github.moehreag.soundfix.b3d_audio.Channel;
import io.github.moehreag.soundfix.b3d_audio.Library;
import io.github.moehreag.soundfix.b3d_audio.Listener;
import io.github.moehreag.soundfix.b3d_audio.ListenerTransform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.instance.TickableSoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code SoundEngine} class handles the management and playback of sounds in the game.
 */
@Environment(EnvType.CLIENT)
public class SoundEngine {
	/**
	 * The marker used for logging
	 */
	private static final Marker MARKER = MarkerManager.getMarker("SOUNDS");
	private static final Logger LOGGER = LogManager.getLogger("SoundEngine");
	private static final float PITCH_MIN = 0.5F;
	private static final float PITCH_MAX = 2.0F;
	private static final float VOLUME_MIN = 0.0F;
	private static final float VOLUME_MAX = 1.0F;
	private static final int MIN_SOURCE_LIFETIME = 20;
	/**
	 * A set of resource locations for which a missing sound warning has been issued
	 */
	private static final Set<Identifier> ONLY_WARN_ONCE = Sets.newHashSet();
	/**
	 * The default interval in milliseconds for checking the audio device state
	 */
	private static final long DEFAULT_DEVICE_CHECK_INTERVAL_MS = 1000L;
	public static final String MISSING_SOUND = "FOR THE DEBUG!";
	public static final String OPEN_AL_SOFT_PREFIX = "OpenAL Soft on ";
	public static final int OPEN_AL_SOFT_PREFIX_LENGTH = "OpenAL Soft on ".length();
	/**
	 * A reference to the sound handler.
	 */
	private final SoundManager soundManager;
	/**
	 * Reference to the GameSettings object.
	 */
	private final GameOptions options;
	/**
	 * Set to true when the SoundManager has been initialised.
	 */
	private boolean loaded;
	private final Library library = new Library();
	/**
	 * The listener object responsible for managing the sound listener position and orientation
	 */
	private final Listener listener = this.library.getListener();
	private final SoundBufferLibrary soundBuffers;
	private final SoundEngineExecutor executor = new SoundEngineExecutor();
	private final ChannelAccess channelAccess = new ChannelAccess(this.library, this.executor);
	/**
	 * A counter for how long the sound manager has been running
	 */
	private int tickCount;
	private long lastDeviceCheckTime;
	/**
	 * The current state of the audio device check
	 */
	private final AtomicReference<SoundEngine.DeviceCheckState> devicePoolState = new AtomicReference<>(SoundEngine.DeviceCheckState.NO_CHANGE);
	private final Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel = Maps.newHashMap();
	private final Multimap<SoundCategory, SoundInstance> instanceBySource = HashMultimap.create();
	/**
	 * A subset of playingSounds, this contains only {@linkplain TickableSoundInstance}
	 */
	private final List<TickableSoundInstance> tickingSounds = Lists.newArrayList();
	/**
	 * Contains sounds to play in n ticks. Type: HashMap<ISound, Integer>
	 */
	private final Map<SoundInstance, Integer> queuedSounds = Maps.newHashMap();
	/**
	 * The future time in which to stop this sound. Type: HashMap<String, Integer>
	 */
	private final Map<SoundInstance, Integer> soundDeleteTime = Maps.newHashMap();
	private final List<SoundEventListener> listeners = Lists.newArrayList();
	private final List<TickableSoundInstance> queuedTickableSounds = Lists.newArrayList();
	private final List<Sound> preloadQueue = Lists.newArrayList();

	public SoundEngine(SoundManager soundManager, GameOptions options, ResourceManager resourceManager) {
		this.soundManager = soundManager;
		this.options = options;
		this.soundBuffers = new SoundBufferLibrary(resourceManager);
	}

	/**
	 * Reloads the sound engine.
	 * <p>
	 * This method clears the warning set, checks for missing sound events, destroys the current sound system, and reloads the library.
	 */
	public void reload() {
		ONLY_WARN_ONCE.clear();

		/*for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
			if (soundEvent != SoundEvents.EMPTY) {
				ResourceLocation resourceLocation = soundEvent.location();
				if (this.soundManager.getSoundEvent(resourceLocation) == null) {
					LOGGER.warn("Missing sound for event: {}", BuiltInRegistries.SOUND_EVENT.getKey(soundEvent));
					ONLY_WARN_ONCE.add(resourceLocation);
				}
			}
		}*/

		this.destroy();
		this.loadLibrary();
	}

	/**
	 * Loads the sound library if it has not been loaded already.
	 * If loading is successful, the library is initialized, and the sound engine is started, otherwise, an error message is logged, and sounds and music are turned off.
	 */
	private synchronized void loadLibrary() {
		if (!this.loaded) {
			try {
				String device = SoundFix.currentOutputDevice;
				this.library.init(device.isEmpty() ? null : device, SoundFix.hrtfEnabled);
				this.listener.reset();
				this.listener.setGain(this.options.getSoundCategoryVolume(SoundCategory.MASTER));
				this.soundBuffers.preload(this.preloadQueue).thenRun(this.preloadQueue::clear);
				this.loaded = true;
				LOGGER.info(MARKER, "Sound engine started");
			} catch (RuntimeException var2) {
				LOGGER.error(MARKER, "Error starting SoundSystem. Turning off sounds & music", var2);
			}
		}
	}

	/**
	 * {@return the volume value pinned between 0.0f and 1.0f for a given {@linkplain SoundCategory} category}
	 */
	private float getVolume(@Nullable SoundCategory category) {
		return category != null && category != SoundCategory.MASTER ? this.options.getSoundCategoryVolume(category) : 1.0F;
	}

	/**
	 * Updates the volume for a specific sound category.
	 * <p>
	 * If the sound engine has not been loaded, the method returns without performing any action.
	 * <p>
	 * If the category is the "MASTER" category, the overall listener gain (volume) is set to the specified value.
	 * <p>
	 * For other categories, the volume is updated for each sound instance associated with the category.
	 * <p>
	 * If the calculated volume for an instance is less than or equal to 0.0, the instance is stopped.
	 * Otherwise, the volume of the instance is set to the calculated value.
	 */
	public void updateCategoryVolume(SoundCategory category, float volume) {
		if (this.loaded) {
			if (category == SoundCategory.MASTER) {
				this.listener.setGain(volume);
			} else {
				this.instanceToChannel.forEach((instance, source) -> {
					float f = this.calculateVolume(instance);
					source.execute(channel -> {
						if (f <= 0.0F) {
							channel.stop();
						} else {
							channel.setVolume(f);
						}
					});
				});
			}
		}
	}

	/**
	 * Cleans up the Sound System
	 */
	public void destroy() {
		if (this.loaded) {
			this.stopAll();
			this.soundBuffers.clear();
			this.library.cleanup();
			this.loaded = false;
		}
	}

	public void emergencyShutdown() {
		if (this.loaded) {
			this.library.cleanup();
		}
	}

	/**
	 * Stops the provided {@linkplain SoundInstance} from continuing to play.
	 */
	public void stop(SoundInstance sound) {
		if (this.loaded) {
			ChannelAccess.ChannelHandle channelHandle = this.instanceToChannel.get(sound);
			if (channelHandle != null) {
				channelHandle.execute(Channel::stop);
			}
		}
	}

	public void setVolume(SoundInstance soundInstance, float volume) {
		if (this.loaded) {
			ChannelAccess.ChannelHandle channelHandle = this.instanceToChannel.get(soundInstance);
			if (channelHandle != null) {
				channelHandle.execute(channel -> channel.setVolume(volume * this.calculateVolume(soundInstance)));
			}
		}
	}

	/**
	 * Stops all currently playing sounds
	 */
	public void stopAll() {
		if (this.loaded) {
			this.executor.flush();
			this.instanceToChannel.values().forEach(handle -> handle.execute(Channel::stop));
			this.instanceToChannel.clear();
			this.channelAccess.clear();
			this.queuedSounds.clear();
			this.tickingSounds.clear();
			this.instanceBySource.clear();
			this.soundDeleteTime.clear();
			this.queuedTickableSounds.clear();
		}
	}

	public void addEventListener(SoundEventListener listener) {
		this.listeners.add(listener);
	}

	public void removeEventListener(SoundEventListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * The audio device change is checked by this method.
	 * <p>
	 * If the current audio device is disconnected, an informational message is logged, and this method returns {@code true} to indicate a change is needed.
	 * <p>
	 * Otherwise, the elapsed time since the last device check is examined.
	 * If the elapsed time is greater than or equal to 1000 milliseconds, the device check is performed.
	 * <p>
	 * During the device check, the current device state is compared with the preferred sound device specified in the options.
	 * <ul>
	 *   <li>If the preferred sound device is an empty string and the system default audio device has changed, an informational message is logged, and the device pool state is set to indicate a change has been detected.</li>
	 *   <li>If the preferred sound device is not an empty string, it is checked whether the current device name is different from the preferred device name and if the preferred device is available in the list of available sound devices. </li>
	 *   <li>If both conditions are true, an informational message is logged, and the device pool state is set to indicate a change has been detected.</li>
	 * </ul>
	 * <p>
	 * Finally, the device pool state is set to indicate that the device check is complete.
	 * <p>
	 *
	 * @return {@code true} if a change in the audio device is needed, {@code false} otherwise.
	 */
	private boolean shouldChangeDevice() {
		if (this.library.isCurrentDeviceDisconnected()) {
			LOGGER.info("Audio device was lost!");
			return true;
		} else {
			long l = Minecraft.getTime();
			boolean bl = l - this.lastDeviceCheckTime >= DEFAULT_DEVICE_CHECK_INTERVAL_MS;
			if (bl) {
				this.lastDeviceCheckTime = l;
				if (this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.NO_CHANGE, SoundEngine.DeviceCheckState.ONGOING)) {
					String string = SoundFix.currentOutputDevice;
					SoundFix.IO.execute(() -> {
						if ("".equals(string)) {
							if (this.library.hasDefaultDeviceChanged()) {
								LOGGER.info("System default audio device has changed!");
								this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
							}
						} else if (!this.library.getCurrentDeviceName().equals(string) && this.library.getAvailableSoundDevices().contains(string)) {
							LOGGER.info("Preferred audio device has become available!");
							this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
						}

						this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.NO_CHANGE);
					});
				}
			}

			return this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.CHANGE_DETECTED, SoundEngine.DeviceCheckState.NO_CHANGE);
		}
	}

	/**
	 * Ticks all active instances of  {@code TickableSoundInstance}
	 */
	public void tick(boolean isGamePaused) {
		if (this.shouldChangeDevice()) {
			this.reload();
		}

		if (!isGamePaused) {
			this.tickNonPaused();
		}

		this.channelAccess.scheduleTick();
	}

	/**
	 * Executes a single tick for non-paused sounds.
	 * <p>
	 * The following steps are taken as part of this method:
	 * <ul>
	 *   <li>Increment the tick count.</li>
	 *   <li>Clears the queued tickable sounds list.</li>
	 *   <li>Updates and handles tickable sounds currently playing.</li>
	 *   <li>Updates volume, pitch, and position for each tickable sound.</li>
	 *   <li>Removes stopped or expired tickable sounds from the instance-to-channel mapping.</li>
	 *   <li>Removes stopped tickable sounds from the ticking sounds list.</li>
	 *   <li>Handles queued sounds that are ready to be played.</li>
	 * </ul>
	 * <p>
	 * Note: This method assumes that it is being called within a tick loop.
	 *
	 * @implNote This method assumes proper synchronization or thread confinement mechanisms are in place.
	 */
	private void tickNonPaused() {
		this.tickCount++;
		this.queuedTickableSounds/*.stream().filter(SoundInstance::canPlaySound)*/.forEach(this::play);
		this.queuedTickableSounds.clear();

		for (TickableSoundInstance tickableSoundInstance : this.tickingSounds) {
			/*if (!tickableSoundInstance.canPlaySound()) {
				this.stop(tickableSoundInstance);
			}*/

			tickableSoundInstance.tick();
			if (tickableSoundInstance.isStopped()) {
				this.stop(tickableSoundInstance);
			} else {
				float f = this.calculateVolume(tickableSoundInstance);
				float g = this.calculatePitch(tickableSoundInstance);
				Vec3d vec3 = new Vec3d(tickableSoundInstance.getX(), tickableSoundInstance.getY(), tickableSoundInstance.getZ());
				ChannelAccess.ChannelHandle channelHandle = this.instanceToChannel.get(tickableSoundInstance);
				if (channelHandle != null) {
					channelHandle.execute(channel -> {
						channel.setVolume(f);
						channel.setPitch(g);
						channel.setSelfPosition(vec3);
					});
				}
			}
		}

		Iterator<Entry<SoundInstance, ChannelAccess.ChannelHandle>> iterator = this.instanceToChannel.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<SoundInstance, ChannelAccess.ChannelHandle> entry = iterator.next();
			ChannelAccess.ChannelHandle channelHandle2 = entry.getValue();
			SoundInstance soundInstance = entry.getKey();
			float h = this.options.getSoundCategoryVolume(soundManager.get(soundInstance.getLocation()).getCategory());
			if (h <= 0.0F) {
				channelHandle2.execute(Channel::stop);
				iterator.remove();
			} else if (channelHandle2.isStopped()) {
				int i = this.soundDeleteTime.get(soundInstance);
				if (i <= this.tickCount) {
					if (shouldLoopManually(soundInstance)) {
						this.queuedSounds.put(soundInstance, this.tickCount + soundInstance.getPeriod());
					}

					iterator.remove();
					LOGGER.debug(MARKER, "Removed channel {} because it's not playing anymore", new Object[]{channelHandle2});
					this.soundDeleteTime.remove(soundInstance);

					try {
						this.instanceBySource.remove(soundManager.get(soundInstance.getLocation()).getCategory(), soundInstance);
					} catch (RuntimeException ignored) {
					}

					if (soundInstance instanceof TickableSoundInstance) {
						this.tickingSounds.remove(soundInstance);
					}
				}
			}
		}

		Iterator<Entry<SoundInstance, Integer>> iterator2 = this.queuedSounds.entrySet().iterator();

		while (iterator2.hasNext()) {
			Entry<SoundInstance, Integer> entry2 = iterator2.next();
			if (this.tickCount >= entry2.getValue()) {
				SoundInstance soundInstance = entry2.getKey();
				if (soundInstance instanceof TickableSoundInstance) {
					((TickableSoundInstance) soundInstance).tick();
				}

				this.play(soundInstance);
				iterator2.remove();
			}
		}
	}

	/**
	 * {@return Returns {@code true} if the SoundInstance requires manual looping, {@code false} otherwise
	 * <p>
	 *
	 * @param sound the SoundInstance to check
	 */
	private static boolean requiresManualLooping(SoundInstance sound) {
		return sound.getPeriod() > 0;
	}

	/**
	 * @param sound The SoundInstance to check
	 * @return Returns {@code true} if the SoundInstance should loop manually, {@code false} otherwise
	 */
	private static boolean shouldLoopManually(SoundInstance sound) {
		return sound.isLooping() && requiresManualLooping(sound);
	}

	/**
	 * @param sound The SoundInstance to check
	 * @return Returns {@code true} if the SoundInstance should loop automatically, {@code false} otherwise
	 */
	private static boolean shouldLoopAutomatically(SoundInstance sound) {
		return sound.isLooping() && !requiresManualLooping(sound);
	}

	/**
	 * {@return {@code true} if the {@linkplain SoundInstance} is active, {@code false} otherwise}
	 *
	 * @param sound the SoundInstance to check
	 */
	public boolean isActive(SoundInstance sound) {
		if (!this.loaded) {
			return false;
		} else {
			return this.soundDeleteTime.containsKey(sound) && this.soundDeleteTime.get(sound) <= this.tickCount || this.instanceToChannel.containsKey(sound);
		}
	}

	/**
	 * Plays a given sound instance.
	 * <p>
	 * If the sound engine is not loaded or the sound instance cannot be played, the method returns early.
	 * <p>
	 * The method fulfills the following parts:
	 * <ul>
	 *   <li>Performs a series of checks to determine if it can play a sound</li>
	 *   <li>Handles the playing of instances of {@code SoundInstance}</li>
	 *   <li>Logs potential errors that may have occured</li>
	 *   <li>Handles mapping instances of {@code SoundInstance} to specific audio channels</li>
	 *   <li>Handles deletion times for active instances of {@code SoundInstance}</li>
	 *   <li>Calculates and handles various sound properties such as volume, pitch, attenuation, looping, position and relative, </li>
	 * </ul>
	 * <p>
	 *
	 * @param sound the sound instance to be played.
	 * @implNote This method assumes proper synchronization or that thread confinement mechanisms are in place.
	 */
	public void play(SoundInstance sound) {
		if (this.loaded) {
			//if (sound.canPlaySound()) {
			SoundPool weighedSoundEvents = soundManager.get(sound.getLocation());
			Identifier resourceLocation = sound.getLocation();
			if (weighedSoundEvents == null) {
				if (ONLY_WARN_ONCE.add(resourceLocation)) {
					LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", new Object[]{resourceLocation});
				}
			} else {
				Sound sound2 = weighedSoundEvents.get();
				if (sound2 != null) {
					if (sound2 == SoundManager.MISSING_SOUND) {
						if (ONLY_WARN_ONCE.add(resourceLocation)) {
							LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", new Object[]{resourceLocation});
						}
					} else {
						float f = sound.getVolume();
						float g = Math.max(f, 1.0F) * 16;
						SoundCategory soundSource = weighedSoundEvents.getCategory();
						float h = this.calculateVolume(f, soundSource);
						float i = this.calculatePitch(sound);
						SoundInstance.Attenuation attenuation = sound.getAttenuationType();
						boolean bl = soundSource == SoundCategory.MASTER || soundSource == SoundCategory.MUSIC;//sound.isRelative();
						if (h == 0.0F /*&& !sound.canStartSilent()*/) {
							LOGGER.debug(MARKER, "Skipped playing sound {}, volume was zero.", new Object[]{sound2.getLocation()});
						} else {
							Vec3d vec3 = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
							if (!bl && listener.getTransform().position().squaredDistanceTo(vec3) >= g*g) { // cut out inaudible sounds (because they're too far away)
								return;
							}
							if (!this.listeners.isEmpty()) {
								float j = !bl && attenuation != SoundInstance.Attenuation.NONE ? g : Float.POSITIVE_INFINITY;
								if (j == Float.POSITIVE_INFINITY || listener.getTransform().position().squaredDistanceTo(vec3) < g*g) {
									for (SoundEventListener soundEventListener : this.listeners) {
										soundEventListener.onPlaySound(sound, weighedSoundEvents, j);
									}
								}
							}

							if (this.listener.getGain() <= 0.0F) {
								LOGGER.debug(MARKER, "Skipped playing soundEvent: {}, master volume was zero", new Object[]{resourceLocation});
							} else {
								boolean bl2 = shouldLoopAutomatically(sound);
								boolean bl3 = sound2.isStream();
								CompletableFuture<ChannelAccess.ChannelHandle> completableFuture = this.channelAccess
									.createHandle(sound2.isStream() ? Library.Pool.STREAMING : Library.Pool.STATIC);
								ChannelAccess.ChannelHandle channelHandle = completableFuture.join();
								if (channelHandle == null) {
										/*if (SharedConstants.IS_RUNNING_IN_IDE) {
											LOGGER.warn("Failed to create new sound handle");
										}*/
								} else {
									LOGGER.debug(MARKER, "Playing sound {} for event {}", new Object[]{sound2.getLocation(), resourceLocation});
									this.soundDeleteTime.put(sound, this.tickCount + 20);
									this.instanceToChannel.put(sound, channelHandle);
									this.instanceBySource.put(soundSource, sound);
									channelHandle.execute(channel -> {
										channel.setPitch(i);
										channel.setVolume(h);
										if (attenuation == SoundInstance.Attenuation.LINEAR) {
											channel.linearAttenuation(g);
										} else {
											channel.disableAttenuation();
										}

										channel.setLooping(bl2 && !bl3);
										channel.setSelfPosition(vec3);
										channel.setRelative(bl);
									});
									if (!bl3) {
										this.soundBuffers.getCompleteBuffer(sound2.getLocation()).thenAccept(buffer -> channelHandle.execute(channel -> {
											channel.attachStaticBuffer(buffer);
											channel.play();
										}));
									} else {
										this.soundBuffers.getStream(sound2.getLocation(), bl2).thenAccept(audioStream -> channelHandle.execute(channel -> {
											channel.attachBufferStream(audioStream);
											channel.play();
										}));
									}

									if (sound instanceof TickableSoundInstance) {
										this.tickingSounds.add((TickableSoundInstance) sound);
									}
								}
							}
						}
					}
				}
			}
			//}
		}
	}

	/**
	 * Queues a new {@linkplain TickableSoundInstance}
	 *
	 * @param tickableSound the {@linkplain TickableSoundInstance} to queue
	 */
	public void queueTickingSound(TickableSoundInstance tickableSound) {
		this.queuedTickableSounds.add(tickableSound);
	}

	/**
	 * Requests a specific {@linkplain Sound} instance to be preloaded.
	 */
	public void requestPreload(Sound sound) {
		this.preloadQueue.add(sound);
	}

	/**
	 * Calculates the pitch of the sound being played.
	 * <p>
	 * Clamps the sound between 0.5f and 2.0f.
	 *
	 * @param sound the {@linkplain SoundInstance} being played
	 */
	private float calculatePitch(SoundInstance sound) {
		return MathHelper.clamp(sound.getPitch(), 0.5F, 2.0F);
	}

	/**
	 * Calculates the volume for the sound being played.
	 * <p>
	 * Delegates to {@code #calculateVolume(float, SoundSource)}
	 */
	private float calculateVolume(SoundInstance sound) {
		return this.calculateVolume(sound.getVolume(), soundManager.get(sound.getLocation()).getCategory());
	}

	/**
	 * Calculates the volume of the sound being played.
	 * <p>
	 * Clamps the sound between 0.0f and 1.0f.
	 */
	private float calculateVolume(float volumeMultiplier, SoundCategory source) {
		return MathHelper.clamp(volumeMultiplier * this.getVolume(source), 0.0F, 1.0F);
	}

	/**
	 * Pauses all currently playing sounds
	 */
	public void pause() {
		if (this.loaded) {
			this.channelAccess.executeOnChannels(channels -> channels.forEach(Channel::pause));
		}
	}

	/**
	 * Resumes playing all currently playing sounds (after pauseAllSounds)
	 */
	public void resume() {
		if (this.loaded) {
			this.channelAccess.executeOnChannels(channels -> channels.forEach(Channel::unpause));
		}
	}

	/**
	 * Adds a sound to play in n ticks
	 */
	public void playDelayed(SoundInstance sound, int delay) {
		this.queuedSounds.put(sound, this.tickCount + delay);
	}

	public void updateSource(ListenerTransform transform) {
		if (this.loaded) {
			this.executor.execute(() -> this.listener.setTransform(transform));
		}
	}

	public void stop(@Nullable Identifier soundName, @Nullable SoundCategory category) {
		if (category != null) {
			for (SoundInstance soundInstance : this.instanceBySource.get(category)) {
				if (soundName == null || soundInstance.getLocation().equals(soundName)) {
					this.stop(soundInstance);
				}
			}
		} else if (soundName == null) {
			this.stopAll();
		} else {
			for (SoundInstance soundInstancex : this.instanceToChannel.keySet()) {
				if (soundInstancex.getLocation().equals(soundName)) {
					this.stop(soundInstancex);
				}
			}
		}
	}

	public String getDebugString() {
		return this.library.getDebugString();
	}

	public List<String> getAvailableSoundDevices() {
		return this.library.getAvailableSoundDevices();
	}

	public ListenerTransform getListenerTransform() {
		return this.listener.getTransform();
	}

	@Environment(EnvType.CLIENT)
	enum DeviceCheckState {
		ONGOING,
		CHANGE_DETECTED,
		NO_CHANGE
	}
}
