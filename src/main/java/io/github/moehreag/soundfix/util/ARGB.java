package io.github.moehreag.soundfix.util;

public class ARGB {
	public static int color(int alpha, int red, int green, int blue) {
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	static int floor(float value) {
		int i = (int) value;
		return value < (float) i ? i - 1 : i;
	}

	public static int colorFromFloat(float alpha, float red, float green, float blue) {
		return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
	}

	public static int as8BitChannel(float value) {
		return floor(value * 255.0F);
	}
}
