package com.i.player;






import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
	
	private ArrayList<Song> songList;
	private final OnItemClickListener listener;
	private int currentPlayingIndex = -1;
	
	public interface OnItemClickListener {
		void onItemClick(int position);
	}
	
	public SongAdapter(ArrayList<Song> songList, OnItemClickListener listener) {
		this.songList = songList;
		this.listener = listener;
	}
	
	public void updateList(ArrayList<Song> newList) {
		this.songList = newList;
		this.currentPlayingIndex = -1;
		notifyDataSetChanged();
	}
	
	@NonNull
	@Override
	public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
		.inflate(R.layout.item_song1, parent, false);
		return new SongViewHolder(view);
	}
	
	@Override
	public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
		Song song = songList.get(position);
		
		holder.tvTitle.setText((position+1)+" "+song.getTitle());
		holder.tvArtist.setText(song.getArtist());
		holder.tvDuration.setText(formatTime(song.getDuration()));
		
		if (position == currentPlayingIndex) {
			holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
			R.color.colorAccent));
			holder.itemView.setBackgroundResource(R.color.primary_transparent);
			} else {
			holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
			android.R.color.white));
			holder.itemView.setBackgroundResource(android.R.color.transparent);
		}
		
		holder.itemView.setOnClickListener(v -> {
			if (listener != null) {
				listener.onItemClick(position);
			}
		});
	}
	
	public void setCurrentPlayingIndex(int index) {
		if (index < 0 || index >= songList.size()) {
			currentPlayingIndex = -1;
			} else {
			int oldIndex = currentPlayingIndex;
			currentPlayingIndex = index;
			if (oldIndex != -1) {
				notifyItemChanged(oldIndex);
			}
			notifyItemChanged(currentPlayingIndex);
		}
	}
	
	@Override
	public int getItemCount() {
		return songList.size();
	}
	
	private String formatTime(int milliseconds) {
		int seconds = (milliseconds / 1000) % 60;
		int minutes = (milliseconds / (1000 * 60)) % 60;
		int hours = milliseconds / (1000 * 60 * 60);
		
		if (hours > 0) {
			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
			} else {
			return String.format("%02d:%02d", minutes, seconds);
		}
	}
	
	static class SongViewHolder extends RecyclerView.ViewHolder {
		TextView tvTitle, tvArtist, tvDuration;
		
		SongViewHolder(@NonNull View itemView) {
			super(itemView);
			tvTitle = itemView.findViewById(R.id.songTitleText);
			tvArtist = itemView.findViewById(R.id.songArtistText);
			tvDuration = itemView.findViewById(R.id.tvDuration);
		}
	}
}