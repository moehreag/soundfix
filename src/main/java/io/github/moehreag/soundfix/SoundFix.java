package io.github.moehreag.soundfix;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.github.moehreag.soundfix.sounds.SoundEngine;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class SoundFix implements ClientModInitializer {

	public static final Executor SOUND = Executors.newCachedThreadPool();
	public static final Executor IO = Executors.newCachedThreadPool();

	public static String currentOutputDevice = "";
	public static boolean hrtfEnabled;
	public static SoundEngine engine;

	@Override
	public void onInitializeClient() {

	}

	public static Text getOutputDeviceButtonMessage() {
		String device = currentOutputDevice;
		if (currentOutputDevice.isEmpty()) {
			device = I18n.translate("options.audioDevice.default");
		} else if (currentOutputDevice.startsWith(SoundEngine.OPEN_AL_SOFT_PREFIX)) {
			device = currentOutputDevice.substring(SoundEngine.OPEN_AL_SOFT_PREFIX_LENGTH);
		}
		return new TranslatableText("options.audioDevice", device);
	}

	public static Text getHrtfEnabledButtonMessage() {
		return new TranslatableText("options.directionalAudio", hrtfEnabled ? ScreenTexts.ON : ScreenTexts.OFF);
	}
}
