package io.github.moehreag.soundfix.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SoundInstance;

@Environment(EnvType.CLIENT)
public interface SoundEventListener {
	void onPlaySound(SoundInstance sound, SoundPool accessor, float range);
}
