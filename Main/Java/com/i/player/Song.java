package com.i.player;




import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class Song implements Parcelable {
	
	private final String title;
	private final String artist;
	private final Uri uri;   // ✅ path → uri
	private final int duration;
	private final String album;
	private final int year;
	
	public Song(String title, String artist, Uri uri, int duration, String album, int year) {
		this.title = (title != null && !title.isEmpty()) ? title : "Unknown";
		this.artist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
		this.uri = uri;  // ✅ direct Uri store
		this.duration = duration;
		this.album = (album != null && !album.isEmpty()) ? album : "Unknown Album";
		this.year = year;
	}
	
	protected Song(Parcel in) {
		title = in.readString();
		artist = in.readString();
		String uriString = in.readString();
		uri = uriString != null ? Uri.parse(uriString) : null;  // ✅ restore Uri
		duration = in.readInt();
		album = in.readString();
		year = in.readInt();
	}
	
	public static final Creator<Song> CREATOR = new Creator<Song>() {
		@Override
		public Song createFromParcel(Parcel in) {
			return new Song(in);
		}
		
		@Override
		public Song[] newArray(int size) {
			return new Song[size];
		}
	};
	
	public String getTitle() { return title; }
	public String getArtist() { return artist; }
	public Uri getUri() { return uri; }   // ✅ updated getter
	public int getDuration() { return duration; }
	public String getAlbum() { return album; }
	public int getYear() { return year; }
	
	// ✅ Uri based check (no File path)
	public boolean isPlayable() {
		return uri != null;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(title);
		dest.writeString(artist);
		dest.writeString(uri != null ? uri.toString() : null); // ✅ save as string
		dest.writeInt(duration);
		dest.writeString(album);
		dest.writeInt(year);
	}
}