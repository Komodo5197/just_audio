package com.ryanheise.just_audio;

import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.exoplayer.upstream.Allocator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * A {@link MediaSource} that lazily defers to another {@link MediaSource} when it is required.
 * <p>
 * This {@link MediaSource} must be used with a {@link androidx.media3.exoplayer.source.MaskingMediaSource}.
 */
class LazyMediaSource implements MediaSource {
    private final LazyMediaSourceProvider mediaSourceProvider;
    public final String id;
    public final MediaItem placeholderMediaItem;

    private final Map<MediaSourceEventListener, Handler> pendingEventListeners = new HashMap<>();
    private final Map<DrmSessionEventListener, Handler> pendingDrmEventListeners = new HashMap<>();

    private MediaSource mediaSource;

    LazyMediaSource(LazyMediaSourceProvider mediaSourceProvider, String id, MediaItem placeholderMediaItem) {
        this.mediaSourceProvider = mediaSourceProvider;
        this.id = id;
        this.placeholderMediaItem = placeholderMediaItem;
    }

    @Override
    public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
        if (mediaSource == null) {
            pendingEventListeners.put(eventListener, handler);
        } else {
            mediaSource.addEventListener(handler, eventListener);
        }
    }

    @Override
    public void removeEventListener(MediaSourceEventListener eventListener) {
        if (mediaSource == null) {
            pendingEventListeners.remove(eventListener);
        } else {
            mediaSource.removeEventListener(eventListener);
        }
    }

    @Override
    public void addDrmEventListener(Handler handler, DrmSessionEventListener eventListener) {
        if (mediaSource == null) {
            pendingDrmEventListeners.put(eventListener, handler);
        } else {
            mediaSource.addDrmEventListener(handler, eventListener);
        }
    }

    @Override
    public void removeDrmEventListener(DrmSessionEventListener eventListener) {
        if (mediaSource == null) {
            pendingDrmEventListeners.remove(eventListener);
        } else {
            mediaSource.removeDrmEventListener(eventListener);
        }
    }

    @Override
    public Timeline getInitialTimeline() {
        if (mediaSource == null) return null;
        return mediaSource.getInitialTimeline();
    }

    @Override
    public boolean isSingleWindow() {
        if (mediaSource == null) return false;
        return mediaSource.isSingleWindow();
    }

    @Override
    public MediaItem getMediaItem() {
        if (mediaSource == null) {
            return placeholderMediaItem;
        } else {
            return mediaSource.getMediaItem();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void prepareSource(
            MediaSourceCaller caller,
            TransferListener mediaTransferListener,
            PlayerId playerId
    ) {
        CompletableFuture<MediaSource> future = new CompletableFuture<>();
        mediaSourceProvider.createMediaSource(id, future);
        MediaSource newSource= null;
        try {
            newSource = future.get();
        } catch (ExecutionException ignored) {
        } catch (InterruptedException ignored) {
        }
        if (newSource == null) {
            this.mediaSource = new SilenceMediaSource(0);
        } else {
            this.mediaSource = newSource;
        }

        for (Map.Entry<MediaSourceEventListener, Handler> entry : pendingEventListeners.entrySet()) {
            this.mediaSource.addEventListener(entry.getValue(), entry.getKey());
        }
        pendingEventListeners.clear();
        for (Map.Entry<DrmSessionEventListener, Handler> entry : pendingDrmEventListeners.entrySet()) {
            this.mediaSource.addDrmEventListener(entry.getValue(), entry.getKey());
        }
        pendingDrmEventListeners.clear();
        this.mediaSource.prepareSource(caller, mediaTransferListener, playerId);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (mediaSource == null) return;
        mediaSource.maybeThrowSourceInfoRefreshError();
    }

    @Override
    public void enable(MediaSourceCaller caller) {
        if (mediaSource == null) throw new IllegalStateException();
        mediaSource.enable(caller);
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        if (mediaSource == null) throw new IllegalStateException();
        return mediaSource.createPeriod(id, allocator, startPositionUs);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        if (mediaSource == null) throw new IllegalStateException();
        mediaSource.releasePeriod(mediaPeriod);
    }

    @Override
    public void disable(MediaSourceCaller caller) {
        if (mediaSource == null) throw new IllegalStateException();
        mediaSource.disable(caller);
    }

    @Override
    public void releaseSource(MediaSourceCaller caller) {
        if (mediaSource == null) return;
        mediaSource.releaseSource(caller);
    }
}

interface LazyMediaSourceProvider {
    void createMediaSource(String id, CompletableFuture<MediaSource> receiver);
}