package com.hhst.youtubelite.util;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hhst.youtubelite.R;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

/**
 * Image helpers for loading and resizing thumbnails.
 */
public final class ImageUtils {

	private static final int THUMB = R.drawable.bg_thumbnail_placeholder;

	private ImageUtils() {
	}

	public static void loadThumb(@NonNull ImageView view, @Nullable String url) {
		loadThumb(view, url, null);
	}

	public static void loadThumb(@NonNull ImageView view,
	                             @Nullable String url,
	                             @Nullable Callback callback) {
		if (url == null || url.isBlank()) {
			showThumb(view);
			return;
		}
		// .fit() decodes the thumbnail at the ImageView's own size instead of the full
		// source resolution (e.g. 1280x720) — a big memory/bandwidth saving in lists.
		var request = Picasso.get()
						.load(url)
						.fit()
						.placeholder(THUMB)
						.error(THUMB);
		if (callback == null) {
			request.into(view);
			return;
		}
		request.into(view, callback);
	}

	/**
	 * Loads an image at its full source resolution (no downscaling) — for the gallery's
	 * zoomable PhotoView, where a downscaled bitmap would look blurry when zoomed.
	 */
	public static void loadFull(@NonNull ImageView view,
	                            @Nullable String url,
	                            @Nullable Callback callback) {
		if (url == null || url.isBlank()) {
			showThumb(view);
			return;
		}
		var request = Picasso.get()
						.load(url)
						.placeholder(THUMB)
						.error(THUMB);
		if (callback == null) {
			request.into(view);
			return;
		}
		request.into(view, callback);
	}

	public static void showThumb(@NonNull ImageView view) {
		view.setImageResource(THUMB);
	}
}
