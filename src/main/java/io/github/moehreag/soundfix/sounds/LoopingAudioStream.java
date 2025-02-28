package io.github.moehreag.soundfix.sounds;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LoopingAudioStream implements AudioStream {
	private final LoopingAudioStream.AudioStreamProvider provider;
	private AudioStream stream;
	private final BufferedInputStream bufferedInputStream;

	public LoopingAudioStream(LoopingAudioStream.AudioStreamProvider provider, InputStream inputStream) throws IOException {
		this.provider = provider;
		this.bufferedInputStream = new BufferedInputStream(inputStream);
		this.bufferedInputStream.mark(Integer.MAX_VALUE);
		this.stream = provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
	}

	@Override
	public AudioFormat getFormat() {
		return this.stream.getFormat();
	}

	@Override
	public ByteBuffer read(int size) throws IOException {
		ByteBuffer byteBuffer = this.stream.read(size);
		if (!byteBuffer.hasRemaining()) {
			this.stream.close();
			this.bufferedInputStream.reset();
			this.stream = this.provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
			byteBuffer = this.stream.read(size);
		}

		return byteBuffer;
	}

	public void close() throws IOException {
		this.stream.close();
		this.bufferedInputStream.close();
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface AudioStreamProvider {
		AudioStream create(InputStream inputStream) throws IOException;
	}

	@Environment(EnvType.CLIENT)
	static class NoCloseBuffer extends FilterInputStream {
		NoCloseBuffer(InputStream inputStream) {
			super(inputStream);
		}

		public void close() {
		}
	}
}
