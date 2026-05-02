package com.ryanheise.just_audio;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.source.CompositeMediaSource;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.exoplayer.upstream.Allocator;

/**
 * A {@link MediaSource} that lazily defers to another {@link MediaSource} when it is required.
 * <p>
 * The wrapped{@link MediaSource} is not fetched until this {@link MediaSource} is prepared.
 */
class LazyMediaSource2 extends CompositeMediaSource<Void> {

    private final LazyMediaSourceProvider mediaSourceProvider;
    public final String id;

    private MediaSource mediaSource;

    LazyMediaSource2(LazyMediaSourceProvider mediaSourceProvider, String id) {
        this.mediaSourceProvider = mediaSourceProvider;
        this.id = id;
    }

    @Override
    public Timeline getInitialTimeline() {
        if (mediaSource == null) return null;
        return mediaSource.getInitialTimeline();
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(mediaTransferListener);
        mediaSourceProvider.createMediaSource(id, (newMediaSource) -> {
            if(this.mediaSource!=null){
                releaseChildSource(null);
            }
            if (newMediaSource == null) {
                this.mediaSource = new SilenceMediaSource(0);
            } else {
                this.mediaSource = newMediaSource;
            }
            prepareChildSource(null, this.mediaSource);
        });
    }

    @Override
    protected void onChildSourceInfoRefreshed(Void childSourceId, MediaSource mediaSource, Timeline newTimeline) {
        refreshSourceInfo(newTimeline);
    }

    @Override
    public MediaItem getMediaItem() {
        return new MediaItem.Builder()
                .setTag(id)
                .build();
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
    protected void releaseSourceInternal() {
        super.releaseSourceInternal();
        mediaSource=null;
    }

    public interface LazyMediaSourceReceiver {
        void onMediaSourceCreated(MediaSource mediaSource);
    }

    public interface LazyMediaSourceProvider {
        void createMediaSource(String id, LazyMediaSourceReceiver receiver);
    }
}