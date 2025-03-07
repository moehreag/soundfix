package io.github.moehreag.soundfix.mixin;

import com.google.gson.Gson;
import io.github.moehreag.soundfix.Engine;
import io.github.moehreag.soundfix.subtitles.SubtitlesHud;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.sound.system.SoundEngine;
import net.minecraft.client.sound.system.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

	@Shadow
	@Final
	@Mutable
	private SoundEngine engine;

	@Shadow
	@Final
	private static Gson GSON;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void replaceSoundEngine(ResourceManager resourceManager, GameOptions options, CallbackInfo ci) {
		if (engine != null) {
			engine.close();
		}
		this.engine = new Engine((SoundManager) (Object) this, options);
	}

	@Inject(method = "reload", at = @At("TAIL"))
	private void reloadSubtitles(ResourceManager resourceManager, CallbackInfo ci) {
		SubtitlesHud.getInstance().reload(resourceManager, GSON);
	}
}
