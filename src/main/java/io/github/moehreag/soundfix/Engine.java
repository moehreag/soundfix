package io.github.moehreag.soundfix;

import java.util.HashMap;
import java.util.Map;

import io.github.moehreag.soundfix.b3d_audio.ListenerTransform;
import io.github.moehreag.soundfix.sounds.SoundEventListener;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class Engine extends SoundSystem {
	private final io.github.moehreag.soundfix.sounds.SoundEngine engine;
	private final Map<SoundInstanceListener, SoundEventListener> listeners;

	public Engine(SoundManager manager, GameOptions options, ResourceManager resourceManager) {
		super(manager, options, resourceManager);
		listeners = new HashMap<>();
		engine = new io.github.moehreag.soundfix.sounds.SoundEngine(manager, options, resourceManager);
		SoundFix.engine = engine;
	}

	@Override
	public void reloadSounds() {
		engine.reload();
	}

	@Override
	public void updateSoundVolume(SoundCategory soundCategory, float f) {
		engine.updateCategoryVolume(soundCategory, f);
	}

	@Override
	public void stopAll() {
		engine.stopAll();
	}

	@Override
	public void registerListener(SoundInstanceListener soundInstanceListener) {
		SoundEventListener listener = (sound, accessor, range) -> soundInstanceListener.onSoundPlayed(sound, accessor);
		listeners.put(soundInstanceListener, listener);
		engine.addEventListener(listener);
	}

	@Override
	public void unregisterListener(SoundInstanceListener soundInstanceListener) {
		engine.removeEventListener(listeners.get(soundInstanceListener));
	}

	@Override
	public void tick(boolean bl) {
		engine.tick(bl);
	}

	@Override
	public void stop() {
		engine.stopAll();
	}

	@Override
	public boolean isPlaying(SoundInstance sound) {
		return engine.isActive(sound);
	}

	@Override
	public void stop(SoundInstance sound) {
		engine.stop(sound);
	}

	@Override
	public void play(SoundInstance sound1) {
		engine.play(sound1);
	}

	@Override
	public void playNextTick(TickableSoundInstance tickableSoundInstance) {
		engine.queueTickingSound(tickableSoundInstance);
	}

	@Override
	public void addPreloadedSound(Sound sound) {
		engine.requestPreload(sound);
	}

	@Override
	public void pauseAll() {
		engine.pause();
	}

	@Override
	public void resumeAll() {
		engine.resume();
	}

	@Override
	public void play(SoundInstance sound, int delay) {
		engine.playDelayed(sound, delay);
	}

	@Override
	public void updateListenerPosition(Camera camera) {
		engine.updateSource(new ListenerTransform(camera.getPos(), new Vec3d(camera.getHorizontalPlane()), new Vec3d(camera.getVerticalPlane())));
	}

	@Override
	public void stopSounds(@Nullable Identifier identifier, @Nullable SoundCategory soundCategory) {
		engine.stop(identifier, soundCategory);
	}

	@Override
	public String getDebugString() {
		return engine.getDebugString();
	}
}
