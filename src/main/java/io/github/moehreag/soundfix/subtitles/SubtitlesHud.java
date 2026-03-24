package io.github.moehreag.soundfix.subtitles;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.render.platform.GlStateManager;
import io.github.moehreag.soundfix.SoundFix;
import io.github.moehreag.soundfix.b3d_audio.ListenerTransform;
import io.github.moehreag.soundfix.sounds.SoundEventListener;
import io.github.moehreag.soundfix.util.ARGB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.Window;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class SubtitlesHud extends GuiElement implements SoundEventListener {

	private static final Logger LOGGER = LogManager.getLogger("Subtitles");
	private static final Identifier SUBTITLES = new Identifier("soundfix", "subtitles.json");

	private static final long DISPLAY_TIME = 3000L;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Map<String, String> soundSubtitles = new HashMap<>();
	private final List<Subtitle> subtitles = new ArrayList<>();
	private boolean isListening;
	private final List<Subtitle> audibleSubtitles = new ArrayList<>();
	public boolean showSubtitles;
	public float notificationDisplayTime = 1;

	private static final SubtitlesHud instance = new SubtitlesHud();

	private SubtitlesHud() {
	}

	public static SubtitlesHud getInstance() {
		return instance;
	}

	public String getSubtitlesOptionMessage() {
		return I18n.translate("options.showSubtitles", showSubtitles ? I18n.translate("options.on") : I18n.translate("options.off"));
	}

	public void render() {
		if (!this.isListening && showSubtitles) {
			SoundFix.engine.addEventListener(this);
			this.isListening = true;
		} else if (this.isListening && !showSubtitles) {
			SoundFix.engine.removeEventListener(this);
			this.isListening = false;
		}

		if (this.isListening) {
			ListenerTransform listenerTransform = SoundFix.engine.getListenerTransform();
			Vec3d pos = listenerTransform.position();
			Vec3d fw = listenerTransform.forward();
			Vec3d right = listenerTransform.right();
			this.audibleSubtitles.clear();

			for (Subtitle subtitle : this.subtitles) {
				if (subtitle.isAudibleFrom(pos)) {
					this.audibleSubtitles.add(subtitle);
				}
			}

			if (!this.audibleSubtitles.isEmpty()) {
				int index = 0;
				int maxWidth = 0;
				double d = notificationDisplayTime;
				Iterator<Subtitle> iterator = this.audibleSubtitles.iterator();

				while (iterator.hasNext()) {
					Subtitle subtitle2 = iterator.next();
					subtitle2.purgeOldInstances(DISPLAY_TIME * d);
					if (!subtitle2.isStillActive()) {
						iterator.remove();
					} else {
						maxWidth = Math.max(maxWidth, this.minecraft.textRenderer.getWidth(subtitle2.getText()));
					}
				}

				maxWidth += this.minecraft.textRenderer.getWidth("<") + this.minecraft.textRenderer.getWidth(" ") * 2 + this.minecraft.textRenderer.getWidth(">");

				Window window = new Window(minecraft);
				GlStateManager.pushMatrix();
				int halfWidth = maxWidth / 2;
				int lineHeight = minecraft.textRenderer.fontHeight;
				int halfLineHeight = lineHeight / 2;
				GlStateManager.translated(window.getScaledWidth() - halfWidth - 2.0F, window.getScaledHeight() - 35, 0.0F);
				for (Subtitle subtitle2 : this.audibleSubtitles) {
					String component = subtitle2.getText();
					SoundPlayedAt soundPlayedAt = subtitle2.getClosest(pos);
					if (soundPlayedAt != null) {
						Vec3d relativePos = soundPlayedAt.location.subtract(pos).normalize();
						double e = right.dot(relativePos);
						double f = fw.dot(relativePos);
						boolean bl = f > 0.5;
						float scale = 1.0F;
						int o = this.minecraft.textRenderer.getWidth(component);
						int color = MathHelper.floor(MathHelper.clampedLerp(255.0F, 75.0F, (float) (System.currentTimeMillis() - soundPlayedAt.time) / (float) (DISPLAY_TIME * d)));
						GlStateManager.pushMatrix();
						GlStateManager.translated(0, -(index * (lineHeight + 1)), 0.0F);
						GlStateManager.scalef(scale, scale, scale);
						fill(-halfWidth - 1, -halfLineHeight - 1, halfWidth + 1, halfLineHeight + 1, ARGB.colorFromFloat(0.8f, 0, 0, 0));
						int q = ARGB.color(255, color, color, color);
						if (!bl) {
							if (e > 0.0) {
								drawString(this.minecraft.textRenderer, ">", halfWidth - this.minecraft.textRenderer.getWidth(">"), -halfLineHeight, q);
							} else if (e < 0.0) {
								drawString(this.minecraft.textRenderer, "<", -halfWidth, -halfLineHeight, q);
							}
						}

						drawString(this.minecraft.textRenderer, component, -o / 2, -halfLineHeight, q);
						GlStateManager.popMatrix();
						index++;
					}
				}
				GlStateManager.popMatrix();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void reload(ResourceManager manager, Gson gson) {
		soundSubtitles.clear();
		try (var in = manager.getResource(SUBTITLES).asStream();
			 var reader = new InputStreamReader(in)) {
			Map<String, String> read = (Map<String, String>) gson.fromJson(reader, TypeToken.getParameterized(Map.class, String.class, String.class));
			soundSubtitles.putAll(read);
		} catch (IOException e) {
			LOGGER.warn("Failed to load subtitles!");
		}
	}

	@Override
	public void onPlaySound(SoundInstance sound, SoundPool accessor, float range) {
		if (soundSubtitles.containsKey(accessor.getLocation().getPath())) {
			String component = I18n.translate(soundSubtitles.get(accessor.getLocation().getPath()));
			if (!this.subtitles.isEmpty()) {
				for (Subtitle subtitle : this.subtitles) {
					if (subtitle.getText().equals(component)) {
						subtitle.refresh(new Vec3d(sound.getX(), sound.getY(), sound.getZ()));
						return;
					}
				}
			}

			this.subtitles.add(new Subtitle(component, range, new Vec3d(sound.getX(), sound.getY(), sound.getZ())));
		}
	}

	record SoundPlayedAt(Vec3d location, long time) {
	}

	static class Subtitle {
		private final String text;
		private final float range;
		private final List<SoundPlayedAt> playedAt = new ArrayList<>();

		public Subtitle(String text, float range, Vec3d location) {
			this.text = text;
			this.range = range;
			this.playedAt.add(new SoundPlayedAt(location, System.currentTimeMillis()));
		}

		public String getText() {
			return this.text;
		}

		@Nullable
		public SoundPlayedAt getClosest(Vec3d location) {
			if (this.playedAt.isEmpty()) {
				return null;
			} else {
				return this.playedAt.size() == 1
					? this.playedAt.get(0)
					: this.playedAt
					.stream()
					.min(Comparator.comparingDouble(soundPlayedAt -> soundPlayedAt.location().distanceTo(location)))
					.orElse(null);
			}
		}

		public void refresh(Vec3d location) {
			this.playedAt.removeIf(soundPlayedAt -> location.equals(soundPlayedAt.location()));
			this.playedAt.add(new SoundPlayedAt(location, System.currentTimeMillis()));
		}

		public boolean isAudibleFrom(Vec3d location) {
			if (Float.isInfinite(this.range)) {
				return true;
			} else if (this.playedAt.isEmpty()) {
				return false;
			} else {
				var soundPlayedAt = this.getClosest(location);
				return soundPlayedAt != null && closerThan(location, soundPlayedAt.location, this.range);
			}
		}

		private boolean closerThan(Vec3d first, Vec3d pos, double distance) {
			return first.squaredDistanceTo(pos) < distance * distance;
		}

		public void purgeOldInstances(double displayTime) {
			long l = System.currentTimeMillis();
			this.playedAt.removeIf(soundPlayedAt -> (double) (l - soundPlayedAt.time()) > displayTime);
		}

		public boolean isStillActive() {
			return !this.playedAt.isEmpty();
		}
	}
}
