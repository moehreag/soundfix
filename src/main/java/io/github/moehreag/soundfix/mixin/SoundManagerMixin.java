package io.github.moehreag.soundfix.mixin;

import io.github.moehreag.soundfix.Engine;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.resource.ResourceManager;
import net.minecraft.client.sound.SoundManager;
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
	private SoundSystem soundSystem;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void replaceSoundEngine(ResourceManager resourceManager, GameOptions options, CallbackInfo ci) {
		if (soundSystem != null) {
			soundSystem.stopAll();
		}
		this.soundSystem = new Engine((SoundManager) (Object) this, options, resourceManager);
	}
}
