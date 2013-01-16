package com.twofours.surespot.main;

import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.NetworkController;

public class MainAdapter extends BaseAdapter {
	private final static String TAG = "MainAdapter";

	private final ArrayList<Friend> mFriends = new ArrayList<Friend>();
	private final ArrayList<String> mActiveChats = new ArrayList<String>();

	private Context mContext;

	public MainAdapter(Context context) {
		mContext = context;
		// refreshActiveChats();

	}

	public void refreshActiveChats() {
		String sChats = Utils.getSharedPrefsString(SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS);
		mActiveChats.clear();
		if (sChats != null) {
			JSONArray jsonChats;
			try {
				jsonChats = new JSONArray(sChats);
				for (int i = 0; i < jsonChats.length(); i++) {
					String chatName = jsonChats.getString(i);
					mActiveChats.add(chatName);
				}
			} catch (JSONException e) {
				Log.e(TAG, "Error decoding active chat json list: " + e.toString());
			}
		}
	}

	/*
	 * public void refreshFlags() { for (Friend friend : mFriends) { if (mActiveChats.contains(friend.getName())) {
	 * friend.setFlags(friend.getFlags() | ~Friend.ACTIVE_CHAT ); } else { friend.setFlags(friend.getFlags() &
	 * ~Friend.ACTIVE_CHAT ); } } notifyDataSetChanged(); }
	 */

	public void messageReceived(String name) {
		Log.v(TAG, "message received");
		Friend friend = getFriend(name);
		friend.incMessageCount(1);
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public void messageDeltaReceived(String name, int delta) {
		Friend friend = getFriend(name);
		friend.setMessageCount(delta);
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	private Friend getFriend(String friendName) {
		for (Friend friend : mFriends) {
			if (friend.getName().equals(friendName)) {
				return friend;
			}
		}
		return null;
	}

	public void addNewFriend(String name) {
		Friend friend = getFriend(name);
		if (friend == null) {
			friend = new Friend();
			mFriends.add(friend);
			friend.setName(name);
		}

		friend.setNewFriend(true);
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public void addFriendInvited(String name) {
		Friend friend = new Friend();
		friend.setName(name);
		friend.setInvited(true);
		mFriends.add(friend);
		Collections.sort(mFriends);
		notifyDataSetChanged();

	}

	public void addFriendInviter(String name) {
		Friend friend = new Friend();
		friend.setName(name);
		friend.setInviter(true);
		mFriends.add(friend);
		Collections.sort(mFriends);
		notifyDataSetChanged();

	}

	public void addFriends(JSONArray friends) {
		try {
			for (int i = 0; i < friends.length(); i++) {
				Friend friend = Friend.toFriend(friends.getJSONObject(i));
				friend.setChatActive(mActiveChats.contains(friend.getName()));
				mFriends.add(friend);
			}
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
		}

		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public void clearFriends(boolean notify) {
		mFriends.clear();
		if (notify) {
			notifyDataSetChanged();
		}
	}

	@Override
	public int getCount() {
		return mFriends.size();
	}

	@Override
	public Object getItem(int position) {

		return mFriends.get(position);

	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public void removeFriend(String name) {
		mFriends.remove(getFriend(name));		
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Friend friend = (Friend) getItem(position);
		FriendViewHolder friendViewHolder;

		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.main_friend_item, parent, false);

			((Button) convertView.findViewById(R.id.notificationItemAccept))
					.setOnClickListener(FriendInviteResponseListener);
			((Button) convertView.findViewById(R.id.notificationItemIgnore))
					.setOnClickListener(FriendInviteResponseListener);

			friendViewHolder = new FriendViewHolder();
			friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);
			friendViewHolder.newMessageCountView = (TextView) convertView.findViewById(R.id.newMessageCount);
			friendViewHolder.vgFriend = convertView.findViewById(R.id.friendLayout);
			friendViewHolder.vgInvite = convertView.findViewById(R.id.inviteLayout);
			convertView.setTag(friendViewHolder);

		} else {
			friendViewHolder = (FriendViewHolder) convertView.getTag();
		}

		friendViewHolder.tvName.setText(friend.getName() + (friend.isInvited() ? " (invited)" : ""));

		if (friend.isInviter()) {
			friendViewHolder.vgFriend.setVisibility(View.GONE);
			friendViewHolder.vgInvite.setVisibility(View.VISIBLE);
		} else {
			friendViewHolder.vgFriend.setVisibility(View.VISIBLE);
			friendViewHolder.vgInvite.setVisibility(View.GONE);

			if (friend.isChatActive()) {
				convertView.setBackgroundColor(Color.WHITE);
			} else {
				convertView.setBackgroundColor(Color.rgb(0xee, 0xee, 0xee));
			}

			if (friend.getMessageCount() > 0) {
				friendViewHolder.newMessageCountView.setText(friend.getMessageCount().toString());
			} else {
				friendViewHolder.newMessageCountView.setText("");
			}

			if (friend.isNewFriend()) {
				friendViewHolder.tvName.setTypeface(null, Typeface.ITALIC);
			} else {
				friendViewHolder.tvName.setTypeface(null, Typeface.NORMAL);
			}

		}

		return convertView;
	}

	private OnClickListener FriendInviteResponseListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			final String action = (String) v.getTag();
			final int position = ((ListView) v.getParent().getParent().getParent()).getPositionForView((View) v
					.getParent());
			final Friend friend = (Friend) getItem(position);
			final String friendname = friend.getName();

			NetworkController.respondToInvite(friendname, action, new AsyncHttpResponseHandler() {
				public void onSuccess(String arg0) {

					Log.d(TAG, "Invitation acted upon successfully: " + action);
					if (action.equals("accept")) {
						friend.setInvited(false);
						friend.setNewFriend(true);
						
					} else {
						mFriends.remove(position);					
					}

					notifyDataSetChanged();
				}

				public void onFailure(Throwable error, String content) {
					Log.e(TAG, content);
				};
			});
		}
	};

	public static class NotificationViewHolder {
		public TextView tvName;
	}

	public static class FriendViewHolder {
		public TextView tvName;
		public TextView newMessageCountView;
		public View vgInvite;
		public View vgFriend;
	}

	

}
