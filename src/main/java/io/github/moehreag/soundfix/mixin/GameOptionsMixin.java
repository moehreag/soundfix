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

	@Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundCategory;values()[Lnet/minecraft/client/sound/SoundCategory;"))
	private void loadAdditionalOptions(CallbackInfo ci, @Local String[] strings) {
		if ("soundDevice".equals(strings[0])) {
			if ("\"\"".equals(strings[1])) {
				SoundFix.currentOutputDevice = "";
			} else {
				SoundFix.currentOutputDevice = strings[1];
			}
		} else if ("directionalAudio".equals(strings[0])) {
			SoundFix.hrtfEnabled = "true".equals(strings[1]);
		}
	}

	@Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundCategory;values()[Lnet/minecraft/client/sound/SoundCategory;"))
	private void saveAdditionalOptions(CallbackInfo ci, @Local PrintWriter writer) {
		if (!SoundFix.currentOutputDevice.isEmpty()) {
			writer.println("soundDevice:" + SoundFix.currentOutputDevice);
		} else {
			writer.println("soundDevice:\"\"");
		}
		writer.println("directionalAudio:" + SoundFix.hrtfEnabled);
	}
}
