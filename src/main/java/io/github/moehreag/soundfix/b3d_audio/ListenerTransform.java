package io.github.moehreag.soundfix.b3d_audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public record ListenerTransform(Vec3d position, Vec3d forward, Vec3d up) {
	public static final ListenerTransform INITIAL = new ListenerTransform(new Vec3d(0, 0, 0), new Vec3d(0.0, 0.0, -1.0), new Vec3d(0.0, 1.0, 0.0));

	public Vec3d right() {
		return this.forward.crossProduct(this.up);
	}
}
