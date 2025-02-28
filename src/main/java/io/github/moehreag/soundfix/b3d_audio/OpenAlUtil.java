package io.github.moehreag.soundfix.b3d_audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

/**
 * The OpenALUtil class provides utility functions for working with OpenAL audio.
 */
@Environment(EnvType.CLIENT)
public class OpenAlUtil {
	private static final Logger LOGGER = LogManager.getLogger("OpenAL");

	/**
	 * Converts an OpenAL error code to a human-readable error message.
	 *
	 * @param errorCode The OpenAL error code to convert
	 * @return A String representing the error message for the given error code.
	 */
	private static String alErrorToString(int errorCode) {
		return switch (errorCode) {
			case 40961 -> "Invalid name parameter.";
			case 40962 -> "Invalid enumerated parameter value.";
			case 40963 -> "Invalid parameter parameter value.";
			case 40964 -> "Invalid operation.";
			case 40965 -> "Unable to allocate memory.";
			default -> "An unrecognized error occurred.";
		};
	}

	/**
	 * Checks for an OpenAL error and logs an error message if one is found.
	 *
	 * @param operationState A String describing the operation being performed when the error occurred
	 * @return true if an OpenAL error was found, false otherwise.
	 */
	static boolean checkALError(String operationState) {
		int i = AL10.alGetError();
		if (i != AL10.AL_NO_ERROR) {
			LOGGER.error("{}: {}", new Object[]{operationState, alErrorToString(i)});
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Converts an ALC error code to a human-readable error message.
	 *
	 * @param errorCode The ALC error code to convert
	 * @return A String representing the error message for the given error code.
	 */
	private static String alcErrorToString(int errorCode) {
		return switch (errorCode) {
			case ALC10.ALC_INVALID_DEVICE -> "Invalid device.";
			case ALC10.ALC_INVALID_CONTEXT -> "Invalid context.";
			case ALC10.ALC_INVALID_ENUM -> "Illegal enum.";
			case ALC10.ALC_INVALID_VALUE -> "Invalid value.";
			case ALC10.ALC_OUT_OF_MEMORY -> "Unable to allocate memory.";
			default -> "An unrecognized error occurred.";
		};
	}

	/**
	 * Checks for an ALC error and logs an error message if one is found.
	 *
	 * @param deviceHandle   The handle of the device to check for errors on
	 * @param operationState A String describing the operation being performed when the error occurred
	 * @return true if an ALC error was found, false otherwise.
	 */
	static boolean checkALCError(long deviceHandle, String operationState) {
		int i = ALC10.alcGetError(deviceHandle);
		if (i != ALC10.ALC_NO_ERROR) {
			LOGGER.error("{} ({}): {}", new Object[]{operationState, deviceHandle, alcErrorToString(i)});
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Converts an AudioFormat object to the corresponding OpenAL audio format code.
	 *
	 * @param format The AudioFormat object to convert
	 * @return An integer representing the corresponding OpenAL audio format code.
	 * @throws IllegalArgumentException if the given AudioFormat is not a supported format.
	 */
	static int audioFormatToOpenAl(AudioFormat format) {
		Encoding encoding = format.getEncoding();
		int i = format.getChannels();
		int j = format.getSampleSizeInBits();
		if (encoding.equals(Encoding.PCM_UNSIGNED) || encoding.equals(Encoding.PCM_SIGNED)) {
			if (i == 1) {
				if (j == 8) {
					return 4352;
				}

				if (j == 16) {
					return 4353;
				}
			} else if (i == 2) {
				if (j == 8) {
					return 4354;
				}

				if (j == 16) {
					return 4355;
				}
			}
		}

		throw new IllegalArgumentException("Invalid audio format: " + format);
	}
}
