package io.github.moehreag.soundfix.b3d_audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;

/**
 * The Listener class represents the listener in a 3D audio environment.
 * <p>
 * The listener's position and orientation determine how sounds are perceived by the listener.
 */
@Environment(EnvType.CLIENT)
public class Listener {
	private float gain = 1.0F;
	private ListenerTransform transform = ListenerTransform.INITIAL;

	public void setTransform(ListenerTransform transform) {
		this.transform = transform;
		Vec3d vec3 = transform.position();
		Vec3d vec32 = transform.forward();
		Vec3d vec33 = transform.up();
		AL10.alListener3f(AL10.AL_POSITION, (float)vec3.x, (float)vec3.y, (float)vec3.z);
		AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{(float)vec32.x, (float)vec32.y, (float)vec32.z, (float)vec33.x, (float)vec33.y, (float)vec33.z});
	}

	/**
	 * Sets the listener's gain.
	 *
	 * @param gain The gain to set for the listener.
	 */
	public void setGain(float gain) {
		AL10.alListenerf(AL10.AL_GAIN, gain);
		this.gain = gain;
	}

	/**
	 * {@return the current gain value of the listener}
	 */
	public float getGain() {
		return this.gain;
	}

	/**
	 * Resets the listener's position and orientation to default values.
	 */
	public void reset() {
		this.setTransform(ListenerTransform.INITIAL);
	}

	public ListenerTransform getTransform() {
		return this.transform;
	}
}
