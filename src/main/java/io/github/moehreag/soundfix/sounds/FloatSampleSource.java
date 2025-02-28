package io.github.moehreag.soundfix.sounds;

import java.io.IOException;
import java.nio.ByteBuffer;

import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface FloatSampleSource extends FiniteAudioStream {
	int EXPECTED_MAX_FRAME_SIZE = 8192;

	boolean readChunk(FloatConsumer output) throws IOException;

	@Override
	default ByteBuffer read(int size) throws IOException {
		ChunkedSampleByteBuf chunkedSampleByteBuf = new ChunkedSampleByteBuf(size + EXPECTED_MAX_FRAME_SIZE);

		while (this.readChunk(chunkedSampleByteBuf) && chunkedSampleByteBuf.size() < size) {
		}

		return chunkedSampleByteBuf.get();
	}

	@Override
	default ByteBuffer readAll() throws IOException {
		ChunkedSampleByteBuf chunkedSampleByteBuf = new ChunkedSampleByteBuf(EXPECTED_MAX_FRAME_SIZE*2);

		while (this.readChunk(chunkedSampleByteBuf)) {
		}

		return chunkedSampleByteBuf.get();
	}
}
