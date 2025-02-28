package io.github.moehreag.soundfix.mixin;

import java.util.List;
import java.util.stream.Stream;

import io.github.moehreag.soundfix.SoundFix;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SoundsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundsScreen.class)
public abstract class SoundsScreenMixin extends Screen {

	@Shadow
	@Final
	private GameOptions options;
	@Unique
	private ButtonWidget enableHrtf;
	@Unique
	private List<String> cachedHrtfOnTooltip;
	@Unique
	private List<String> cachedHrtfOffTooltip;

	@Inject(method = "init", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 2))
	private void addAdditionalButtons(CallbackInfo ci) {
		cachedHrtfOnTooltip = textRenderer.split(I18n.translate("options.directionalAudio.on.tooltip"), 170);
		cachedHrtfOffTooltip = textRenderer.split(I18n.translate("options.directionalAudio.off.tooltip"), 170);
		int i = 10;
		buttons.add(new ButtonWidget(99, this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), 310, 20, SoundFix.getOutputDeviceButtonMessage()));
		i += 2;
		buttons.add(enableHrtf = new ButtonWidget(101, this.width / 2 - 75/*155 + i % 2 * 160*/, this.height / 6 - 12 + 24 * (i >> 1), 150, 20, SoundFix.getHrtfEnabledButtonMessage()));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void renderTooltip(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		if (enableHrtf.isHovered()) {
			renderTooltip(SoundFix.hrtfEnabled ? cachedHrtfOnTooltip : cachedHrtfOffTooltip, mouseX, mouseY);
		}
	}

	@Inject(method = "buttonClicked", at = @At("TAIL"))
	private void buttonClicked(ButtonWidget button, CallbackInfo ci) {
		if (button.id == 101) {
			SoundFix.hrtfEnabled = !SoundFix.hrtfEnabled;
			button.message = SoundFix.getHrtfEnabledButtonMessage();
		} else if (button.id == 99) {
			List<String> devices = Stream.concat(Stream.of(""), SoundFix.engine.getAvailableSoundDevices().stream()).toList();
			SoundFix.currentOutputDevice = devices.get((devices.indexOf(SoundFix.currentOutputDevice) + 1) % devices.size());
			button.message = SoundFix.getOutputDeviceButtonMessage();
		}
		if (button.id == 101 || button.id == 99) {
			minecraft.getSoundManager().reload(minecraft.getResourceManager());
			button.playClickSound(minecraft.getSoundManager());
			options.save();
		}
	}
}
