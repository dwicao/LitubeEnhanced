package com.hhst.youtubelite.util;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.annotation.NonNull;

/**
 * Samsung DeX detection. DeX presents the app on a large desktop-like display; the Samsung
 * display flag (Display.FLAG_DEX_SIMULATED, an internal Samsung constant that has been stable
 * across One UI versions) is the primary signal, with a large-screen (>= 600dp) fallback for
 * DeX-like environments.
 */
public final class DexUtils {

	/**
	 * Samsung's internal display flag for DeX (not part of the public AOSP API).
	 */
	private static final int FLAG_DEX_SIMULATED = 0x2000;

	private DexUtils() {
	}

	/**
	 * Whether the app is currently running on Samsung DeX. Primary signal: Samsung's
	 * presentation-display flag. Fallback: DeX presents a large (>= 600dp) desktop-like
	 * screen — this also covers One UI versions where the internal display flag is not
	 * reported reliably. Note: large tablets also match the fallback; the DeX Mode toggle
	 * can force the mobile layout there if desired.
	 */
	public static boolean isDeXRunning(@NonNull Context context) {
		DisplayManager displayManager =
						(DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
		if (displayManager != null) {
			for (Display display : displayManager.getDisplays()) {
				if ((display.getFlags() & FLAG_DEX_SIMULATED) != 0) {
					return true;
				}
			}
		}
		Configuration config = context.getResources().getConfiguration();
		return config.smallestScreenWidthDp >= 600;
	}
}
