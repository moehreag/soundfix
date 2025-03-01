package io.github.moehreag.soundfix.sounds;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.google.common.collect.Maps;
import io.github.moehreag.soundfix.SoundFix;
import io.github.moehreag.soundfix.b3d_audio.SoundBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.sound.Sound;
import net.minecraft.resource.Identifier;

/**
 * The {@linkplain SoundBufferLibrary} class provides a cache containing instances of {@linkplain SoundBuffer} and {@linkplain AudioStream} for use in Minecraft sound handling.
 */
@Environment(EnvType.CLIENT)
public class SoundBufferLibrary {
	/**
	 * The {@linkplain ResourceManager} used for loading sound resources.
	 */
	private final ResourceManager resourceManager;
	private final Map<Identifier, CompletableFuture<SoundBuffer>> cache = Maps.newHashMap();

	public SoundBufferLibrary(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	/**
	 * {@return Returns a {@linkplain CompletableFuture} containing the complete {@linkplain SoundBuffer}. The {@linkplain SoundBuffer} is loaded asynchronously and cached.}
	 *
	 * @param soundID the {@linkplain Identifier} of the sound
	 */
	public CompletableFuture<SoundBuffer> getCompleteBuffer(Identifier soundID) {
		return this.cache.computeIfAbsent(soundID, resourceLocation -> CompletableFuture.supplyAsync(() -> {
			try (InputStream inputStream = this.resourceManager.getResource(resourceLocation).asStream()) {
				try (FiniteAudioStream finiteAudioStream = new JOrbisAudioStream(inputStream)) {
					return new SoundBuffer(finiteAudioStream.readAll(), finiteAudioStream.getFormat());
				}
			} catch (IOException var10) {
				throw new CompletionException(var10);
			}
		}, SoundFix.SOUND));
	}

	/**
	 * {@return Returns a {@linkplain CompletableFuture} containing the {@linkplain AudioStream}. The {@linkplain AudioStream} is loaded asynchronously.}
	 *
	 * @param resourceLocation the {@linkplain Identifier} of the sound
	 * @param isWrapper        whether the {@linkplain AudioStream} should be a {@linkplain LoopingAudioStream}
	 */
	public CompletableFuture<AudioStream> getStream(Identifier resourceLocation, boolean isWrapper) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				InputStream inputStream = this.resourceManager.getResource(resourceLocation).asStream();
				return isWrapper ? new LoopingAudioStream(JOrbisAudioStream::new, inputStream) : new JOrbisAudioStream(inputStream);
			} catch (IOException var4) {
				throw new CompletionException(var4);
			}
		}, SoundFix.SOUND);
	}

	/**
	 * Clears the cache of all {@linkplain SoundBuffer} instances.
	 */
	public void clear() {
		this.cache.values().forEach(buffer -> buffer.thenAccept(SoundBuffer::discardAlBuffer));
		this.cache.clear();
	}

	/**
	 * Preloads the {@linkplain SoundBuffer} objects for the specified collection of sounds.
	 * <p>
	 *
	 * @param sounds the collection of sounds to preload
	 * @return a {@linkplain CompletableFuture} representing the completion of the preload operation
	 */
	public CompletableFuture<?> preload(Collection<Sound> sounds) {
		return CompletableFuture.allOf(sounds.stream().map(sound -> this.getCompleteBuffer(sound.getLocation())).toArray(CompletableFuture[]::new));
	}
}
