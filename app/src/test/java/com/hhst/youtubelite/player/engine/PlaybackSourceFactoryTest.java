package com.hhst.youtubelite.player.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies that progressive-DASH manifests generated for direct streams get SDR color info
 * (BT709 / limited / SDR) injected into their video representation. Without it, ExoPlayer
 * builds a Format with no ColorInfo and some devices render AVC streams with a wrong color
 * cast (e.g. blue-ish skin).
 */
public class PlaybackSourceFactoryTest {

	private static final String VIDEO_MANIFEST =
					"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"
									+ "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
									+ " xmlns=\"urn:mpeg:DASH:schema:MPD:2011\""
									+ " xsi:schemaLocation=\"urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd\""
									+ " minBufferTime=\"PT1.500S\" profiles=\"urn:mpeg:dash:profile:full:2011\""
									+ " type=\"static\" mediaPresentationDuration=\"PT241.134S\">"
									+ "<Period>"
									+ "<AdaptationSet id=\"0\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\">"
									+ "<Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>"
									+ "<Representation id=\"137\" codecs=\"avc1.640028\" startWithSAP=\"1\""
									+ " maxPlayoutRate=\"1\" bandwidth=\"4706943\" width=\"1920\" height=\"1080\""
									+ " frameRate=\"30\">"
									+ "<BaseURL>https://example.com/videoplayback</BaseURL>"
									+ "<SegmentBase indexRange=\"674-7075\"><Initialization range=\"0-673\"/></SegmentBase>"
									+ "</Representation>"
									+ "</AdaptationSet>"
									+ "</Period></MPD>";

	private static final String AUDIO_MANIFEST =
					"<MPD><Period><AdaptationSet id=\"0\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\">"
									+ "<Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>"
									+ "<Representation id=\"140\" codecs=\"mp4a.40.2\" startWithSAP=\"1\""
									+ " maxPlayoutRate=\"1\" bandwidth=\"129376\" audioSamplingRate=\"44100\">"
									+ "<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\""
									+ " value=\"2\"/>"
									+ "<BaseURL>https://example.com/audio</BaseURL>"
									+ "</Representation></AdaptationSet></Period></MPD>";

	@Test
	public void videoManifest_getsSdrColorInfoInjected() {
		String result = PlaybackSourceFactory.ensureSdrColorInfo(VIDEO_MANIFEST);

		assertTrue(result.contains("frameRate=\"30\" videoRange=\"limited\""
						+ " colourPrimaries=\"BT709\" transferCharacteristics=\"SDR\""
						+ " matrixCoefficients=\"BT709\">"));
		// The surrounding structure must be preserved.
		assertTrue(result.contains("<AdaptationSet id=\"0\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\">"));
		assertTrue(result.contains("<Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>"));
		assertTrue(result.contains("<BaseURL>https://example.com/videoplayback</BaseURL>"));
		assertFalse(result.contains("videoRange=\"limited\" colourPrimaries=\"BT709\"</Representation>"));
	}

	@Test
	public void manifestWithExistingColorInfo_isLeftUntouched() {
		String manifest = VIDEO_MANIFEST.replace(
						" frameRate=\"30\">",
						" frameRate=\"30\" videoRange=\"full\" colourPrimaries=\"BT709\">");

		assertEquals(manifest, PlaybackSourceFactory.ensureSdrColorInfo(manifest));
	}

	@Test
	public void audioManifest_isLeftUntouched() {
		assertEquals(AUDIO_MANIFEST, PlaybackSourceFactory.ensureSdrColorInfo(AUDIO_MANIFEST));
	}

	@Test
	public void emptyOrNullInput_isLeftUntouched() {
		assertEquals("", PlaybackSourceFactory.ensureSdrColorInfo(""));
		assertEquals("not xml at all", PlaybackSourceFactory.ensureSdrColorInfo("not xml at all"));
	}
}
