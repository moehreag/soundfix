package io.github.moehreag.soundfix.mixin;

import java.util.List;
import java.util.stream.Stream;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.moehreag.soundfix.SoundFix;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.GameOptionsScreen;
import net.minecraft.client.gui.screen.options.SoundOptionsScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundsScreenMixin extends GameOptionsScreen {

	@Unique
	private List<StringRenderable> cachedHrtfOnTooltip;
	@Unique
	private List<StringRenderable> cachedHrtfOffTooltip;

	private SoundsScreenMixin(Screen screen, GameOptions gameOptions, Text text) {
		super(screen, gameOptions, text);
	}

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/options/SoundOptionsScreen;addButton(Lnet/minecraft/client/gui/widget/AbstractButtonWidget;)Lnet/minecraft/client/gui/widget/AbstractButtonWidget;", ordinal = 2))
	private AbstractButtonWidget addSubtitlesButton(SoundOptionsScreen instance, AbstractButtonWidget abstractButtonWidget, Operation<AbstractButtonWidget> original) {
		abstractButtonWidget.x = width / 2 + 5;
		abstractButtonWidget.y -= 24;
		abstractButtonWidget.setWidth(150);
		return original.call(instance, abstractButtonWidget);
	}

	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/options/SoundOptionsScreen;addButton(Lnet/minecraft/client/gui/widget/AbstractButtonWidget;)Lnet/minecraft/client/gui/widget/AbstractButtonWidget;", ordinal = 3))
	private void addAdditionalButtons(CallbackInfo ci, @Local int i) {
		cachedHrtfOnTooltip = textRenderer.wrapLines(new TranslatableText("options.directionalAudio.on.tooltip"), 170);
		cachedHrtfOffTooltip = textRenderer.wrapLines(new TranslatableText("options.directionalAudio.off.tooltip"), 170);
		addButton(new ButtonWidget(this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), 150, 20, SoundFix.getOutputDeviceButtonMessage(), button -> {
			List<String> devices = Stream.concat(Stream.of(""), SoundFix.engine.getAvailableSoundDevices().stream()).toList();
			SoundFix.currentOutputDevice = devices.get((devices.indexOf(SoundFix.currentOutputDevice) + 1) % devices.size());
			button.setMessage(SoundFix.getOutputDeviceButtonMessage());
			reloadSoundSystem(button);
		}));
		i++;
		addButton(new ButtonWidget(this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), 150, 20, SoundFix.getHrtfEnabledButtonMessage(), button -> {
			SoundFix.hrtfEnabled = !SoundFix.hrtfEnabled;
			button.setMessage(SoundFix.getHrtfEnabledButtonMessage());
			reloadSoundSystem(button);
		}, (buttonWidget, matrixStack, mouseX, mouseY) -> {
			if (buttonWidget.isHovered()) {
				renderTooltip(matrixStack, SoundFix.hrtfEnabled ? cachedHrtfOnTooltip : cachedHrtfOffTooltip, mouseX, mouseY);
			}
		}));
	}

	@Unique
	private void reloadSoundSystem(ButtonWidget button) {
		SoundFix.engine.reload();
		button.playDownSound(client.getSoundManager());
		gameOptions.write();
	}
}
