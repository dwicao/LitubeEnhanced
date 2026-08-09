package com.hhst.youtubelite.player.history;

import androidx.annotation.Nullable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One local watch-history entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class WatchHistoryItem {
	@Nullable
	private String videoId;
	@Nullable
	private String title;
	@Nullable
	private String thumbnailUrl;
	private long timestamp;
}
