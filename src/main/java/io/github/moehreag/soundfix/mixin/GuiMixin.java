package io.github.moehreag.soundfix.mixin;

import io.github.moehreag.soundfix.subtitles.SubtitlesHud;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class GuiMixin {

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientPlayerInteractionManager;hidesGui()Z", ordinal = 0))
	private void renderSubtitleHud(float tickDelta, CallbackInfo ci) {
		SubtitlesHud.getInstance().render();
	}
}
