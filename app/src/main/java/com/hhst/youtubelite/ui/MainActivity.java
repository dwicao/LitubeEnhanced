package com.hhst.youtubelite.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hhst.youtubelite.Constant;
import com.hhst.youtubelite.PlaybackService;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.browser.TabManager;
import com.hhst.youtubelite.browser.YoutubeWebview;
import com.hhst.youtubelite.downloader.ui.DownloadActivity;
import com.hhst.youtubelite.downloader.ui.DownloadDialog;
import com.hhst.youtubelite.downloader.ui.DownloadPermissionHost;
import com.hhst.youtubelite.downloader.ui.PlaylistDownloadDialog;
import com.hhst.youtubelite.downloader.ui.PlaylistDownloadItem;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.extractor.potoken.PoTokenHost;
import com.hhst.youtubelite.player.LitePlayer;
import com.hhst.youtubelite.player.common.PlayerLoopMode;
import com.hhst.youtubelite.player.queue.QueueItem;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.ui.queue.QueueAdapter;
import com.hhst.youtubelite.ui.queue.QueueTouch;
import com.hhst.youtubelite.util.DeviceUtils;
import com.hhst.youtubelite.util.DexUtils;
import com.hhst.youtubelite.util.PermissionUtils;
import com.hhst.youtubelite.util.ToastUtils;
import com.hhst.youtubelite.util.UrlUtils;
import com.hhst.youtubelite.util.ViewUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Primary screen that wires playback, queue, and download entry points.
 */
@AndroidEntryPoint
@UnstableApi
public final class MainActivity extends AppCompatActivity implements LifecycleEventObserver, DownloadPermissionHost {
	private static final String STATE_LAST_URL = "main.last_url";
	private final Handler handler = new Handler(Looper.getMainLooper());
	@Inject
	ExtensionManager extensionManager;
	@Inject
	TabManager tabManager;
	@Inject
	LitePlayer player;
	@Inject
	YoutubeExtractor youtubeExtractor;
	@Inject
	QueueRepository queueRepository;
	@Inject
	PoTokenHost poTokenHost;
	@Nullable
	private PlaybackService playbackService;
	@Nullable
	private ServiceConnection serviceConnection;
	@Nullable
	private TextView hintText;
	@NonNull
	private final Runnable hideHintRunnable = this::hideHint;
	private MainActivityViewModel viewModel;
	@Nullable
	private QueueSheet queueSheet;
	@Nullable
	private OnBackPressedCallback appBackCallback;
	private long lastBackTime;
	private boolean bootstrapped;
	private boolean suppressPiP;
	@Nullable
	private Runnable pendingPermissionAction;
	@Nullable
	private String restoredUrl;
	/**
	 * True once the RECORD_AUDIO permission has been requested at least once in this process,
	 * used together with shouldShowRequestPermissionRationale to detect a permanent denial
	 * ("don't ask again") and route the user to system settings.
	 */
	private boolean micPermissionRequested;
	@Nullable
	private AlertDialog micPermissionDialog;
	@Nullable
	private AudioManager audioManager;
	@Nullable
	private View playerRoot;
	private boolean musicMuted;
	private int lastMusicVolume = -1;
	private static final String DEX_WINDOW_PREFS = "dex_window";
	/**
	 * DeX only: opaque black overlay placed between the WebView and the player while the
	 * player is open. It blanks the page (no click-through possible) without hiding the
	 * WebView view itself — the page keeps rendering, so its player stays muted/paused via
	 * the injected sync script instead of autoplaying and stealing audio focus.
	 */
	@Nullable
	private View deXBlankOverlay;

	static boolean shouldEnterPictureInPicture(@Nullable LitePlayer player,
	                                           @Nullable ExtensionManager extensionManager,
	                                           final boolean isInPictureInPictureMode) {
		return !isInPictureInPictureMode
						&& extensionManager != null
						&& extensionManager.isEnabled(Constant.ENABLE_PIP)
						&& player != null
						&& player.shouldAutoEnterPictureInPicture();
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		super.onCreate(savedInstanceState);
		restoredUrl = savedInstanceState != null ? savedInstanceState.getString(STATE_LAST_URL) : null;
		viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
		viewModel.getState().observe(this, this::renderQueueSheet);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

		View mainView = findViewById(R.id.main);
		ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			Insets tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, tappable.bottom);
			return insets;
		});

		hintText = findViewById(R.id.activity_hint_text);
		if (hintText != null) {
			int pad = ViewUtils.dpToPx(this, 16);
			hintText.setPadding(pad, pad / 2, pad, pad / 2);
		}

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		playerRoot = findViewById(R.id.playerView);
		restoreDeXWindow();
		playerRoot.post(() -> {
			findViewById(R.id.btn_queue).setOnClickListener(v -> showQueueBottomSheet());
			findViewById(R.id.btn_mini_queue).setOnClickListener(v -> showQueueBottomSheet());
		});
		if (PermissionUtils.needsPostNotificationsPermission()
						&& !PermissionUtils.hasPostNotificationsPermission(this)) {
			ActivityCompat.requestPermissions(
							this,
							PermissionUtils.postNotificationsPermission(),
							PermissionUtils.REQUEST_POST_NOTIFICATIONS);
		}
		serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				playbackService = ((PlaybackService.PlaybackBinder) binder).getService();
				if (player != null && playbackService != null) {
					player.attachPlaybackService(playbackService);
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				playbackService = null;
			}
		};
		bindService(new Intent(this, PlaybackService.class), serviceConnection, Context.BIND_AUTO_CREATE);
		ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
		appBackCallback = new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				handleAppBack();
			}
		};
		getOnBackPressedDispatcher().addCallback(this, appBackCallback);

		// Initialize potoken dependency and open home page.
		long startupDeadlineMs = SystemClock.uptimeMillis() + 4_000L;

		mainView.post(new Runnable() {
			@Override
			public void run() {
				if (bootstrapped) {
					handleIntent(getIntent());
					return;
				}
				poTokenHost.prewarm();
				if (tabManager.getWebView() == null) {
					String initialUrl = restoredUrl;
					if (initialUrl == null || initialUrl.isBlank()) {
						initialUrl = Constant.HOME_URL;
					}
					tabManager.openTab(initialUrl, UrlUtils.getPageClass(initialUrl));
				}
				if (!poTokenHost.isReady() && SystemClock.uptimeMillis() < startupDeadlineMs) {
					handler.postDelayed(this, 100L);
					return;
				}
				bootstrapped = true;
				handleIntent(getIntent());
			}
		});
	}

	@Override
	protected void onNewIntent(@NonNull Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	protected void onUserLeaveHint() {
		super.onUserLeaveHint();
		boolean suppressAutoEnterPiP = suppressPiP;
		suppressPiP = false;
		if (!suppressAutoEnterPiP
						&& shouldEnterPictureInPicture(player, extensionManager, DeviceUtils.isInPictureInPictureMode(this))) {
			player.enterPictureInPicture();
		}
	}

	@Override
	public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
		player.onPictureInPictureModeChanged(isInPictureInPictureMode);
	}

	@Override
	public void onStateChanged(@NonNull androidx.lifecycle.LifecycleOwner source,
	                           @NonNull Lifecycle.Event event) {
		if (event != Lifecycle.Event.ON_STOP
						|| player == null
						|| DeviceUtils.isInPictureInPictureMode(this)
						|| extensionManager.isEnabled(Constant.ENABLE_BACKGROUND_PLAY)) {
			return;
		}
		player.suspendBackgroundPlayback();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// The activity is not recreated (see configChanges in the manifest); keep the
		// player's rotation/fullscreen state in sync with the new configuration.
		if (player != null) player.syncRotation(DeviceUtils.isRotateOn(this), newConfig.orientation);
		// DeX can be connected/disconnected while the app is running; switch the WebView
		// between the desktop and mobile layout accordingly.
		applyDeXModeToWebView();
	}

	/**
	 * DeX Mode keyboard shortcuts (when a video is open in the player): Space = play/pause,
	 * Left/Right = seek ±5s, Up/Down = volume, F = fullscreen, M = mute, Esc = exit
	 * fullscreen, Shift+Left/Right = queue previous/next, Ctrl+Shift+R = reload. Only active
	 * while running on DeX with the DeX Mode setting on; on phones these keys keep their
	 * normal behavior. The player takes focus when it opens (and on click), so shortcuts
	 * work out of the box; while the WebView (e.g. the search box) has focus, keystrokes are
	 * not stolen — only the explicit Ctrl+Shift+R reload still works there.
	 */
	@Override
	public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN
						&& player != null
						&& isDeXModeActive()
						&& player.isPlaybackOpen()
						&& !DeviceUtils.isInPictureInPictureMode(this)
						&& (webViewHasFocus() == false || isReloadShortcut(event))
						&& handleDeXShortcut(event)) {
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	private boolean isReloadShortcut(@NonNull KeyEvent event) {
		return event.getKeyCode() == KeyEvent.KEYCODE_R
						&& event.isCtrlPressed()
						&& event.isShiftPressed();
	}

	private boolean webViewHasFocus() {
		YoutubeWebview webView = getWebView();
		return webView != null && webView.hasFocus();
	}

	/**
	 * DeX Mode is only active on a real Samsung DeX display with the DeX Mode toggle on.
	 */
	private boolean isDeXModeActive() {
		return DexUtils.isDeXRunning(this)
						&& extensionManager != null
						&& extensionManager.isEnabled(Constant.DEX_MODE);
	}

	private boolean handleDeXShortcut(@NonNull KeyEvent event) {
		int keyCode = event.getKeyCode();
		if (keyCode == KeyEvent.KEYCODE_R
						&& event.isCtrlPressed()
						&& event.isShiftPressed()) {
			YoutubeWebview webView = getWebView();
			if (webView != null) webView.reload();
			return true;
		}
		if (event.isCtrlPressed() || event.isAltPressed()) return false;
		if (event.isShiftPressed()) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
				player.skipToPrevious();
				return true;
			}
			if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
				player.skipToNext();
				return true;
			}
			return false;
		}
		switch (keyCode) {
			case KeyEvent.KEYCODE_SPACE:
				if (event.getRepeatCount() == 0) player.togglePlayPause();
				return true;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				player.seekBy(-5000);
				return true;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				player.seekBy(5000);
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
				adjustVolumeBy(true);
				return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				adjustVolumeBy(false);
				return true;
			case KeyEvent.KEYCODE_F:
				// DeX: fullscreen is forced on (videos open fullscreen and can't exit), so
				// the F toggle is disabled here; outside DeX it toggles fullscreen normally.
				if (event.getRepeatCount() == 0 && !isDeXModeActive()) {
					if (player.isFullscreen()) player.exitFullscreen();
					else player.enterFullscreen();
				}
				return true;
			case KeyEvent.KEYCODE_M:
				if (event.getRepeatCount() == 0) toggleMute();
				return true;
			case KeyEvent.KEYCODE_ESCAPE:
				// Esc = back: in DeX it closes the video and returns to the previous page
				// (same as the Back button); outside DeX it exits fullscreen.
				if (event.getRepeatCount() == 0) {
					if (isDeXModeActive()) handleAppBack();
					else if (player.isFullscreen()) player.exitFullscreen();
				}
				return true;
			case KeyEvent.KEYCODE_T:
				// DeX: consume YouTube's page shortcut for theater mode — the desktop page
				// behind the fullscreen player must not re-layout (it causes the WebView
				// surface to die → blank screen).
				return true;
			default:
				return false;
		}
	}

	/**
	 * DeX Mode mouse support (only on DeX): scroll wheel over the player adjusts volume;
	 * secondary (right) click over the player opens a context menu (play/pause, fullscreen,
	 * PiP, download, reload).
	 */
	@Override
	public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
		if (isDeXModeActive() && player != null && player.isPlaybackOpen() && !DeviceUtils.isInPictureInPictureMode(this)) {
			int action = event.getActionMasked();
			// Phantom clicks (mouse primary button) delivered after a popup dismiss can
			// bypass the overlay and reach the WebView behind the fullscreen player. Consume
			// every primary-button press while the player is open; the secondary button is
			// kept for the context menu.
			if (action == MotionEvent.ACTION_BUTTON_PRESS
							&& (event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
				return true;
			}
			if (action == MotionEvent.ACTION_SCROLL && isEventOverPlayer(event)) {
				float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
				if (player.isInMiniPlayer()) {
					// DeX: scroll wheel over the floating player window resizes it.
					player.resizeInAppMiniPlayer(delta > 0 ? 30 : -30);
				} else {
					if (delta > 0) adjustVolumeBy(true);
					else if (delta < 0) adjustVolumeBy(false);
				}
				return true;
			}
			if (action == MotionEvent.ACTION_BUTTON_PRESS
							&& (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
							&& isEventOverPlayer(event)) {
				showPlayerContextMenu();
				return true;
			}
		}
		return super.dispatchGenericMotionEvent(event);
	}

	private boolean isEventOverPlayer(@NonNull MotionEvent event) {
		if (playerRoot == null || playerRoot.getVisibility() != View.VISIBLE) return false;
		Rect rect = new Rect();
		return playerRoot.getGlobalVisibleRect(rect)
						&& rect.contains((int) event.getRawX(), (int) event.getRawY());
	}

	/**
	 * DeX only: while the fullscreen player is open, the WebView page behind it must never
	 * receive clicks — dismissing the quality/speed popup can deliver a phantom click to
	 * the view below (the page), which would open a related video instead of changing
	 * quality. This listener consumes every touch that reaches the WebView while a video is
	 * open; when no video is open it returns false and the page works normally.
	 */
	private final View.OnTouchListener webViewTouchBlocker = (v, event) ->
					isDeXModeActive() && player != null && player.isPlaybackOpen();

	/**
	 * DeX only: while the player is open (always fullscreen in DeX), the WebView page
	 * behind it is hidden entirely (rendered blank) so clicks can never pass through to it
	 * — even phantom mouse/generic-motion clicks delivered after dismissing the quality
	 * popup. When the player closes the page is shown again (its surface is repaired by
	 * LitePlayerView.refreshWebViewAfterDeXFullscreenExit), so Back never leaves a blank
	 * screen.
	 */
	public void updateWebViewForDeXPlayer(boolean playerOpen) {
		YoutubeWebview webView = getWebView();
		if (webView != null) {
			if (playerOpen) {
				webView.setOnTouchListener(webViewTouchBlocker);
			} else {
				webView.setOnTouchListener(null);
			}
		}
		if (isDeXModeActive()) {
			// DeX: cover the page with an opaque blank overlay while the player is open —
			// visually blank and unclickable, but the WebView stays visible/rendering so the
			// page player sync script (mute + pause) still runs. Back removes the overlay.
			View overlay = ensureDeXBlankOverlay();
			overlay.setVisibility(playerOpen ? View.VISIBLE : View.GONE);
		}
	}

	private View ensureDeXBlankOverlay() {
		if (deXBlankOverlay != null) return deXBlankOverlay;
		View overlay = new View(this);
		overlay.setBackgroundColor(android.graphics.Color.BLACK);
		overlay.setClickable(true);
		// Consume every touch that reaches it — nothing can pass through to the page.
		overlay.setOnTouchListener((v, event) -> true);
		ViewGroup root = findViewById(R.id.main);
		if (root != null) {
			// Insert between the WebView container (index 0) and the player (index 1) so the
			// player stays on top.
			root.addView(overlay, 1, new ConstraintLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.MATCH_PARENT));
		}
		deXBlankOverlay = overlay;
		return overlay;
	}
	private void adjustVolumeBy(boolean up) {
		if (audioManager == null) return;
		audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
						up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
	}

	private void toggleMute() {
		if (audioManager == null) return;
		if (musicMuted) {
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(lastMusicVolume, 0), 0);
			musicMuted = false;
		} else {
			lastMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
			musicMuted = true;
		}
	}

	private void showPlayerContextMenu() {
		if (player == null) return;
		CharSequence[] items = {
						player.isPlaying() ? "Pause" : "Play",
						player.isFullscreen() ? "Exit fullscreen" : "Fullscreen",
						"Picture-in-picture",
						"Download",
						"Reload page"
		};
		new AlertDialog.Builder(this)
						.setTitle("Player")
						.setItems(items, (dialog, which) -> {
							switch (which) {
								case 0 -> player.togglePlayPause();
								case 1 -> {
									if (player.isFullscreen()) player.exitFullscreen();
									else player.enterFullscreen();
								}
								case 2 -> player.enterPictureInPicture();
								case 3 -> {
									String url = currentUrl();
									if (url != null && youtubeExtractor != null) {
										new DownloadDialog(url, this, youtubeExtractor).show();
									}
								}
								case 4 -> {
									YoutubeWebview webView = getWebView();
									if (webView != null) webView.reload();
								}
								default -> { }
							}
						})
						.show();
	}

	/**
	 * DeX Mode: keeps the WebView in sync with the current DeX state (desktop layout when
	 * running on DeX and the DeX Mode setting is on, mobile layout otherwise).
	 */
	private void applyDeXModeToWebView() {
		YoutubeWebview webView = getWebView();
		if (webView == null) return;
		boolean wantDeX = DexUtils.isDeXRunning(this)
						&& extensionManager != null
						&& extensionManager.isEnabled(Constant.DEX_MODE);
		webView.setDeXDesktopMode(wantDeX);
	}

	/**
	 * DeX Mode: remembers the window size/position so it can be restored next launch
	 * (best-effort; only meaningful for freeform DeX windows).
	 */
	private void saveDeXWindow() {
		if (!DexUtils.isDeXRunning(this)) return;
		try {
			WindowManager.LayoutParams attrs = getWindow().getAttributes();
			getSharedPreferences(DEX_WINDOW_PREFS, MODE_PRIVATE).edit()
							.putInt("w", getWindow().getDecorView().getWidth())
							.putInt("h", getWindow().getDecorView().getHeight())
							.putInt("x", attrs.x)
							.putInt("y", attrs.y)
							.apply();
		} catch (Exception ignored) {
		}
	}

	private void restoreDeXWindow() {
		if (!DexUtils.isDeXRunning(this)) return;
		try {
			SharedPreferences prefs = getSharedPreferences(DEX_WINDOW_PREFS, MODE_PRIVATE);
			int w = prefs.getInt("w", 0);
			int h = prefs.getInt("h", 0);
			if (w <= 0 || h <= 0) return;
			WindowManager.LayoutParams attrs = getWindow().getAttributes();
			attrs.width = w;
			attrs.height = h;
			attrs.x = prefs.getInt("x", attrs.x);
			attrs.y = prefs.getInt("y", attrs.y);
			getWindow().setAttributes(attrs);
		} catch (Exception ignored) {
		}
	}

	private void handleIntent(@Nullable Intent intent) {
		if (intent == null) return;
		String action = intent.getAction();
		boolean isDownloadAction = "TRIGGER_DOWNLOAD_FROM_SHARE".equals(action);

		if ("OPEN_DOWNLOADS".equals(action)) {
			startActivity(new Intent(this, DownloadActivity.class));
			return;
		}

		String url = null;
		if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
			url = intent.getData().toString();
		} else if (Intent.ACTION_SEND.equals(action) || isDownloadAction) {
			// Extract a shared YouTube URL from the incoming text.
			String text = intent.getStringExtra(Intent.EXTRA_TEXT);
			if (text != null) {
				Pattern pat = Pattern.compile("https?://[\\w./?=&%#-]+", Pattern.CASE_INSENSITIVE);
				Matcher m = pat.matcher(text);
				url = m.find() ? m.group() : null;
			}
		}

		if (url != null) {
			if (isDownloadAction) {
				String loadUrl = url.replace(Constant.YOUTUBE_MOBILE_HOST, "www.youtube.com");
				long fetchToast = ToastUtils.show(this, "Fetching download links...");
				handler.postDelayed(() -> ToastUtils.cancel(fetchToast), 1000);
				handler.postDelayed(() -> new DownloadDialog(loadUrl, this, youtubeExtractor).show(), 600);
			} else {
				String loadUrl = url.replace("www.youtube.com", Constant.YOUTUBE_MOBILE_HOST);
				if (tabManager != null) {
					tabManager.openTab(loadUrl, UrlUtils.getPageClass(loadUrl));
				}
			}
		} else if (tabManager.getWebView() == null) {
			tabManager.openTab(Constant.HOME_URL, UrlUtils.getPageClass(Constant.HOME_URL));
		}
	}

	private void showQueueBottomSheet() {
		if (DeviceUtils.isInPictureInPictureMode(this)) return;
		BottomSheetDialog dialog = new BottomSheetDialog(this);
		View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_queue, new android.widget.FrameLayout(this), false);
		dialog.setContentView(sheetView);

		ImageButton closeButton = sheetView.findViewById(R.id.btn_queue_close);
		SwitchMaterial enabledSwitch = sheetView.findViewById(R.id.switch_queue_enabled);
		ImageButton downloadButton = sheetView.findViewById(R.id.btn_queue_download);
		ImageButton orderButton = sheetView.findViewById(R.id.btn_queue_order);
		ImageButton clearButton = sheetView.findViewById(R.id.btn_queue_clear);
		TextView emptyView = sheetView.findViewById(R.id.queue_empty);
		RecyclerView recyclerView = sheetView.findViewById(R.id.queue_items_recycler);
		QueueAdapter adapter = new QueueAdapter(new QueueAdapter.Actions() {
			@Override
			public void onPlayRequested(@NonNull QueueItem item) {
				dialog.dismiss();
				if (item.getVideoUrl() != null) {
					tabManager.playInWatch(item.getVideoUrl());
				}
			}

			@Override
			public void onDeleteRequested(@NonNull QueueItem item) {
				new MaterialAlertDialogBuilder(MainActivity.this)
								.setMessage(R.string.remove_queue_item_confirmation)
								.setPositiveButton(R.string.confirm, (d, which) -> {
									String videoId = item.getVideoId();
									if (videoId == null) return;
									viewModel.removeQueueItem(videoId);
								})
								.setNegativeButton(R.string.cancel, null)
								.show();
			}
		});
		QueueSheet sheet = new QueueSheet(enabledSwitch, orderButton, emptyView, recyclerView, adapter);
		queueSheet = sheet;
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setAdapter(adapter);
		recyclerView.setNestedScrollingEnabled(true);
		recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
		new ItemTouchHelper(new QueueTouch(adapter::moveItem, new QueueTouch.DragStateCallback() {
			@Override
			public void onDragStateChanged(boolean dragging) {
				if (sheet.behavior != null) sheet.behavior.setDraggable(!dragging);
			}

			@Override
			public void onDragFinished() {
				viewModel.moveQueue(adapter.snapshotItems());
				if (sheet.behavior != null) sheet.behavior.setDraggable(true);
			}
		})).attachToRecyclerView(recyclerView);

		closeButton.setOnClickListener(v -> dialog.dismiss());
		enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (!buttonView.isPressed()) return;
			viewModel.setQueueEnabled(isChecked);
			ToastUtils.show(this, isChecked ? R.string.queue_enabled_on : R.string.queue_enabled_off);
		});
		downloadButton.setOnClickListener(v -> {
			dialog.dismiss();
			List<QueueItem> items = uiState().items();
			if (items.isEmpty()) {
				ToastUtils.show(this, R.string.queue_download_unavailable);
				return;
			}
			List<PlaylistDownloadItem> dialogItems = new java.util.ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				QueueItem queueItem = items.get(i);
				String videoId = queueItem.getVideoId() != null
								? queueItem.getVideoId()
								: YoutubeExtractor.getVideoId(queueItem.getVideoUrl());
				String itemUrl = queueItem.getVideoUrl() != null && !queueItem.getVideoUrl().isBlank()
								? queueItem.getVideoUrl()
								: videoId == null || videoId.isBlank()
								? null
								: "https://www.youtube.com/watch?v=" + videoId;
				PlaylistDownloadItem item = new PlaylistDownloadItem(
								i,
								videoId == null ? "unknown" : videoId,
								itemUrl == null ? "" : itemUrl);
				item.setTitle(queueItem.getTitle());
				item.setAuthor(queueItem.getAuthor());
				item.setThumbnailUrl(queueItem.getThumbnailUrl());
				if (videoId == null || itemUrl == null || itemUrl.isBlank()) {
					item.setAvailabilityStatus(PlaylistDownloadItem.AvailabilityStatus.LOAD_FAILED);
					item.setFailureReason(getString(R.string.playlist_download_status_failed));
					item.setSelected(false);
				} else {
					item.setAvailabilityStatus(PlaylistDownloadItem.AvailabilityStatus.READY);
					item.setSelected(true);
				}
				dialogItems.add(item);
			}
			new PlaylistDownloadDialog(
							getString(R.string.queue),
							dialogItems,
							null,
							null,
							this,
							youtubeExtractor,
							null).show();
		});
		orderButton.setOnClickListener(v -> {
			PlayerLoopMode newMode = uiState().loopMode().next();
			player.setLoopMode(newMode);
		});
		clearButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
						.setMessage(R.string.clear_queue_confirmation)
						.setPositiveButton(R.string.confirm, (d, which) -> viewModel.clearQueue())
						.setNegativeButton(R.string.cancel, null)
						.show());
		dialog.setOnShowListener(ignored -> {
			final android.widget.FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
			if (bottomSheet == null) return;
			BottomSheetBehavior<android.widget.FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
			sheet.behavior = behavior;
			int sheetBasePaddingBottom = sheetView.getPaddingBottom();
			int recyclerBasePaddingBottom = recyclerView.getPaddingBottom();
			int recyclerTrailingSpace = Math.round(getResources().getDisplayMetrics().density * 24);
			View mainView = findViewById(R.id.main);
			WindowInsetsCompat rootInsets = mainView != null
							? ViewCompat.getRootWindowInsets(mainView)
							: ViewCompat.getRootWindowInsets(bottomSheet);
			int bottomInset = rootInsets != null
							? rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
							: 0;
			sheetView.setPadding(
							sheetView.getPaddingLeft(),
							sheetView.getPaddingTop(),
							sheetView.getPaddingRight(),
							sheetBasePaddingBottom + Math.max(0, bottomInset));
			// Keep last row visible.
			recyclerView.setPadding(
							recyclerView.getPaddingLeft(),
							recyclerView.getPaddingTop(),
							recyclerView.getPaddingRight(),
							recyclerBasePaddingBottom + Math.max(Math.max(0, bottomInset), Math.max(0, recyclerTrailingSpace)));
			View playerRoot = findViewById(R.id.playerView);
			int mainHeight = mainView != null ? mainView.getHeight() : 0;
			int topInset = mainView != null ? mainView.getPaddingTop() : 0;
			int playerBottom = playerRoot != null ? playerRoot.getBottom() : 0;
			final int maxSheetHeight;
			if (mainHeight <= 0) {
				maxSheetHeight = 0;
			} else if (uiState().miniPlayer()) {
				maxSheetHeight = Math.max(0, mainHeight - Math.max(0, topInset));
			} else if (playerBottom <= 0 || playerBottom >= mainHeight) {
				maxSheetHeight = mainHeight;
			} else {
				maxSheetHeight = mainHeight - playerBottom;
			}
			final android.view.ViewGroup.LayoutParams bottomSheetLayoutParams = bottomSheet.getLayoutParams();
			if (bottomSheetLayoutParams != null && maxSheetHeight > 0) {
				bottomSheetLayoutParams.height = maxSheetHeight;
				bottomSheet.setLayoutParams(bottomSheetLayoutParams);
			}
			final android.view.ViewGroup.LayoutParams sheetLayoutParams = sheetView.getLayoutParams();
			if (sheetLayoutParams != null && maxSheetHeight > 0) {
				sheetLayoutParams.height = maxSheetHeight;
				sheetView.setLayoutParams(sheetLayoutParams);
			}
			behavior.setPeekHeight(maxSheetHeight > 0 ? maxSheetHeight : sheetView.getMeasuredHeight());
			behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
			sheet.scrollPending = true;
			renderQueueSheet(uiState());
		});
		dialog.setOnDismissListener(d -> {
			if (queueSheet == sheet) {
				queueSheet = null;
			}
		});
		renderQueueSheet(uiState());
		dialog.show();
	}

	@NonNull
	private MainActivityViewModel.UiState uiState() {
		final MainActivityViewModel.UiState state = viewModel.getState().getValue();
		if (state != null) return state;
		return new MainActivityViewModel.UiState(
						queueRepository.isEnabled(),
						queueRepository.getItems(),
						player.getVideoId(),
						player.getLoopMode(),
						player.isInMiniPlayer());
	}

	private void renderQueueSheet(@NonNull MainActivityViewModel.UiState state) {
		QueueSheet sheet = queueSheet;
		if (sheet == null) return;
		if (sheet.enabledSwitch.isChecked() != state.queueEnabled()) {
			sheet.enabledSwitch.setChecked(state.queueEnabled());
		}
		renderLoop(sheet.orderButton, state.loopMode());
		sheet.adapter.replaceItems(state.items(), state.videoId());
		boolean empty = state.items().isEmpty();
		sheet.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
		sheet.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
		if (sheet.scrollPending) {
			scrollQueueToPlaying(sheet.recyclerView, state.items(), state.videoId());
			sheet.scrollPending = false;
		}
	}

	private void scrollQueueToPlaying(@NonNull RecyclerView recyclerView,
	                                  @NonNull List<QueueItem> items,
	                                  @Nullable String playingId) {
		if (playingId == null) return;
		int playingPosition = -1;
		for (int i = 0; i < items.size(); i++) {
			if (playingId.equals(items.get(i).getVideoId())) {
				playingPosition = i;
				break;
			}
		}
		if (playingPosition < 0) return;
		int target = playingPosition;
		recyclerView.post(() -> {
			final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
			if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
				linearLayoutManager.scrollToPositionWithOffset(
								target,
								Math.max(0, recyclerView.getPaddingTop()) + Math.max(0, recyclerView.getHeight()) / 3);
				return;
			}
			recyclerView.scrollToPosition(target);
		});
	}

	private void renderLoop(@NonNull ImageButton button, @NonNull PlayerLoopMode mode) {
		switch (mode) {
			case PLAYLIST_NEXT -> {
				button.setImageResource(R.drawable.ic_playback_end_next);
				button.setContentDescription(getString(R.string.playback_end_next));
			}
			case LOOP_ONE -> {
				button.setImageResource(R.drawable.ic_playback_end_loop);
				button.setContentDescription(getString(R.string.playback_end_loop));
			}
			case PAUSE_AT_END -> {
				button.setImageResource(R.drawable.ic_playback_end_pause);
				button.setContentDescription(getString(R.string.playback_end_pause));
			}
			case PLAYLIST_RANDOM -> {
				button.setImageResource(R.drawable.ic_playback_end_shuffle);
				button.setContentDescription(getString(R.string.playback_end_playlist_random));
			}
		}
	}

	@Nullable
	private YoutubeWebview getWebView() {
		return tabManager != null ? tabManager.getWebView() : null;
	}

	@Nullable
	private String currentUrl() {
		YoutubeWebview webView = getWebView();
		if (webView == null) return null;
		String url = webView.getUrl();
		return url == null || url.isBlank() ? null : url;
	}

	/**
	 * Opens the download dialog for the currently open video (used by the DeX player's
	 * download button, which replaces the useless fullscreen button in DeX Mode).
	 */
	public void downloadCurrentVideo() {
		String url = currentUrl();
		if (url == null || youtubeExtractor == null) return;
		new DownloadDialog(url, this, youtubeExtractor).show();
	}

	public void handleAppBack() {
		if (DeviceUtils.isInPictureInPictureMode(this)) {
			if (appBackCallback != null) {
				appBackCallback.setEnabled(false);
			}
			getOnBackPressedDispatcher().onBackPressed();
			if (appBackCallback != null) {
				appBackCallback.setEnabled(true);
			}
			return;
		}
		if (player != null && player.isFullscreen()) {
			if (isDeXModeActive()) {
				// DeX: fullscreen is forced (no small mode), so back closes the video
				// entirely and returns to the page the user was on before.
				player.hide();
				YoutubeWebview webview = getWebView();
				if (webview != null && tabManager != null) {
					tabManager.evaluateJavascript("window.dispatchEvent(new Event('onGoBack'));", null);
				}
				if (tabManager != null) tabManager.goBack();
			} else {
				player.exitFullscreen();
			}
			return;
		}
		YoutubeWebview webview = getWebView();
		if (webview != null && tabManager != null) {
			tabManager.evaluateJavascript("window.dispatchEvent(new Event('onGoBack'));", null);
			if (webview.fullscreen != null && webview.fullscreen.getVisibility() == View.VISIBLE) {
				tabManager.evaluateJavascript("document.exitFullscreen()", null);
				return;
			}
		}
		if (tabManager != null && !tabManager.goBack()) {
			long time = System.currentTimeMillis();
			if (time - lastBackTime < 2_000L) finish();
			else {
				lastBackTime = time;
				ToastUtils.show(this, R.string.press_back_again_to_exit);
			}
		}
	}

	public void showHint(@NonNull String text, long durationMs) {
		if (hintText == null || DeviceUtils.isInPictureInPictureMode(this)) return;
		hintText.setText(text);
		hintText.setVisibility(View.VISIBLE);
		hintText.bringToFront();
		hintText.setTranslationZ(1000f);
		hintText.setAlpha(1.0f);
		ViewUtils.animateViewAlpha(hintText, 1.0f, View.GONE);
		handler.removeCallbacks(hideHintRunnable);
		if (durationMs > 0) {
			handler.postDelayed(hideHintRunnable, durationMs);
		}
	}

	public void hideHint() {
		if (hintText != null) {
			ViewUtils.animateViewAlpha(hintText, 0.0f, View.GONE);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		suppressPiP = false;
		if (player != null && player.isInMiniPlayer() && !DeviceUtils.isInPictureInPictureMode(this)) {
			player.restoreInAppMiniPlayerUiIfNeeded();
		}
	}

	@Override
	public void startActivity(@Nullable Intent intent) {
		suppressPiP = shouldSuppressPiPForStartedActivity(intent);
		super.startActivity(intent);
	}

	@Override
	public void startActivity(@Nullable Intent intent, @Nullable Bundle options) {
		suppressPiP = shouldSuppressPiPForStartedActivity(intent);
		super.startActivity(intent, options);
	}

	@Override
	protected void onStop() {
		if (player != null && player.isInMiniPlayer() && !isChangingConfigurations() && !DeviceUtils.isInPictureInPictureMode(this)) {
			player.suspendInAppMiniPlayerUiIfNeeded();
		}
		saveDeXWindow();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
		if (serviceConnection != null) unbindService(serviceConnection);
		if (!isChangingConfigurations() && player != null) player.release();
	}

	/**
	 * Ensures the app holds RECORD_AUDIO before WebView voice search captures audio. Called
	 * by YoutubeWebview.onPermissionRequest when YouTube's mic button is tapped: if the
	 * permission is already granted the deferred WebView request completes immediately;
	 * otherwise the system permission dialog is shown (or, if permanently denied, a dialog
	 * that leads to system settings). Must run on the UI thread.
	 */
	public void ensureMicrophonePermissionForVoiceSearch() {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			runOnUiThread(this::ensureMicrophonePermissionForVoiceSearch);
			return;
		}
		if (!PermissionUtils.hasMicrophonePermission(this)) {
			boolean everRequested = micPermissionRequested;
			micPermissionRequested = true;
			if (everRequested && !ActivityCompat.shouldShowRequestPermissionRationale(
							this, Manifest.permission.RECORD_AUDIO)) {
				// Previously denied with "don't ask again": the system dialog will no longer
				// appear, so route the user to the app's settings instead.
				showMicrophonePermissionSettingsDialog();
				return;
			}
			ActivityCompat.requestPermissions(
							this,
							PermissionUtils.microphonePermission(),
							PermissionUtils.REQUEST_RECORD_AUDIO);
			return;
		}
		YoutubeWebview webView = getWebView();
		if (webView != null) {
			webView.grantPendingMediaPermission(true);
		}
	}

	/**
	 * Shows the fallback dialog when the microphone permission is not granted and the system
	 * permission dialog can no longer be shown (e.g. "don't ask again"): "Yes" opens the
	 * app's permission screen in system settings, "No" shows a toast.
	 */
	private void showMicrophonePermissionSettingsDialog() {
		if (micPermissionDialog != null && micPermissionDialog.isShowing()) {
			return;
		}
		micPermissionDialog = new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.mic_permission_title)
						.setMessage(R.string.mic_permission_settings_message)
						.setPositiveButton(R.string.mic_permission_yes,
										(dialog, which) -> openAppSettings())
						.setNegativeButton(R.string.mic_permission_no,
										(dialog, which) -> {
											// Deny the deferred WebView request so the page
											// does not wait forever for the microphone.
											YoutubeWebview webView = getWebView();
											if (webView != null) {
												webView.grantPendingMediaPermission(false);
											}
											ToastUtils.show(this, R.string.mic_permission_denied);
										})
						.show();
	}

	private void openAppSettings() {
		Intent intent = new Intent(
						Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
						Uri.parse("package:" + getPackageName()));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PermissionUtils.REQUEST_RECORD_AUDIO) {
			// YouTube voice search: complete the deferred WebView microphone request and
			// tell the user what happened.
			boolean granted = grantResults.length > 0
							&& grantResults[0] == PackageManager.PERMISSION_GRANTED;
			YoutubeWebview webView = getWebView();
			if (webView != null) {
				webView.grantPendingMediaPermission(granted);
			}
			if (granted) {
				ToastUtils.show(this, R.string.mic_permission_granted);
			} else if (!ActivityCompat.shouldShowRequestPermissionRationale(
							this, Manifest.permission.RECORD_AUDIO)) {
				// "Don't ask again" was chosen: the system dialog will not reappear.
				showMicrophonePermissionSettingsDialog();
			} else {
				ToastUtils.show(this, R.string.mic_permission_denied);
			}
			return;
		}
		if (requestCode != PermissionUtils.REQUEST_STORAGE_PERMISSION) return;
		Runnable action = pendingPermissionAction;
		pendingPermissionAction = null;
		if (grantResults.length == 0) return;
		for (int result : grantResults) {
			if (result != PackageManager.PERMISSION_GRANTED) return;
		}
		if (action != null) action.run();
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		String url = currentUrl();
		if (url != null) {
			outState.putString(STATE_LAST_URL, url);
		}
	}

	@Override
	public void requestDownloadStoragePermission(@NonNull Runnable onGranted) {
		if (!PermissionUtils.needsLegacyStoragePermission()
						|| PermissionUtils.hasDownloadStoragePermission(this)) {
			onGranted.run();
			return;
		}
		pendingPermissionAction = onGranted;
		ActivityCompat.requestPermissions(
						this,
						PermissionUtils.downloadStoragePermissions(),
						PermissionUtils.REQUEST_STORAGE_PERMISSION);
	}

	private boolean shouldSuppressPiPForStartedActivity(@Nullable Intent intent) {
		if (intent == null) return false;
		if (intent.getComponent() == null) return true;
		return getPackageName().equals(intent.getComponent().getPackageName());
	}

/**
 * Helper that owns the queue bottom sheet widgets and transient state.
 */
	private static final class QueueSheet {
		@NonNull
		private final SwitchMaterial enabledSwitch;
		@NonNull
		private final ImageButton orderButton;
		@NonNull
		private final TextView emptyView;
		@NonNull
		private final RecyclerView recyclerView;
		@NonNull
		private final QueueAdapter adapter;
		@Nullable
		private BottomSheetBehavior<android.widget.FrameLayout> behavior;
		private boolean scrollPending;

		private QueueSheet(@NonNull SwitchMaterial enabledSwitch,
		                   @NonNull ImageButton orderButton,
		                   @NonNull TextView emptyView,
		                   @NonNull RecyclerView recyclerView,
		                   @NonNull QueueAdapter adapter) {
			this.enabledSwitch = enabledSwitch;
			this.orderButton = orderButton;
			this.emptyView = emptyView;
			this.recyclerView = recyclerView;
			this.adapter = adapter;
		}
	}

}
