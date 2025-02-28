package io.github.moehreag.soundfix.sounds;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface AudioStream extends Closeable {
	AudioFormat getFormat();

	ByteBuffer read(int size) throws IOException;
}
