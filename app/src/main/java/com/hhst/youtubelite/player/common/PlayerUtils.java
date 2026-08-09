package com.hhst.youtubelite.player.common;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.util.StringUtils;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stream selection helpers.
 */
@UnstableApi
public final class PlayerUtils {
	private static final Pattern FPS_SUFFIX = Pattern.compile("p(\\d+)$", Pattern.CASE_INSENSITIVE);

	public static boolean isPortrait(@NonNull Engine engine) {
		int videoWidth = engine.getVideoSize().width;
		int videoHeight = engine.getVideoSize().height;
		return videoWidth > 0 && videoHeight > 0 && videoHeight > videoWidth;
	}

	@NonNull
	public static List<VideoStream> filterBestStreams(@Nullable List<VideoStream> streams) {
		if (streams == null || streams.isEmpty()) return new ArrayList<>();

		Map<String, VideoStream> best = new HashMap<>();

		for (VideoStream stream : streams) {
			String res = stream.getResolution();
			String key = res + "#" + stream.getFps();
			VideoStream prev = best.get(key);

			if (prev == null || isBetterStream(stream, prev)) best.put(key, stream);
		}

		List<VideoStream> result = new ArrayList<>(best.values());
		result.sort((s1, s2) -> {
			int h1 = streamHeight(s1);
			int h2 = streamHeight(s2);
			if (h1 != h2) return Integer.compare(h2, h1);
			return Integer.compare(s2.getFps(), s1.getFps());
		});
		return result;
	}

	@NonNull
	public static List<String> sortResolutionLabels(@NonNull List<String> resolutions) {
		List<String> sorted = new ArrayList<>(resolutions);
		sorted.sort((left, right) -> {
			int height = Integer.compare(StringUtils.parseHeight(right), StringUtils.parseHeight(left));
			if (height != 0) return height;
			int fps = Integer.compare(parseFps(right), parseFps(left));
			if (fps != 0) return fps;
			return left.compareTo(right);
		});
		return sorted;
	}

	private static int streamHeight(@NonNull VideoStream stream) {
		int height = stream.getHeight();
		return height > 0 ? height : StringUtils.parseHeight(stream.getResolution());
	}

	private static int parseFps(@Nullable String resolution) {
		if (resolution == null) return 0;
		Matcher matcher = FPS_SUFFIX.matcher(resolution);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	public static boolean isBetterStream(@NonNull VideoStream s1, @NonNull VideoStream s2) {
		int p1 = getCodecPriority(s1.getCodec());
		int p2 = getCodecPriority(s2.getCodec());
		if (p1 != p2) return p1 > p2;

		if (s1.getFps() != s2.getFps()) return s1.getFps() > s2.getFps();

		return s1.getBitrate() > s2.getBitrate();
	}

	public static int getCodecPriority(@Nullable String codec) {
		if (codec == null) return 0;
		String lower = codec.toLowerCase(Locale.ROOT);
		if (lower.startsWith("avc") || lower.startsWith("h264")) return 4;
		if (lower.contains("vp9") || lower.contains("vp8")) return 3;
		if (lower.contains("h265")) return 2;
		if (lower.contains("av01")) return 1;

		return 0;
	}

	@Nullable
	public static VideoStream selectVideoStream(@Nullable List<VideoStream> streams, @Nullable String targetRes) {
		if (streams == null || streams.isEmpty()) return null;
		// Issue #290: a 1080p device crashed on 4K VP9 (NO_EXCEEDS_CAPABILITIES). Prefer only
		// streams this device's decoders can actually play, so selection automatically falls
		// back to the best supported resolution (e.g. 1080p) instead of picking 4K first.
		List<VideoStream> playable = filterPlayableStreams(streams);
		if (playable.isEmpty()) {
			// Nothing passes the capability check (or it could not be queried); keep the
			// original list rather than dropping playback entirely.
			playable = streams;
		}
		if (targetRes == null) return playable.get(0);

		for (VideoStream s : playable) if (s.getResolution().equals(targetRes)) return s;

		int targetHeight = StringUtils.parseHeight(targetRes);
		for (VideoStream s : playable) if (s.getHeight() <= targetHeight) return s;

		return playable.get(0);
	}

	/**
	 * Filters out video streams whose resolution exceeds what any decoder on this device can
	 * play for their codec (e.g. 4K VP9 on a 1080p device). Streams with unknown codec or
	 * unknown size are always kept.
	 */
	@NonNull
	public static List<VideoStream> filterPlayableStreams(@Nullable List<VideoStream> streams) {
		if (streams == null || streams.isEmpty()) return new ArrayList<>();
		List<VideoStream> playable = new ArrayList<>(streams.size());
		for (VideoStream stream : streams) {
			if (isVideoStreamPlayable(stream)) {
				playable.add(stream);
			}
		}
		return playable;
	}

	/**
	 * Whether the device has a decoder that can play this stream's codec at its resolution.
	 * Unknown codecs/sizes and any failure to query the codec list are treated as playable so
	 * that the capability check never hides valid streams.
	 */
	public static boolean isVideoStreamPlayable(@NonNull VideoStream stream) {
		return isVideoStreamPlayable(stream, false);
	}

	/**
	 * Same as {@link #isVideoStreamPlayable(VideoStream)} but with {@code strict} selection
	 * semantics: when a hardware decoder exists for the codec, only hardware decoders count
	 * (ExoPlayer prefers hardware decoders, so a size they cannot decode is exactly what
	 * causes NO_EXCEEDS_CAPABILITIES crashes — e.g. 4K VP9 on a 1080p-only device whose
	 * software VP9 decoder would otherwise claim support).
	 */
	public static boolean isVideoStreamPlayable(@NonNull VideoStream stream, boolean strict) {
		String mime = codecToMimeType(stream.getCodec());
		if (mime == null) return true;
		int height = stream.getHeight();
		if (height <= 0) height = StringUtils.parseHeight(stream.getResolution());
		if (height <= 0) return true;
		int width = stream.getWidth();
		if (width <= 0) {
			// YouTube resolutions are 16:9 (3840x2160, 1920x1080, ...); approximate the width
			// for the capability check when the extractor did not provide it.
			width = Math.max(2, (int) Math.round(height * 16.0 / 9.0 / 2) * 2);
		}
		int fps = stream.getFps();
		return strict
						? isSizeSupportedByPreferredDecoder(mime, width, height, fps)
						: isSizeSupportedByAnyDecoder(mime, width, height, fps);
	}

	/**
	 * Picks the best stream (highest height, then fps) from {@code streams} that this device
	 * can actually play, at or below {@code maxHeight} (ignored when {@code <= 0}). Returns
	 * null when nothing qualifies.
	 */
	@Nullable
	public static VideoStream bestPlayableStream(@NonNull List<VideoStream> streams,
	                                             int maxHeight,
	                                             boolean strict) {
		VideoStream best = null;
		int bestScore = -1;
		for (VideoStream stream : streams) {
			if (!isVideoStreamPlayable(stream, strict)) continue;
			int height = streamHeight(stream);
			if (height <= 0 || (maxHeight > 0 && height > maxHeight)) continue;
			int score = height * 1000 + Math.max(stream.getFps(), 0);
			if (score > bestScore) {
				best = stream;
				bestScore = score;
			}
		}
		return best;
	}

	@Nullable
	static String codecToMimeType(@Nullable String codec) {
		if (codec == null) return null;
		String lower = codec.toLowerCase(Locale.ROOT);
		if (lower.startsWith("avc") || lower.startsWith("h264")) return "video/avc";
		if (lower.contains("vp9")) return "video/x-vnd.on2.vp9";
		if (lower.contains("vp8")) return "video/x-vnd.on2.vp8";
		if (lower.contains("h265") || lower.contains("hevc")) return "video/hevc";
		if (lower.contains("av01") || lower.contains("av1")) return "video/av01";
		return null;
	}

	/**
	 * Result cache for {@link #isSizeSupportedByAnyDecoder}: keyed by mime + width x height.
	 */
	private static final Map<String, Boolean> DECODER_SIZE_SUPPORT_CACHE = new ConcurrentHashMap<>();
	/**
	 * Result cache for {@link #isSizeSupportedByPreferredDecoder}.
	 */
	private static final Map<String, Boolean> DECODER_PREFERRED_SIZE_SUPPORT_CACHE = new ConcurrentHashMap<>();

	/**
	 * Test seam: when set, replaces the real MediaCodecList query (which is unavailable in
	 * JVM unit tests). Production code never sets this.
	 */
	@Nullable
	static SizeSupportQuery sizeSupportQueryOverride;

	interface SizeSupportQuery {
		boolean query(@NonNull String mime, int width, int height, int fps);
	}

	private static boolean isSizeSupportedByAnyDecoder(@NonNull String mime, int width, int height, int fps) {
		return isSizeSupportedCached(DECODER_SIZE_SUPPORT_CACHE, mime, width, height, fps, false);
	}

	private static boolean isSizeSupportedByPreferredDecoder(@NonNull String mime, int width, int height, int fps) {
		return isSizeSupportedCached(DECODER_PREFERRED_SIZE_SUPPORT_CACHE, mime, width, height, fps, true);
	}

	private static boolean isSizeSupportedCached(@NonNull Map<String, Boolean> cache,
	                                             @NonNull String mime,
	                                             int width,
	                                             int height,
	                                             int fps,
	                                             boolean preferred) {
		SizeSupportQuery override = sizeSupportQueryOverride;
		String key = mime + "#" + width + "x" + height + "@" + fps;
		if (override == null) {
			Boolean cached = cache.get(key);
			if (cached != null) return cached;
		}
		boolean supported = preferred
						? queryPreferredDecoderSizeSupport(mime, width, height, fps)
						: queryDecoderSizeSupport(mime, width, height, fps);
		if (override == null) {
			cache.put(key, supported);
		}
		return supported;
	}

	private static boolean queryDecoderSizeSupport(@NonNull String mime, int width, int height, int fps) {
		SizeSupportQuery override = sizeSupportQueryOverride;
		if (override != null) {
			return override.query(mime, width, height, fps);
		}
		try {
			MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
			for (MediaCodecInfo info : list.getCodecInfos()) {
				if (info.isEncoder()) continue;
				for (String type : info.getSupportedTypes()) {
					if (!mime.equalsIgnoreCase(type)) continue;
					MediaCodecInfo.VideoCapabilities videoCaps =
									info.getCapabilitiesForType(type).getVideoCapabilities();
					if (videoCaps != null && supportsSize(videoCaps, width, height, fps)) {
						return true;
					}
				}
			}
			return false;
		} catch (Exception | LinkageError e) {
			// Unit-test stubs or unusual devices: don't filter when we cannot query.
			return true;
		}
	}

	/**
	 * Size support includes the frame rate: e.g. the Qualcomm VP9 decoder reports 3840x2160 as
	 * supported at 30fps but fails with NO_EXCEEDS_CAPABILITIES at 60fps (itag 315), which
	 * crashes playback. {@code isSizeSupported(w, h)} alone would wrongly accept it, so the
	 * rate-aware {@code areSizeAndRateSupported(w, h, fps)} check is used instead.
	 */
	private static boolean supportsSize(@NonNull MediaCodecInfo.VideoCapabilities videoCaps,
	                                    int width,
	                                    int height,
	                                    int fps) {
		return fps > 0
						? videoCaps.areSizeAndRateSupported(width, height, fps)
						: videoCaps.isSizeSupported(width, height);
	}

	/**
	 * Like {@link #queryDecoderSizeSupport} but mirrors ExoPlayer's decoder preference: when a
	 * hardware decoder exists for the codec, only hardware decoders count — a size they
	 * cannot decode causes NO_EXCEEDS_CAPABILITIES crashes even if a software decoder claims
	 * support. When no hardware decoder exists (or on API < 29 where
	 * {@code isHardwareAccelerated} is unavailable), falls back to the permissive query.
	 */
	private static boolean queryPreferredDecoderSizeSupport(@NonNull String mime, int width, int height, int fps) {
		SizeSupportQuery override = sizeSupportQueryOverride;
		if (override != null) {
			return override.query(mime, width, height, fps);
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			return queryDecoderSizeSupport(mime, width, height, fps);
		}
		try {
			MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
			boolean hardwareDecoderExists = false;
			boolean hardwareSupports = false;
			boolean anySupports = false;
			for (MediaCodecInfo info : list.getCodecInfos()) {
				if (info.isEncoder()) continue;
				for (String type : info.getSupportedTypes()) {
					if (!mime.equalsIgnoreCase(type)) continue;
					MediaCodecInfo.VideoCapabilities videoCaps =
									info.getCapabilitiesForType(type).getVideoCapabilities();
					if (videoCaps == null) continue;
					if (supportsSize(videoCaps, width, height, fps)) {
						anySupports = true;
					}
					if (info.isHardwareAccelerated()) {
						hardwareDecoderExists = true;
						if (supportsSize(videoCaps, width, height, fps)) {
							hardwareSupports = true;
						}
					}
				}
			}
			if (hardwareDecoderExists) return hardwareSupports;
			return anySupports;
		} catch (Exception | LinkageError e) {
			return true;
		}
	}

	@Nullable
	public static AudioStream selectAudioStream(@Nullable List<AudioStream> streams, @Nullable String preferredInfo) {
		if (streams == null || streams.isEmpty()) return null;
		if (preferredInfo == null) return streams.get(0);

		for (AudioStream as : streams) {
			int bitrate = as.getAverageBitrate();
			String bitrateStr = bitrate > 0 ? bitrate + "kbps" : "Unknown bitrate";
			String info = String.format(Locale.getDefault(), "%s - %s - %s", as.getFormat(), as.getCodec(), bitrateStr);
			if (info.equals(preferredInfo)) return as;
		}

		return streams.get(0);
	}
}
