package com.i.player;







import android.content.ContentUris;
import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

 //import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
	
	private static final int REQUEST_PERMISSION = 1;
	
	private RecyclerView recyclerView;
	private TextView songTitle, currentTime, totalTime;
	private ImageButton btnPlay, btnPause, btnNext, btnPrevious, btnShuffle, btnRepeat;
	private SeekBar seekBar;
	private SearchView searchView;
	
	private ArrayList<Song> songList;
	private ArrayList<Song> filteredSongList;
	private SongAdapter songAdapter;
	
	private MusicService musicService;
	private boolean isBound = false;
	
	private boolean isShuffle = false;
	private int repeatMode = 0;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private Runnable seekBarUpdateRunnable;
	
	private MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
	private MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
	private MutableLiveData<Long> durationLiveData = new MutableLiveData<>(0L);
	private MutableLiveData<Long> currentPositionLiveData = new MutableLiveData<>(0L);
	
	private final ServiceConnection serviceConnection = new ServiceConnection() {
	
	
	///++++++++++
	
	
	
	@Override
public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

    MusicService.LocalBinder binder = (MusicService.LocalBinder) iBinder;
    musicService = binder.getService();
    isBound = true;

    // Playback listener set karo
    musicService.setPlaybackListener(new MusicService.PlaybackListener() {

        @Override
        public void onPlaybackStateChanged(boolean isPlaying) {
            isPlayingLiveData.postValue(isPlaying);
        }

        @Override
        public void onSongChanged(Song song, int position) {
            currentSongLiveData.postValue(song);

            if (songAdapter != null) {
                int adapterPosition = filteredSongList.indexOf(song);
                songAdapter.setCurrentPlayingIndex(adapterPosition);
            }
        }

        @Override
        public void onDurationChanged(long duration) {
            durationLiveData.postValue(duration);
        }

        @Override
        public void onCurrentPositionChanged(long position) {
            currentPositionLiveData.postValue(position);
        }

        @Override
        public void onPlaybackError(String error) {
            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
        }
    });


    // ✅ Service connect hone ke baad playlist bhejo
    if (songList != null && !songList.isEmpty()) {
        musicService.setSongList(songList);
    }


    // ✅ Agar pehle se koi song selected hai to UI update karo
    Song currentSong = musicService.getCurrentSong();

    if (currentSong != null) {

        currentSongLiveData.postValue(currentSong);
        durationLiveData.postValue(musicService.getDuration());
        isPlayingLiveData.postValue(musicService.isPlaying());

        if (songAdapter != null) {
            int adapterPosition = filteredSongList.indexOf(currentSong);
            songAdapter.setCurrentPlayingIndex(adapterPosition);
        }

    } else {

        // ✅ First open ke baad pehla song select dikhao (play nahi karega)
        if (songList != null && !songList.isEmpty()) {

            currentSongLiveData.postValue(songList.get(0));
          durationLiveData.postValue((long) songList.get(0).getDuration());          
        //    durationLiveData.postValue(songList.get(0).getDuration());
            isPlayingLiveData.postValue(false);

            if (songAdapter != null) {
                songAdapter.setCurrentPlayingIndex(0);
            }
        }
    }
}
	
	
		
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			isBound = false;
			musicService = null;
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	//	EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		initViews();
		checkPermissions();
		setupSearchView();
		observeLiveData();
		bindMusicService();
	}
	
	private void initViews() {
		recyclerView = findViewById(R.id.recyclerView);
		songTitle = findViewById(R.id.songTitle);
		currentTime = findViewById(R.id.currentTime);
		totalTime = findViewById(R.id.totalTime);
		btnPlay = findViewById(R.id.btnPlay);
		btnPause = findViewById(R.id.btnPause);
		btnNext = findViewById(R.id.btnNext);
		btnPrevious = findViewById(R.id.btnPrevious);
		btnShuffle = findViewById(R.id.btnShuffle);
		btnRepeat = findViewById(R.id.btnRepeat);
		seekBar = findViewById(R.id.seekBar);
		searchView = findViewById(R.id.searchView);
		
		songList = new ArrayList<>();
		filteredSongList = new ArrayList<>();
		
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		setupClickListeners();
		setupSeekBar();
	}
	
	private void setupClickListeners() {
		btnPlay.setOnClickListener(v -> {
			if (musicService != null) {
				musicService.playSong();
			}
		});
		
		btnPause.setOnClickListener(v -> {
			if (musicService != null) {
				musicService.pauseSong();
			}
		});
		
		btnNext.setOnClickListener(v -> {
			if (musicService != null) {
				musicService.playNext();
			}
		});
		
		btnPrevious.setOnClickListener(v -> {
			if (musicService != null) {
				musicService.playPrevious();
			}
		});
		
		btnShuffle.setOnClickListener(v -> {
			if (musicService != null) {
				isShuffle = !isShuffle;
				musicService.setShuffle(isShuffle);
				if (isShuffle) {
					btnShuffle.setImageTintList(ContextCompat.getColorStateList(this, R.color.colorAccent));
					} else {
					btnShuffle.setImageTintList(ContextCompat.getColorStateList(this, R.color.white));
				}
				Toast.makeText(this, isShuffle ? "Shuffle On" : "Shuffle Off", Toast.LENGTH_SHORT).show();
			}
		});
		
		btnRepeat.setOnClickListener(v -> {
			if (musicService != null) {
				repeatMode = (repeatMode + 1) % 3;
				musicService.setRepeatMode(repeatMode);
				updateRepeatIcon();
				String mode = repeatMode == 0 ? "Repeat Off" : (repeatMode == 1 ? "Repeat One" : "Repeat All");
				Toast.makeText(this, mode, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	

	
	
	private void updateRepeatIcon() {
		switch (repeatMode) {
			case 0: // Repeat Off
			btnRepeat.setImageResource(R.drawable.ic_repeat); // default repeat icon
			btnRepeat.setImageTintList(ContextCompat.getColorStateList(this, R.color.white));
			break;
			case 1: // Repeat One
			btnRepeat.setImageResource(R.drawable.ic_repeat_one); // repeat one icon
			btnRepeat.setImageTintList(ContextCompat.getColorStateList(this, R.color.colorAccent));
			break;
			case 2: // Repeat All
			btnRepeat.setImageResource(R.drawable.ic_repeat_all); // repeat all icon
			btnRepeat.setImageTintList(ContextCompat.getColorStateList(this, R.color.colorAccent));
			break;
		}
	}
	
	
	
	
	
	
	private void setupSeekBar() {
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser && musicService != null) {
					musicService.seekTo(progress);
					currentTime.setText(formatTime(progress));
				}
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
	}
	
	private void setupSearchView() {
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				return false;
			}
			
			@Override
			public boolean onQueryTextChange(String newText) {
				filterSongs(newText);
				return true;
			}
		});
		
		AutoCompleteTextView searchAutoComplete =
		searchView.findViewById(androidx.appcompat.R.id.search_src_text);
		
		if (searchAutoComplete != null) {
			searchAutoComplete.setTextColor(ContextCompat.getColor(this, android.R.color.white));
			searchAutoComplete.setHintTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
		}
	}
	
	
	
	private void filterSongs(String query) {
		filteredSongList.clear();
		
		if (query == null || query.isEmpty()) {
			filteredSongList.addAll(songList);
			} else {
			String lowerCaseQuery = query.toLowerCase().trim();
			
			for (Song song : songList) {
				String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";
				String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";
				String album = song.getAlbum() != null ? song.getAlbum().toLowerCase() : "";
				
				if (title.contains(lowerCaseQuery) ||
				artist.contains(lowerCaseQuery) ||
				album.contains(lowerCaseQuery)) {
					
					filteredSongList.add(song);
				}
			}
			
			
			
		}
		
		if (songAdapter != null) {
			songAdapter.updateList(filteredSongList);
			
			Song currentSong = currentSongLiveData.getValue();
			if (currentSong != null) {
				int currentPosition = filteredSongList.indexOf(currentSong);
				songAdapter.setCurrentPlayingIndex(currentPosition);
			}
		}
	}
	
	
	
	private void observeLiveData() {
		
		
	
	currentSongLiveData.observe(this, song -> {
		if (song != null) {
			int currentIndex = filteredSongList.indexOf(song) + 1; // serial number
			int total = filteredSongList.size();
			songTitle.setText(currentIndex + "/" + total + " " + song.getTitle() + " - " + song.getArtist());
			} else {
			songTitle.setText("No song playing (" + filteredSongList.size() + " songs)");
		}
	});
	
	isPlayingLiveData.observe(this, isPlaying -> {
		if (isPlaying) {
			btnPlay.setVisibility(View.GONE);
			btnPause.setVisibility(View.VISIBLE);
			} else {
			btnPlay.setVisibility(View.VISIBLE);
			btnPause.setVisibility(View.GONE);
		}
	});
	
	durationLiveData.observe(this, duration -> {
		if (duration > 0) {
			totalTime.setText(formatTime(duration));
			seekBar.setMax((int) (duration/1)); // ✅ FIX
			} else {
			totalTime.setText("00:00");
			seekBar.setMax(0);
		}
	});
	
	currentPositionLiveData.observe(this, position -> {
		currentTime.setText(formatTime(position));
		
		Long duration = durationLiveData.getValue();
		if (duration != null && duration > 0) {
			seekBar.setProgress((int) (long) position);
		}
	});
	
	startSeekBarUpdate();
}




private void startSeekBarUpdate() {
	if (seekBarUpdateRunnable != null) {
		handler.removeCallbacks(seekBarUpdateRunnable);
	}
	
	seekBarUpdateRunnable = new Runnable() {
		@Override
		public void run() {
			if (musicService != null) {
				if (musicService.isPlaying()) {
					long position = musicService.getCurrentPosition();
					currentPositionLiveData.postValue(position);
				}
				
				long duration = musicService.getDuration();
				Long currentDuration = durationLiveData.getValue();
				if (currentDuration == null || currentDuration != duration) {
					durationLiveData.postValue(duration);
				}
			}
			handler.postDelayed(this, 1000);
		}
	};
	handler.post(seekBarUpdateRunnable);
}


private void bindMusicService() {
	Intent intent = new Intent(this, MusicService.class);
	
	// ✅ IMPORTANT: Service को start करें (bind के साथ)
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		startForegroundService(intent);  // ✅ ये जरूरी है
		} else {
		startService(intent);
	}
	
	// फिर bind करें
	bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
}





private void checkPermissions() {
	ArrayList<String> permissions = new ArrayList<>();
	
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
		!= PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
		}
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
		!= PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.POST_NOTIFICATIONS);
		}
		} else {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
		!= PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
		}
	}
	
	if (!permissions.isEmpty()) {
		ActivityCompat.requestPermissions(this,
		permissions.toArray(new String[0]),
		REQUEST_PERMISSION);
		} else {
		loadSongs();
	}
}



@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
@NonNull int[] grantResults) {
	super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	
	if (requestCode == REQUEST_PERMISSION) {
		boolean granted = true;
		
		for (int result : grantResults) {
			if (result != PackageManager.PERMISSION_GRANTED) {
				granted = false;
				break;
			}
		}
		
		if (granted) {
			loadSongs();
			} else {
			Toast.makeText(this,
			"Permission required to load music. Please allow from settings.",
			Toast.LENGTH_LONG).show();
			// ❌ finish() hata diya (Play Store safe)
		}
	}
}






private void loadSongs() {
	ContentResolver contentResolver = getContentResolver();
	Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
	
	String[] projection = {
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.DURATION,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.YEAR
	};
	
	String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
	+ MediaStore.Audio.Media.DURATION + " > 28000";
	
	String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
	
	songList.clear();
	
	try (Cursor cursor = contentResolver.query(songUri, projection, selection, null, sortOrder)) {
		if (cursor != null && cursor.moveToFirst()) {
			
			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
			int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
			int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
			int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
			int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
			int yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
			
			do {
				long id = cursor.getLong(idColumn);
				
				Uri contentUri = ContentUris.withAppendedId(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
				
				String title = cursor.getString(titleColumn);
				String artist = cursor.getString(artistColumn);
				int duration = cursor.getInt(durationColumn);
				String album = cursor.getString(albumColumn);
				int year = cursor.getInt(yearColumn);
				
				Song song = new Song(title, artist, contentUri, duration, album, year);
				songList.add(song);
				
			} while (cursor.moveToNext());
		}
		
		} catch (SecurityException e) {
		Toast.makeText(this, "Unable to access music files", Toast.LENGTH_LONG).show();
		return;
	}
	
	if (songList.isEmpty()) {
		Toast.makeText(this, "No music files found", Toast.LENGTH_LONG).show();
		return;
	}
	
	filteredSongList.clear();
	filteredSongList.addAll(songList);
	



songAdapter = new SongAdapter(filteredSongList, position -> {

    Song selectedSong = filteredSongList.get(position);
    int actualPosition = songList.indexOf(selectedSong);

    Runnable playAction = () -> {

        if (musicService == null || !isBound) return;

        // Playlist सिर्फ पहली बार set होगी
        if (musicService.getCurrentSong() == null) {
            musicService.setSongList(songList);
        }

        // अगर current song पर click हुआ
        if (musicService.getCurrentSongPosition() == actualPosition) {

            if (musicService.isPlaying()) {
                musicService.pauseSong();
            } else {
                musicService.playSong(); // Resume वहीं से
            }

        } else {
            // दूसरा song play करो
            musicService.playSongAt(actualPosition);
        }
    };

    if (musicService != null && isBound) {
        playAction.run();
    } else {
        handler.postDelayed(playAction, 500);
    }

});



	
	recyclerView.setAdapter(songAdapter);
	
	if (musicService != null) {
		musicService.setSongList(songList);
	}
}





private String formatTime(long milliseconds) {
	int seconds = (int) ((milliseconds / 1000) % 60);
	int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
	int hours = (int) (milliseconds / (1000 * 60 * 60));
	
	if (hours > 0) {
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		} else {
		return String.format("%02d:%02d", minutes, seconds);
	}
}

@Override
protected void onDestroy() {
	super.onDestroy();
	if (seekBarUpdateRunnable != null) {
		handler.removeCallbacks(seekBarUpdateRunnable);
	}
	if (isBound) {
		unbindService(serviceConnection);
		isBound = false;
	}
}
}