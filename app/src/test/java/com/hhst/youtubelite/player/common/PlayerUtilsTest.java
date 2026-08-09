package com.hhst.youtubelite.player.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtilsTest {

	@Test
	public void filterBestStreams_keepsOneChoicePerVisibleQuality() {
		VideoStream p1080 = createVideoStream("avc1", 1080, 30, 5_000_000);
		VideoStream duplicate = createVideoStream("vp9", 1080, 30, 4_000_000);
		VideoStream p720 = createVideoStream("avc1", 720, 60, 3_000_000);

		List<VideoStream> filtered = PlayerUtils.filterBestStreams(List.of(duplicate, p720, p1080));

		assertEquals(2, filtered.size());
		assertSame(p1080, filtered.get(0));
		assertSame(p720, filtered.get(1));
	}

	@Test
	public void filterBestStreams_sortsVisibleQualitiesByHeightThenFps() {
		VideoStream p1080 = createVideoStream("avc1", 0, "1080p30", 30, 5_000_000);
		VideoStream p1440 = createVideoStream("avc1", 0, "1440p", 30, 7_000_000);
		VideoStream p720 = createVideoStream("avc1", 0, "720p60", 60, 4_000_000);

		List<VideoStream> filtered = PlayerUtils.filterBestStreams(List.of(p1080, p1440, p720));

		assertEquals("1440p", filtered.get(0).getResolution());
		assertEquals("1080p30", filtered.get(1).getResolution());
		assertEquals("720p60", filtered.get(2).getResolution());
	}

	@Test
	public void sortResolutionLabels_usesHeightBeforeFpsSuffix() {
		List<String> sorted = PlayerUtils.sortResolutionLabels(List.of(
						"1080p30",
						"720p60",
						"1440p",
						"1080p60",
						"2160p",
						"720p30"));

		assertEquals(List.of(
						"2160p",
						"1440p",
						"1080p60",
						"1080p30",
						"720p60",
						"720p30"), sorted);
	}

	@Test
	public void filterBestStreams_handlesNullAndEmptyInputs() {
		assertTrue(PlayerUtils.filterBestStreams(null).isEmpty());
		assertTrue(PlayerUtils.filterBestStreams(new ArrayList<>()).isEmpty());
	}

	@Test
	public void selectVideoStream_usesSavedQualityWhenAvailable() {
		VideoStream p1080 = createVideoStream("avc1", 1080, 30, 5_000_000);
		VideoStream p720 = createVideoStream("avc1", 720, 30, 3_000_000);
		List<VideoStream> streams = List.of(p1080, p720);

		assertSame(p720, PlayerUtils.selectVideoStream(streams, "720p"));
		assertSame(p1080, PlayerUtils.selectVideoStream(streams, null));
		assertSame(p1080, PlayerUtils.selectVideoStream(streams, "2160p"));
		assertNull(PlayerUtils.selectVideoStream(null, "720p"));
	}

	@Test
	public void codecToMimeType_mapsKnownVideoCodecs() {
		assertEquals("video/avc", PlayerUtils.codecToMimeType("avc1.640028"));
		assertEquals("video/avc", PlayerUtils.codecToMimeType("h264"));
		assertEquals("video/x-vnd.on2.vp9", PlayerUtils.codecToMimeType("vp09.00.51.08"));
		assertEquals("video/x-vnd.on2.vp8", PlayerUtils.codecToMimeType("vp8"));
		assertEquals("video/hevc", PlayerUtils.codecToMimeType("h265"));
		assertEquals("video/hevc", PlayerUtils.codecToMimeType("hev1.1.6.L93.B0"));
		assertEquals("video/av01", PlayerUtils.codecToMimeType("av01.0.08M.08"));
		assertNull(PlayerUtils.codecToMimeType(null));
		assertNull(PlayerUtils.codecToMimeType("unknown-codec"));
	}

	@Test
	public void isVideoStreamPlayable_keepsUnknownCodecOrSize() {
		// Unknown codec: no capability check can be performed, so the stream stays playable.
		assertTrue(PlayerUtils.isVideoStreamPlayable(createVideoStream("unknown-codec", 2160, 30, 5_000_000)));
		// Unknown height (0) and unparseable resolution: nothing to verify, stays playable.
		VideoStream noSize = createVideoStream("vp9", 0, "", 30, 5_000_000);
		assertTrue(PlayerUtils.isVideoStreamPlayable(noSize));
	}

	@Test
	public void filterPlayableStreams_keepsAllWhenCapabilitiesCannotBeQueried() {
		// On the JVM there is no MediaCodecList; the check degrades to "playable".
		List<VideoStream> streams = List.of(
						createVideoStream("vp9", 2160, 30, 5_000_000),
						createVideoStream("avc1", 1080, 30, 4_000_000));
		assertEquals(2, PlayerUtils.filterPlayableStreams(streams).size());
		assertTrue(PlayerUtils.filterPlayableStreams(null).isEmpty());
	}

	@Test
	public void filterPlayableStreams_drops4kVp9WhenDecoderLacksSupport() {
		// Simulates a 1080p-only device (issue #290): the VP9 decoder rejects sizes above
		// 1080p, so 4K VP9 must be filtered out while 1080p VP9 and 4K AVC stay.
		PlayerUtils.sizeSupportQueryOverride =
						(mime, width, height, fps) -> !(mime.equals("video/x-vnd.on2.vp9") && height > 1080);
		try {
			VideoStream vp9_4k = createVideoStream("vp9", 2160, "2160p", 30, 10_000_000);
			VideoStream vp9_1080 = createVideoStream("vp9", 1080, "1080p", 30, 5_000_000);
			VideoStream avc_4k = createVideoStream("avc1", 2160, "2160p", 30, 8_000_000);

			List<VideoStream> playable = PlayerUtils.filterPlayableStreams(List.of(vp9_4k, vp9_1080, avc_4k));

			assertFalse(playable.contains(vp9_4k));
			assertTrue(playable.contains(vp9_1080));
			assertTrue(playable.contains(avc_4k));
		} finally {
			PlayerUtils.sizeSupportQueryOverride = null;
		}
	}

	@Test
	public void selectVideoStream_fallsBackToBestSupportedResolution() {
		// On a 1080p-only device, requesting 4K must select the best playable stream instead
		// of picking the unsupported 4K VP9 stream first.
		PlayerUtils.sizeSupportQueryOverride =
						(mime, width, height, fps) -> !(mime.equals("video/x-vnd.on2.vp9") && height > 1080);
		try {
			VideoStream vp9_4k = createVideoStream("vp9", 2160, "2160p", 30, 10_000_000);
			VideoStream vp9_1080 = createVideoStream("vp9", 1080, "1080p", 30, 5_000_000);

			assertSame(vp9_1080, PlayerUtils.selectVideoStream(List.of(vp9_4k, vp9_1080), "2160p"));
		} finally {
			PlayerUtils.sizeSupportQueryOverride = null;
		}
	}

	@Test
	public void isVideoStreamPlayable_strictConsultsDecoderCapabilities() {
		// Simulates a device whose VP9 decoder cannot exceed 1080p (issue #290): the strict
		// check used by the player's hard fallback drops 4K VP9 and keeps 1080p.
		PlayerUtils.sizeSupportQueryOverride =
						(mime, width, height, fps) -> !(mime.equals("video/x-vnd.on2.vp9") && height > 1080);
		try {
			VideoStream vp9_4k = createVideoStream("vp9", 2160, "2160p", 30, 10_000_000);
			VideoStream vp9_1080 = createVideoStream("vp9", 1080, "1080p", 30, 5_000_000);

			assertFalse(PlayerUtils.isVideoStreamPlayable(vp9_4k, true));
			assertTrue(PlayerUtils.isVideoStreamPlayable(vp9_1080, true));
		} finally {
			PlayerUtils.sizeSupportQueryOverride = null;
		}
	}

	@Test
	public void bestPlayableStream_returnsBestAtOrBelowHeight() {
		// The player's hard fallback uses this to swap a 4K stream for the best playable one.
		PlayerUtils.sizeSupportQueryOverride =
						(mime, width, height, fps) -> !(mime.equals("video/x-vnd.on2.vp9") && height > 1080);
		try {
			VideoStream vp9_4k = createVideoStream("vp9", 2160, "2160p", 30, 10_000_000);
			VideoStream vp9_1080 = createVideoStream("vp9", 1080, "1080p", 30, 5_000_000);
			VideoStream avc_720 = createVideoStream("avc1", 720, "720p", 30, 3_000_000);

			assertSame(vp9_1080,
							PlayerUtils.bestPlayableStream(List.of(vp9_4k, vp9_1080, avc_720), 2160, true));
			// The max-height cap is respected: at 720p only the 720p stream qualifies.
			assertSame(avc_720,
							PlayerUtils.bestPlayableStream(List.of(vp9_1080, avc_720), 720, true));
			assertNull(PlayerUtils.bestPlayableStream(List.of(vp9_4k), 2160, true));
		} finally {
			PlayerUtils.sizeSupportQueryOverride = null;
		}
	}

	@Test
	public void isVideoStreamPlayable_distinguishes4k30From4k60() {
		// Root cause of the reported crash: the Qualcomm VP9 decoder reports 3840x2160@30 as
		// supported but fails at 60fps (itag 315) with NO_EXCEEDS_CAPABILITIES. The capability
		// check must consider the frame rate, not just the size.
		PlayerUtils.sizeSupportQueryOverride =
						(mime, width, height, fps) -> !(mime.equals("video/x-vnd.on2.vp9") && height >= 2160 && fps > 30);
		try {
			VideoStream vp9_4k30 = createVideoStream("vp9", 2160, "2160p", 30, 10_000_000);
			VideoStream vp9_4k60 = createVideoStream("vp9", 2160, "2160p60", 60, 12_000_000);
			VideoStream vp9_1080p60 = createVideoStream("vp9", 1080, "1080p60", 60, 5_000_000);

			assertTrue(PlayerUtils.isVideoStreamPlayable(vp9_4k30, true));
			assertFalse(PlayerUtils.isVideoStreamPlayable(vp9_4k60, true));
			assertTrue(PlayerUtils.isVideoStreamPlayable(vp9_1080p60, true));
		} finally {
			PlayerUtils.sizeSupportQueryOverride = null;
		}
	}

	private VideoStream createVideoStream(String codec, int height, int fps, int bitrate) {
		return createVideoStream(codec, height, height + "p", fps, bitrate);
	}

	private VideoStream createVideoStream(String codec, int height, String resolution, int fps, int bitrate) {
		VideoStream stream = mock(VideoStream.class);
		when(stream.getCodec()).thenReturn(codec);
		when(stream.getHeight()).thenReturn(height);
		when(stream.getFps()).thenReturn(fps);
		when(stream.getBitrate()).thenReturn(bitrate);
		when(stream.getResolution()).thenReturn(resolution);
		return stream;
	}

}
