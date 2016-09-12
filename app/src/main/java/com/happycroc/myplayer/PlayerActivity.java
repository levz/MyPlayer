package com.happycroc.myplayer;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback, ExoPlayer.Listener,
        MediaCodecVideoTrackRenderer.EventListener{

    private static final String TAG = "MyPlayer";

    private static final String USER_AGENT = "MyPlayer 1.0";
    private static final Uri VIDEO_URI = Uri.parse("http://html5demos.com/assets/dizzy.mp4");

    private static final int RENDERER_VIDEO = 0;
    private static final int RENDERER_AUDIO = 1;

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private View shutterView;
    private AspectRatioFrameLayout videoFrame;
    private SurfaceView surfaceView;

    private ExoPlayer player;
    private KeyCompatibleMediaController mediaController;
    private long playerPosition;
    private static TrackRenderer[] renderers;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        View root = findViewById(R.id.root);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mediaController.isShowing()) {
                        mediaController.hide();
                    } else {
                        mediaController.show(0);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                        || keyCode == KeyEvent.KEYCODE_MENU) {
                    return false;
                }
                return mediaController.dispatchKeyEvent(event);
            }
        });

        shutterView = findViewById(R.id.shutter);
        videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        // add media controller
        mediaController = new KeyCompatibleMediaController(this);
        mediaController.setAnchorView(root);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            onShown();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            onShown();
        }
    }

    private void onShown() {
        if (player == null)
            preparePlayer(true);
        shutterView.setVisibility(View.GONE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            onHidden();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            onHidden();
        }
    }

    private void onHidden() {
        releasePlayer();
        shutterView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    // Creating and releasing player
    // =============================================================================
    private void preparePlayer(boolean playWhenReady) {
        if (player == null) {
            player = ExoPlayer.Factory.newInstance(2, 1000, 5000);
            player.addListener(this);
            player.seekTo(playerPosition);

            mediaController.setMediaPlayer(new PlayerControl(player));
            mediaController.setEnabled(true);

            Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
            Handler mainHandler = new Handler();

            // Build the video and audio renderers.
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, null);
            DataSource dataSource = new DefaultUriDataSource(this, bandwidthMeter, USER_AGENT);
            ExtractorSampleSource sampleSource = new ExtractorSampleSource(VIDEO_URI, dataSource, allocator,
                    BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE, mainHandler, null, 0);
            MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(this,
                    sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
                    mainHandler, this, 50);
            MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                    MediaCodecSelector.DEFAULT, null, true, mainHandler, null,
                    AudioCapabilities.getCapabilities(this), AudioManager.STREAM_MUSIC);

            renderers = new TrackRenderer[2];
            renderers[RENDERER_VIDEO] = videoRenderer;
            renderers[RENDERER_AUDIO] = audioRenderer;

            player.prepare(videoRenderer, audioRenderer);
        }
        pushSurface(surfaceView.getHolder().getSurface(), false);
        player.setPlayWhenReady(playWhenReady);
    }

    private void releasePlayer() {
        if (player != null) {
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
            renderers = null;
        }
    }



    // SurfaceHolder.Callback implementation
    // =============================================================================
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        pushSurface(holder.getSurface(), false);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        pushSurface(null, true);
    }

    private void pushSurface(Surface surface, boolean blockForSurfacePush) {
        if (renderers == null)
            return;

        if (blockForSurfacePush) {
            player.blockingSendMessage(
                    renderers[RENDERER_VIDEO], MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        } else {
            player.sendMessage(
                    renderers[RENDERER_VIDEO], MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        }
    }


    // Implementing ExoPlayer.Listener
    // =============================================================================
    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED){
            // show media controller at the end of the track
            mediaController.show(0);
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        // TODO: Handle no network error here
        Log.e(TAG, error.getLocalizedMessage());
    }


    // Implementing MediaCodecVideoTrackRenderer.EventListener
    // =============================================================================
    @Override
    public void onDroppedFrames(int count, long elapsed) {

    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        shutterView.setVisibility(View.GONE);
        videoFrame.setAspectRatio(
                height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
    }

    @Override
    public void onDrawnToSurface(Surface surface) {

    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        Log.e(TAG, "Decoder init error: " + e.getLocalizedMessage());
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
        Log.i(TAG, "Decoder initialized.");
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        Log.e(TAG, "Crypto error: " + e.getLocalizedMessage());
    }

    // Const class for player media controller
    // =============================================================================
    private static final class KeyCompatibleMediaController extends MediaController {

        private MediaController.MediaPlayerControl playerControl;

        public KeyCompatibleMediaController(Context context) {
            super(context);
        }

        @Override
        public void setMediaPlayer(MediaController.MediaPlayerControl playerControl) {
            super.setMediaPlayer(playerControl);
            this.playerControl = playerControl;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();
            if (playerControl.canSeekForward() && (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerControl.seekTo(playerControl.getCurrentPosition() + 15000); // milliseconds
                    show();
                }
                return true;
            } else if (playerControl.canSeekBackward() && (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerControl.seekTo(playerControl.getCurrentPosition() - 5000); // milliseconds
                    show();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

}
