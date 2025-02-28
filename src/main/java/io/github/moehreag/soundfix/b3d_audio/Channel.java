package io.github.moehreag.soundfix.b3d_audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.moehreag.soundfix.sounds.AudioStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * Represents an OpenAL audio channel.
 */
@Environment(EnvType.CLIENT)
public class Channel {
	private static final Logger LOGGER = LogManager.getLogger("Channel");
	private static final int QUEUED_BUFFER_COUNT = 4;
	public static final int BUFFER_DURATION_SECONDS = 1;
	private final int source;
	private final AtomicBoolean initialized = new AtomicBoolean(true);
	private int streamingBufferSize = 16384;
	@Nullable
	private AudioStream stream;

	/**
	 * Creates a new OpenAL audio channel.
	 * {@return a new OpenAL audio channel or {@code null} if its creation failed}
	 */
	@Nullable
	static Channel create() {
		int[] is = new int[1];
		AL10.alGenSources(is);
		return OpenAlUtil.checkALError("Allocate new source") ? null : new Channel(is[0]);
	}

	private Channel(int source) {
		this.source = source;
	}

	/**
	 * Stops the audio channel and releases resources.
	 */
	public void destroy() {
		if (this.initialized.compareAndSet(true, false)) {
			AL10.alSourceStop(this.source);
			OpenAlUtil.checkALError("Stop");
			if (this.stream != null) {
				try {
					this.stream.close();
				} catch (IOException var2) {
					LOGGER.error("Failed to close audio stream", var2);
				}

				this.removeProcessedBuffers();
				this.stream = null;
			}

			AL10.alDeleteSources(new int[]{this.source});
			OpenAlUtil.checkALError("Cleanup");
		}
	}

	/**
	 * Starts playing the audio channel.
	 */
	public void play() {
		AL10.alSourcePlay(this.source);
	}

	/**
	 * {@return the state of the audio channel}
	 */
	private int getState() {
		return !this.initialized.get() ? 4116 : AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE);
	}

	/**
	 * Pauses the audio channel.
	 */
	public void pause() {
		if (this.getState() == AL10.AL_PLAYING) {
			AL10.alSourcePause(this.source);
		}
	}

	/**
	 * Resumes playing the audio channel if it was paused.
	 */
	public void unpause() {
		if (this.getState() == AL10.AL_PAUSED) {
			AL10.alSourcePlay(this.source);
		}
	}

	/**
	 * Stops playing the audio channel.
	 */
	public void stop() {
		if (this.initialized.get()) {
			AL10.alSourceStop(this.source);
			OpenAlUtil.checkALError("Stop");
		}
	}

	/**
	 * {@return {@code true} if the audio channel is currently playing, {@code false} otherwise}
	 */
	public boolean playing() {
		return this.getState() == AL10.AL_PLAYING;
	}

	/**
	 * {@return {@code true} if the audio channel is stopped, {@code false} otherwise}
	 */
	public boolean stopped() {
		return this.getState() == AL10.AL_STOPPED;
	}

	/**
	 * Sets the position of the audio channel.
	 *
	 * @param source the position of the audio channel
	 */
	public void setSelfPosition(Vec3d source) {
		AL10.alSourcefv(this.source, AL10.AL_POSITION, new float[]{(float) source.x, (float) source.y, (float) source.z});
	}

	/**
	 * Sets the pitch of the audio channel.
	 *
	 * @param pitch the pitch of the audio channel
	 */
	public void setPitch(float pitch) {
		AL10.alSourcef(this.source, AL10.AL_PITCH, pitch);
	}

	/**
	 * Sets whether the audio channel should loop.
	 *
	 * @param looping {@code true} if the audio channel should loop, {@code false} otherwise
	 */
	public void setLooping(boolean looping) {
		AL10.alSourcei(this.source, AL10.AL_LOOPING, looping ? 1 : 0);
	}

	/**
	 * Sets the volume of the audio channel.
	 *
	 * @param volume the volume of the audio channel
	 */
	public void setVolume(float volume) {
		AL10.alSourcef(this.source, AL10.AL_GAIN, volume);
	}

	/**
	 * Disables attenuation for the audio channel.
	 */
	public void disableAttenuation() {
		AL10.alSourcei(this.source, AL10.AL_DISTANCE_MODEL, 0);
	}

	/**
	 * Sets linear attenuation for the audio channel.
	 *
	 * @param linearAttenuation the linear attenuation of the audio channel
	 */
	public void linearAttenuation(float linearAttenuation) {
		AL10.alSourcei(this.source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
		AL10.alSourcef(this.source, AL10.AL_MAX_DISTANCE, linearAttenuation);
		AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
		AL10.alSourcef(this.source, AL10.AL_REFERENCE_DISTANCE, 0.0F);
	}

	/**
	 * Sets whether the audio channel should be relative to the listener's position.
	 *
	 * @param relative {@code true} if the audio channel should be relative, {@code false} otherwise
	 */
	public void setRelative(boolean relative) {
		AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, relative ? 1 : 0);
	}

	/**
	 * Attaches a static buffer to the audio channel.
	 *
	 * @param buffer the buffer to attach
	 */
	public void attachStaticBuffer(SoundBuffer buffer) {
		buffer.getOrCreateAlBuffer().ifPresent(value -> AL10.alSourcei(this.source, AL10.AL_BUFFER, value));
	}

	/**
	 * Attaches a buffer stream to the audio channel.
	 *
	 * @param stream the stream to attach
	 */
	public void attachBufferStream(AudioStream stream) {
		this.stream = stream;
		AudioFormat audioFormat = stream.getFormat();
		this.streamingBufferSize = calculateBufferSize(audioFormat, BUFFER_DURATION_SECONDS);
		this.pumpBuffers(QUEUED_BUFFER_COUNT);
	}

	/**
	 * Calculates the buffer size for an audio stream.
	 *
	 * @param format       the audio format of the stream
	 * @param sampleAmount the number of samples to buffer
	 * @return the buffer size
	 */
	private static int calculateBufferSize(AudioFormat format, int sampleAmount) {
		return (int) ((float) (sampleAmount * format.getSampleSizeInBits()) / 8.0F * (float) format.getChannels() * format.getSampleRate());
	}

	/**
	 * Reads and queues audio buffers from the stream.
	 *
	 * @param readCount the number of buffers to read and queue
	 */
	private void pumpBuffers(int readCount) {
		if (this.stream != null) {
			try {
				for (int i = 0; i < readCount; i++) {
					ByteBuffer byteBuffer = this.stream.read(this.streamingBufferSize);
					if (byteBuffer != null) {
						new SoundBuffer(byteBuffer, this.stream.getFormat())
							.releaseAlBuffer()
							.ifPresent(bufferName -> AL10.alSourceQueueBuffers(this.source, new int[]{bufferName}));
					}
				}
			} catch (IOException var4) {
				LOGGER.error("Failed to read from audio stream", var4);
			}
		}
	}

	/**
	 * Updates the audio stream by removing processed buffers and queuing new ones.
	 */
	public void updateStream() {
		if (this.stream != null) {
			int i = this.removeProcessedBuffers();
			this.pumpBuffers(i);
		}
	}

	/**
	 * Removes processed audio buffers from the audio channel.
	 *
	 * @return the number of processed buffers removed
	 */
	private int removeProcessedBuffers() {
		int i = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);
		if (i > 0) {
			int[] is = new int[i];
			AL10.alSourceUnqueueBuffers(this.source, is);
			OpenAlUtil.checkALError("Unqueue buffers");
			AL10.alDeleteBuffers(is);
			OpenAlUtil.checkALError("Remove processed buffers");
		}

		return i;
	}
}
