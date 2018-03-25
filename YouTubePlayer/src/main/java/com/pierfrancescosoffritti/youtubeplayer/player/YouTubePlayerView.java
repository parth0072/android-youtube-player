package com.pierfrancescosoffritti.youtubeplayer.player;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.pierfrancescosoffritti.youtubeplayer.R;
import com.pierfrancescosoffritti.youtubeplayer.player.playerUtils.FullScreenHelper;
import com.pierfrancescosoffritti.youtubeplayer.player.playerUtils.PlaybackResumer;
import com.pierfrancescosoffritti.youtubeplayer.ui.AbstractPlayerUIController;
import com.pierfrancescosoffritti.youtubeplayer.ui.DefaultPlayerUIController;
import com.pierfrancescosoffritti.youtubeplayer.ui.PlayerUIController;
import com.pierfrancescosoffritti.youtubeplayer.utils.Callable;
import com.pierfrancescosoffritti.youtubeplayer.utils.NetworkReceiver;
import com.pierfrancescosoffritti.youtubeplayer.utils.Utils;

public class YouTubePlayerView extends FrameLayout implements NetworkReceiver.NetworkListener, LifecycleObserver {

    @NonNull private final WebViewYouTubePlayer youTubePlayer;
    @NonNull private AbstractPlayerUIController playerUIControls;

    @NonNull private final NetworkReceiver networkReceiver;
    @NonNull private final PlaybackResumer playbackResumer;
    @NonNull private final FullScreenHelper fullScreenHelper;
    @NonNull private final InternalYouTubePlayerListener internalYouTubePlayerListener;
    @Nullable private Callable asyncInitialization;

    public YouTubePlayerView(Context context) {
        this(context, null);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        youTubePlayer = new WebViewYouTubePlayer(context);
        addView(youTubePlayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        playerUIControls = new DefaultPlayerUIController(this, youTubePlayer);

        playbackResumer = new PlaybackResumer();
        networkReceiver = new NetworkReceiver(this);
        fullScreenHelper = new FullScreenHelper();
        internalYouTubePlayerListener = new InternalYouTubePlayerListener(playbackResumer);

        setPlayerUIControls(playerUIControls, R.layout.player_controls);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // if height == wrap content make the view 16:9
        if(getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            int sixteenNineHeight = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec) * 9 / 16, View.MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, sixteenNineHeight);
        } else
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Initialize the player
     * @param youTubePlayerInitListener lister for player init events
     * @param handleNetworkEvents if <b>true</b> a broadcast receiver will be registered.<br/>If <b>false</b> you should handle network events with your own broadcast receiver. See {@link YouTubePlayerView#onNetworkAvailable()} and {@link YouTubePlayerView#onNetworkUnavailable()}
     */
    public void initialize(@NonNull final YouTubePlayerInitListener youTubePlayerInitListener, boolean handleNetworkEvents) {
        if(handleNetworkEvents)
            getContext().registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        asyncInitialization = new Callable() {
            @Override
            public void call() {
                youTubePlayer.initialize(new YouTubePlayerInitListener() {
                    @Override
                    public void onInitSuccess(YouTubePlayer youTubePlayer) {
                        addYouTubePlayerInternalListeners(youTubePlayer);
                        youTubePlayerInitListener.onInitSuccess(youTubePlayer);
                    }
                });
            }
        };

        if(Utils.isOnline(getContext()))
            asyncInitialization.call();
    }

    /**
     * Calls {@link WebView#destroy()} on the player. And unregisters the broadcast receiver (for network events), if registered.
     * Call this method before destroying the host Fragment/Activity, or register this View as an observer of its host lifcecycle
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void release() {
        youTubePlayer.destroy();
        try {
            getContext().unregisterReceiver(networkReceiver);
        } catch (Exception ignore) {
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    void onPause() {
        youTubePlayer.pause();
    }

    @Override
    public void onNetworkAvailable() {
        if(asyncInitialization != null)
            asyncInitialization.call();
        else
            playbackResumer.resume(youTubePlayer);
    }

    @Override
    public void onNetworkUnavailable() {
    }

    @NonNull
    public PlayerUIController getPlayerUIController() {
        return playerUIControls;
    }

    /**
     * Call this method to use custom UI controls for the player. The default controls will be removed and replaced with the one you provide.
     * @param playerUIController a {@link AbstractPlayerUIController} used to control the new controls view.
     * @param controlsLayout the layout of the new controls. The layout will be inflated and the result View injected into the provided {@link AbstractPlayerUIController}.
     */
    public void setPlayerUIControls(@NonNull AbstractPlayerUIController playerUIController, @LayoutRes int controlsLayout) {
        removeViews(1, this.getChildCount()-1);

        View controlsView = View.inflate(getContext(), controlsLayout, this);
        playerUIController.onControlsViewInflated(controlsView);

        internalYouTubePlayerListener.removeListener(playerUIControls);
        fullScreenHelper.removeFullScreenListener(playerUIControls);

        playerUIControls = playerUIController;

        internalYouTubePlayerListener.addListener(playerUIControls);
        fullScreenHelper.addFullScreenListener(playerUIControls);
    }

    public void enterFullScreen() {
        fullScreenHelper.enterFullScreen(this);
    }

    public void exitFullScreen() {
        fullScreenHelper.exitFullScreen(this);
    }

    public boolean isFullScreen() {
        return fullScreenHelper.isFullScreen();
    }

    public void toggleFullScreen() {
        fullScreenHelper.toggleFullScreen(this);
    }

    public boolean addFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenHelper.addFullScreenListener(fullScreenListener);
    }

    public boolean removeFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenHelper.removeFullScreenListener(fullScreenListener);
    }

    private void addYouTubePlayerInternalListeners(YouTubePlayer youTubePlayer) {
        youTubePlayer.addListener(internalYouTubePlayerListener);

        youTubePlayer.addListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady() {
                asyncInitialization = null;
            }
        });
    }
}
