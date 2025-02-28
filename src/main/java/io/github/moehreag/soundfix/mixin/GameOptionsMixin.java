package io.github.moehreag.soundfix.mixin;

import java.io.PrintWriter;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.moehreag.soundfix.SoundFix;
import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptions.class)
public class GameOptionsMixin {

	@Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/sound/SoundCategory;values()[Lnet/minecraft/sound/SoundCategory;"))
	private void loadAdditionalOptions(CallbackInfo ci, @Local(ordinal = 0) String string, @Local(ordinal = 1) String string2) {
		if ("soundDevice".equals(string)) {
			if ("\"\"".equals(string2)) {
				SoundFix.currentOutputDevice = "";
			} else {
				SoundFix.currentOutputDevice = string2;
			}
		} else if ("directionalAudio".equals(string)) {
			SoundFix.hrtfEnabled = "true".equals(string2);
		}
	}

	@Inject(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/sound/SoundCategory;values()[Lnet/minecraft/sound/SoundCategory;"))
	private void saveAdditionalOptions(CallbackInfo ci, @Local PrintWriter writer) {
		if (!SoundFix.currentOutputDevice.isEmpty()) {
			writer.println("soundDevice:" + SoundFix.currentOutputDevice);
		} else {
			writer.println("soundDevice:\"\"");
		}
		writer.println("directionalAudio:" + SoundFix.hrtfEnabled);
	}
}
