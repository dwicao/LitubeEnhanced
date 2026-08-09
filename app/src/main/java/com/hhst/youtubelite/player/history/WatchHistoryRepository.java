package com.hhst.youtubelite.player.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Repository that persists the local watch history (videoId, title, thumbnail, timestamp)
 * in MMKV. Entries are de-duplicated by videoId (a re-watch moves the entry to the top)
 * and capped to {@link #MAX_ENTRIES}.
 */
@Singleton
public final class WatchHistoryRepository {
	static final String KEY_ITEMS = "watch_history_items";
	private static final int MAX_ENTRIES = 100;
	private static final Type LIST_TYPE = new TypeToken<List<WatchHistoryItem>>() {
	}.getType();

	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final Gson gson;

	@Inject
	public WatchHistoryRepository(@NonNull MMKV mmkv, @NonNull Gson gson) {
		this.mmkv = mmkv;
		this.gson = gson;
	}

	/**
	 * Records that a video was watched (or resumed). Re-watching an already-listed video
	 * moves it to the top with a fresh timestamp.
	 */
	public synchronized void record(@NonNull String videoId,
	                                @Nullable String title,
	                                @Nullable String thumbnailUrl) {
		List<WatchHistoryItem> items = readItems();
		items.removeIf(it -> Objects.equals(it.getVideoId(), videoId));
		items.add(0, new WatchHistoryItem(videoId, title, thumbnailUrl, System.currentTimeMillis()));
		while (items.size() > MAX_ENTRIES) {
			items.remove(items.size() - 1);
		}
		writeItems(items);
	}

	/**
	 * All history entries, newest first.
	 */
	@NonNull
	public synchronized List<WatchHistoryItem> getAll() {
		List<WatchHistoryItem> items = readItems();
		items.sort((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()));
		return items;
	}

	public synchronized boolean remove(@Nullable String videoId) {
		if (videoId == null) return false;
		List<WatchHistoryItem> items = readItems();
		boolean removed = items.removeIf(it -> Objects.equals(it.getVideoId(), videoId));
		if (removed) {
			writeItems(items);
		}
		return removed;
	}

	public synchronized void clear() {
		mmkv.removeValueForKey(KEY_ITEMS);
	}

	@NonNull
	private List<WatchHistoryItem> readItems() {
		String json = mmkv.decodeString(KEY_ITEMS, null);
		if (json == null || json.isBlank()) return new ArrayList<>();
		try {
			List<WatchHistoryItem> items = gson.fromJson(json, LIST_TYPE);
			return items != null ? items : new ArrayList<>();
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}

	private void writeItems(@NonNull List<WatchHistoryItem> items) {
		mmkv.encode(KEY_ITEMS, gson.toJson(items, LIST_TYPE));
	}
}
