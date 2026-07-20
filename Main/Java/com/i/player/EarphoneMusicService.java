package com.i.player;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.ui.PlayerNotificationManager;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class EarphoneMusicService extends MediaSessionService implements Player.Listener {

    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private PlayerNotificationManager notificationManager;
    private ArrayList<Song> songList;
    private int currentPosition = -1;
    private int repeatMode = 0;
    private boolean isShuffle = false;

    private final IBinder binder = new LocalBinder();
    private PlaybackListener playbackListener;
    private String lastError = "";

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean isPlaying);
        void onSongChanged(Song song, int position);
        void onDurationChanged(long duration);
        void onCurrentPositionChanged(long position);
        void onPlaybackError(String error);
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initPlayer();
        setupNotificationManager();
        
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
registerReceiver(noisyReceiver, filter);
    
}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent != null && intent.getAction() != null) {
            handleNotificationAction(intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (notificationManager != null) {
            notificationManager.setPlayer(null);
            notificationManager = null;
        }
        if (player != null) {
            player.removeListener(this);
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
        
        // earphone related 
        try {
    unregisterReceiver(noisyReceiver);
} catch (Exception ignored) {
}
    
}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        IBinder superBinder = super.onBind(intent);
        if (superBinder != null) return superBinder;
        return binder;
    }

    @NonNull
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }





// -------------------------------------------------------------------------
    // For earphone 
    // -------------------------------------------------------------------------

private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            pauseSong();
        }
    }
};
    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------


private void initPlayer() {
    // ✅ ExoPlayer with auto audio focus
    androidx.media3.common.AudioAttributes audioAttributes =
        new androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();

    player = new ExoPlayer.Builder(this)
        .setMediaSourceFactory(
            new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(new DefaultDataSource.Factory(this)))
        .setAudioAttributes(audioAttributes, true) // ✅ Auto audio focus
        .setSeekForwardIncrementMs(10000) // ⏩ 10 sec forward
        .setSeekBackIncrementMs(10000)    // ⏪ 10 sec rewind
        .build();

    player.addListener(this);

    mediaSession = new MediaSession.Builder(this, player).build();
}

/*
    private void initPlayer() {
        // ✅ ExoPlayer with auto audio focus
        androidx.media3.common.AudioAttributes audioAttributes =
            new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(new DefaultDataSource.Factory(this)))
            .setAudioAttributes(audioAttributes, true) // ✅ true = ExoPlayer handles audio focus
            .build();

        player.addListener(this);

        mediaSession = new MediaSession.Builder(this, player).build();
    }


*/



    private void setupNotificationManager() {
        notificationManager = new PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
        .setSmallIconResourceId(R.drawable.ic_music_note)
        .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
            @Override
            public CharSequence getCurrentContentTitle(Player player) {
                Song currentSong = getCurrentSong();
                if (currentSong != null && songList != null) {
                    int index = currentPosition + 1;
                    int total = songList.size();
                    return index + "/" + total + " " + currentSong.getTitle();
                }
                return "Music Player";
            }

            @Override
            public CharSequence getCurrentContentText(Player player) {
                Song currentSong = getCurrentSong();
                return currentSong != null ? currentSong.getArtist() : "Playing";
            }

            @Nullable
            @Override
            public PendingIntent createCurrentContentIntent(Player player) {
                return buildMainActivityPendingIntent();
            }

            @Override
            public Bitmap getCurrentLargeIcon(
                Player player,
                PlayerNotificationManager.BitmapCallback callback) {

                Song currentSong = getCurrentSong();
                if (currentSong == null) return null;

                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(MusicService.this, currentSong.getUri());
                    byte[] art = retriever.getEmbeddedPicture();
                    retriever.release();
                    if (art != null) {
                        return BitmapFactory.decodeByteArray(art, 0, art.length);
                    }
                } catch (Exception e) {
                    return null;
                }
                return null;
            }
        })
        .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
            @Override
            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                if (ongoing) {
                    startForeground(notificationId, notification);
                }
            }

            @Override
            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                if (player != null && !player.isPlaying()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH);
                    } else {
                        //noinspection deprecation
                        stopForeground(false);
                    }
                }
            }
        })
        .build();

        // ✅ Setup notification controls
        notificationManager.setPlayer(player);
        notificationManager.setUseNextAction(true);
        notificationManager.setUsePreviousAction(true);
        notificationManager.setUsePlayPauseActions(true);
        notificationManager.setUseStopAction(false);
        notificationManager.setUseFastForwardAction(true);
        notificationManager.setUseRewindAction(true);
        notificationManager.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationManager.setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    private PendingIntent buildMainActivityPendingIntent() {
        Intent intent = new Intent(MusicService.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(MusicService.this, 0, intent, flags);
    }

    // -------------------------------------------------------------------------
    // Notification channel
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setAllowBubbles(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // -------------------------------------------------------------------------
    // Playlist management
    // -------------------------------------------------------------------------



public void setSongList(ArrayList<Song> songs) {
    if (songs == null) return;

    this.songList = songs;

    if (player.getMediaItemCount() == 0) {
        buildPlaylist();
    }

    if (currentPosition == -1 && !songList.isEmpty()) {
        currentPosition = 0;

        // First song ko sirf select karo, play mat karo
        player.seekToDefaultPosition(0);
        player.prepare();

        if (playbackListener != null) {
            playbackListener.onSongChanged(songList.get(0), 0);
            playbackListener.onDurationChanged(getDuration());
            playbackListener.onPlaybackStateChanged(false);
        }
    }
}




public int getCurrentSongPosition() {
    return currentPosition;
}



/*
    public void setSongList(ArrayList<Song> songs) {
        if (songs == null) return;
        this.songList = songs;
        if (player.getMediaItemCount() == 0) {
            buildPlaylist();
        }
    }

*/

    private void buildPlaylist() {
        if (songList == null || songList.isEmpty()) return;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (int i = 0; i < songList.size(); i++) {
            Song song = songList.get(i);
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(song.getUri())
                .setMediaId(String.valueOf(i))
                .setMediaMetadata(new MediaMetadata.Builder()
                    .setTitle(song.getTitle())
                    .setArtist(song.getArtist())
                    .setAlbumTitle(song.getAlbum())
                    .build())
                .build();
            mediaItems.add(mediaItem);
        }

        if (!mediaItems.isEmpty()) {
            player.setMediaItems(mediaItems, false);
            player.prepare();
        }
    }

    // -------------------------------------------------------------------------
    // Playback controls
    // -------------------------------------------------------------------------

    public void playSongAt(int position) {
        if (songList == null || songList.isEmpty()) return;
        if (position < 0 || position >= songList.size()) return;

        currentPosition = position;
        player.seekToDefaultPosition(position);
        player.prepare();
        player.play();

        Song song = songList.get(position);
        if (playbackListener != null) {
            playbackListener.onSongChanged(song, position);
            playbackListener.onDurationChanged(getDuration());
            playbackListener.onPlaybackStateChanged(true);
        }
    }

    public void playSong() {
        if (player != null && !player.isPlaying() && currentPosition >= 0) {
            player.play();
            if (playbackListener != null) playbackListener.onPlaybackStateChanged(true);
        } else if (currentPosition < 0 && songList != null && !songList.isEmpty()) {
            playSongAt(0);
        }
    }

    public void pauseSong() {
        if (player != null && player.isPlaying()) {
            player.pause();
            if (playbackListener != null) playbackListener.onPlaybackStateChanged(false);
        }
    }

    public void playNext() {
        if (player == null || songList == null || songList.isEmpty()) return;

        int nextPosition = currentPosition + 1;
        if (nextPosition >= songList.size()) {
            if (repeatMode == 2) nextPosition = 0;
            else {
                pauseSong();
                return;
            }
        }
        playSongAt(nextPosition);
    }

    public void playPrevious() {
        if (player == null || songList == null || songList.isEmpty()) return;

        int prevPosition = currentPosition - 1;
        if (prevPosition < 0) {
            prevPosition = (repeatMode == 2) ? songList.size() - 1 : 0;
        }
        playSongAt(prevPosition);
    }

    public Song getCurrentSong() {
        if (currentPosition >= 0 && songList != null && currentPosition < songList.size()) {
            return songList.get(currentPosition);
        }
        return null;
    }

    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
            if (playbackListener != null) playbackListener.onCurrentPositionChanged(position);
        }
    }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
        if (player != null) player.setShuffleModeEnabled(shuffle);
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        if (player == null) return;
        switch (mode) {
            case 1:
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                break;
            case 2:
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                break;
            default:
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        if (player != null && player.getDuration() > 0 && player.getDuration() != Long.MIN_VALUE) {
            return player.getDuration();
        }
        if (currentPosition >= 0 && songList != null && currentPosition < songList.size()) {
            return songList.get(currentPosition).getDuration();
        }
        return 0;
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    // -------------------------------------------------------------------------
    // Player.Listener callbacks
    // -------------------------------------------------------------------------

    private void handleNotificationAction(String action) {
        if (player == null) return;
        switch (action) {
            case "PLAY":
                playSong();
                break;
            case "PAUSE":
                pauseSong();
                break;
            case "NEXT":
                playNext();
                break;
            case "PREVIOUS":
                playPrevious();
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        boolean isPlaying = playbackState == Player.STATE_READY && player.isPlaying();
        if (playbackListener != null) playbackListener.onPlaybackStateChanged(isPlaying);

        if (playbackState == Player.STATE_ENDED) {
            if (repeatMode == 1) {
                playSongAt(currentPosition);
            } else {
                playNext();
            }
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (playbackListener != null) playbackListener.onPlaybackStateChanged(isPlaying);
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        if (mediaItem != null && mediaItem.mediaId != null) {
            try {
                int newPosition = Integer.parseInt(mediaItem.mediaId);
                if (currentPosition != newPosition) {
                    currentPosition = newPosition;
                    if (playbackListener != null && songList != null
                        && currentPosition < songList.size()) {
                        Song song = songList.get(currentPosition);
                        playbackListener.onSongChanged(song, currentPosition);
                        playbackListener.onDurationChanged(getDuration());
                    }
                }
            } catch (NumberFormatException e) {
                lastError = e.getMessage() != null ? e.getMessage() : "Unknown error";
            }
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        String message = error.getMessage();
        lastError = "Playback error: " + (message != null ? message : "Unknown error");
        if (playbackListener != null) {
            playbackListener.onPlaybackError(lastError);
        }
        playNext();
    }
}