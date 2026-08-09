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
	 * Whether the app is currently running on Samsung DeX. Detection uses the Samsung
	 * presentation-display flag; a generic large-screen (tablet) fallback is deliberately
	 * NOT used so tablets/foldables keep the mobile layout unless the user enables the
	 * DeX Mode toggle manually.
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
		return false;
	}
}
