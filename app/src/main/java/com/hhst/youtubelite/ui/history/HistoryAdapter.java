package com.hhst.youtubelite.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hhst.youtubelite.R;
import com.hhst.youtubelite.player.history.WatchHistoryItem;
import com.hhst.youtubelite.util.ImageUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Adapter that binds local watch-history entries into the history bottom sheet list.
 */
public final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
	@NonNull
	private final List<WatchHistoryItem> items = new ArrayList<>();
	@NonNull
	private final Actions actions;

	public HistoryAdapter(@NonNull Actions actions) {
		this.actions = actions;
	}

	public void submitList(@NonNull List<WatchHistoryItem> newItems) {
		items.clear();
		items.addAll(newItems);
		notifyDataSetChanged();
	}

	public void removeItem(@NonNull String videoId) {
		int index = -1;
		for (int i = 0; i < items.size(); i++) {
			if (videoId.equals(items.get(i).getVideoId())) {
				index = i;
				break;
			}
		}
		if (index >= 0) {
			items.remove(index);
			notifyItemRemoved(index);
		}
	}

	@NonNull
	public List<WatchHistoryItem> snapshotItems() {
		return new ArrayList<>(items);
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = LayoutInflater.from(parent.getContext())
						.inflate(R.layout.item_history_entry, parent, false);
		return new ViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		holder.bind(items.get(position), actions);
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	/**
	 * Contract for app logic.
	 */
	public interface Actions {
		void onPlayRequested(@NonNull WatchHistoryItem item);

		void onDeleteRequested(@NonNull WatchHistoryItem item);
	}

	/**
	 * Component that handles app logic.
	 */
	static final class ViewHolder extends RecyclerView.ViewHolder {
		@NonNull
		private final ImageView thumbnailView;
		@NonNull
		private final TextView titleView;
		@NonNull
		private final TextView timeView;
		@NonNull
		private final ImageButton deleteButton;

		ViewHolder(@NonNull View itemView) {
			super(itemView);
			thumbnailView = itemView.findViewById(R.id.history_item_thumbnail);
			titleView = itemView.findViewById(R.id.history_item_title);
			timeView = itemView.findViewById(R.id.history_item_time);
			deleteButton = itemView.findViewById(R.id.history_item_delete);
		}

		void bind(@NonNull WatchHistoryItem item,
		          @NonNull Actions actions) {
			titleView.setText(item.getTitle() == null || item.getTitle().isBlank()
							? item.getVideoId()
							: item.getTitle());
			timeView.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
							.format(new Date(item.getTimestamp())));
			ImageUtils.loadThumb(thumbnailView, item.getThumbnailUrl());
			itemView.setOnClickListener(v -> actions.onPlayRequested(item));
			deleteButton.setOnClickListener(v -> actions.onDeleteRequested(item));
		}
	}
}
