package io.github.moehreag.soundfix.b3d_audio;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;

/**
 * The SoundBuffer class represents an audio buffer containing audio data in a particular format.
 * <p>
 * The audio data can be used to create an OpenAL buffer, which can be played in a 3D audio environment.
 */
@Environment(EnvType.CLIENT)
public class SoundBuffer {
	@Nullable
	private ByteBuffer data;
	private final AudioFormat format;
	private boolean hasAlBuffer;
	private int alBuffer;

	public SoundBuffer(ByteBuffer data, AudioFormat format) {
		this.data = data;
		this.format = format;
	}

	OptionalInt getOrCreateAlBuffer() {
		if (!this.hasAlBuffer) {
			if (this.data == null) {
				return OptionalInt.empty();
			}

			int i = OpenAlUtil.audioFormatToOpenAl(this.format);
			int[] is = new int[1];
			AL10.alGenBuffers(is);
			if (OpenAlUtil.checkALError("Creating buffer")) {
				return OptionalInt.empty();
			}

			AL10.alBufferData(is[0], i, this.data, (int)this.format.getSampleRate());
			if (OpenAlUtil.checkALError("Assigning buffer data")) {
				return OptionalInt.empty();
			}

			this.alBuffer = is[0];
			this.hasAlBuffer = true;
			this.data = null;
		}

		return OptionalInt.of(this.alBuffer);
	}

	/**
	 * Deletes the OpenAL buffer associated with this SoundBuffer, if it exists.
	 */
	public void discardAlBuffer() {
		if (this.hasAlBuffer) {
			AL10.alDeleteBuffers(new int[]{this.alBuffer});
			if (OpenAlUtil.checkALError("Deleting stream buffers")) {
				return;
			}
		}

		this.hasAlBuffer = false;
	}

	/**
	 * Releases the OpenAL buffer associated with this SoundBuffer and returns it as an OptionalInt.
	 * If no buffer has been created yet, returns an empty OptionalInt.
	 * @return an {@linkplain OptionalInt} containing the OpenAL buffer handle, or an empty one, if the buffer has not been created
	 */
	public OptionalInt releaseAlBuffer() {
		OptionalInt optionalInt = this.getOrCreateAlBuffer();
		this.hasAlBuffer = false;
		return optionalInt;
	}
}
