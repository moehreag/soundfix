package io.github.moehreag.soundfix.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;

@Environment(EnvType.CLIENT)
public interface SoundEventListener {
	void onPlaySound(SoundInstance sound, WeightedSoundSet accessor, float range);
}
