package io.github.moehreag.soundfix;

import io.github.moehreag.soundfix.b3d_audio.ListenerTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundEngine;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Engine extends SoundEngine {
	private final io.github.moehreag.soundfix.sounds.SoundEngine engine;

	public Engine(SoundManager manager, GameOptions options) {
		super(manager, options);
		engine = new io.github.moehreag.soundfix.sounds.SoundEngine(manager, options, Minecraft.getInstance().getResourceManager());
		SoundFix.engine = engine;
	}

	@Override
	public void reload() {
		engine.reload();
	}

	@Override
	public void setVolume(SoundCategory category, float volume) {
		engine.updateCategoryVolume(category, volume);
	}

	@Override
	public void close() {
		engine.destroy();
	}

	@Override
	public void stop() {
		engine.stopAll();
	}

	@Override
	public void tick() {
		engine.tick(Minecraft.getInstance().isPaused());
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
	public void pause() {
		engine.pause();
	}

	@Override
	public void resume() {
		engine.resume();
	}

	@Override
	public void play(SoundInstance sound, int delay) {
		engine.playDelayed(sound, delay);
	}

	@Override
	public void updateListener(PlayerEntity player, float tickDelta) {
		if (player == null)  {
			return;
		}
		var camera = Minecraft.getInstance().getCamera();
		float pitch = camera.prevPitch + (camera.pitch - camera.prevPitch) * tickDelta;
		float yaw = camera.prevYaw + (camera.yaw - camera.prevYaw) * tickDelta;
		double x = camera.prevX + (camera.x - camera.prevX) * (double) tickDelta;
		double y = camera.prevY + (camera.y - camera.prevY) * (double) tickDelta + (double) camera.getEyeHeight();
		double z = camera.prevZ + (camera.z - camera.prevZ) * (double) tickDelta;
		float h = MathHelper.cos((yaw + 90.0F) * ((float) Math.PI / 180F));
		float i = MathHelper.sin((yaw + 90.0F) * ((float) Math.PI / 180F));
		float j = MathHelper.cos(-pitch * ((float) Math.PI / 180F));
		float k = MathHelper.sin(-pitch * ((float) Math.PI / 180F));
		float l = MathHelper.cos((-pitch + 90.0F) * ((float) Math.PI / 180F));
		float m = MathHelper.sin((-pitch + 90.0F) * ((float) Math.PI / 180F));
		float n = h * j;
		float p = i * j;
		float q = h * l;
		float s = i * l;
		engine.updateSource(new ListenerTransform(new Vec3d(x, y, z), new Vec3d(n, k, p), new Vec3d(q, m, s)));
	}
}
