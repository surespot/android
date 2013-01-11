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
import com.twofours.surespot.friends.FriendInvite;
import com.twofours.surespot.network.NetworkController;

public class MainAdapter extends BaseAdapter {
	private final static String TAG = "MainAdapter";

	private final ArrayList<Friend> mFriends = new ArrayList<Friend>();
	private final ArrayList<FriendInvite> mInvites = new ArrayList<FriendInvite>();
	private final ArrayList<String> mActiveChats = new ArrayList<String>();

	private Context mContext;
	public final static int TYPE_INVITE = 0;
	public final static int TYPE_FRIEND = 1;

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
			}
			catch (JSONException e) {
				Log.e(TAG, "Error decoding active chat json list: " + e.toString());
			}
		}
	}

	/*
	 * public void refreshFlags() { for (Friend friend : mFriends) { if (mActiveChats.contains(friend.getName())) {
	 * friend.setFlags(friend.getFlags() | ~Friend.ACTIVE_CHAT ); } else { friend.setFlags(friend.getFlags() & ~Friend.ACTIVE_CHAT ); } }
	 * notifyDataSetChanged(); }
	 */

	public void messageReceived(String name) {
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
			if (friend.getName().equals(friendName)) { return friend; }
		}
		return null;
	}

	public void addNewFriend(String name) {
		Friend friend = new Friend();
		friend.setName(name);
		friend.setFlags(Friend.NEW_FRIEND);
		mFriends.add(friend);
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public void addFriends(JSONArray names) {
		try {
			for (int i = 0; i < names.length(); i++) {
				Friend friend = new Friend();
				String name = names.getString(i);
				friend.setName(name);
				if (mActiveChats.contains(name)) {
					friend.setFlags(Friend.ACTIVE_CHAT);
				}
				mFriends.add(friend);
			}
		}
		catch (JSONException e) {
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


	public void addFriendInvite(String name) {
		FriendInvite friendInvite = new FriendInvite();
		friendInvite.setName(name);
		mInvites.add(friendInvite);
		notifyDataSetChanged();

	}
	
	public void addFriendInvites(JSONArray names) {
		try {
			for (int i = 0; i < names.length(); i++) {
				FriendInvite friendInvite = new FriendInvite();
				String name = names.getJSONObject(i).getString("data");
				friendInvite.setName(name);				
				mInvites.add(friendInvite);
				notifyDataSetChanged();
			}
		}
		catch (JSONException e) {
			Log.e(TAG, e.toString());
		}

		Collections.sort(mFriends);
		notifyDataSetChanged();
	}
	
	public void clearInvites(boolean notify) {
		mInvites.clear();
		if (notify) {
			notifyDataSetChanged();
		}
	}



	@Override
	public int getCount() {
		return mFriends.size() + mInvites.size();
	}

	@Override
	public Object getItem(int position) {
		if (position < mInvites.size()) {
			return mInvites.get(position);
		}
		else {
			return mFriends.get(position - mInvites.size());
		}
	}

	@Override
	public int getItemViewType(int position) {
		if (position < mInvites.size()) {
			return TYPE_INVITE;
		}
		else {
			return TYPE_FRIEND;
		}
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		int type = getItemViewType(position);

		switch (type) {
			case TYPE_INVITE:
				NotificationViewHolder notificationViewHolder;
				if (convertView == null) {
					LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = inflater.inflate(R.layout.main_notification_item, parent, false);

					notificationViewHolder = new NotificationViewHolder();
					notificationViewHolder.tvName = (TextView) convertView.findViewById(R.id.notificationItemText);

					((Button) convertView.findViewById(R.id.notificationItemAccept)).setOnClickListener(FriendInviteResponseListener);
					((Button) convertView.findViewById(R.id.notificationItemIgnore)).setOnClickListener(FriendInviteResponseListener);

					convertView.setTag(notificationViewHolder);
				}
				else {
					notificationViewHolder = (NotificationViewHolder) convertView.getTag();
				}

				FriendInvite item = (FriendInvite) getItem(position);
				notificationViewHolder.tvName.setText(item.getName());
				break;
			case TYPE_FRIEND:
				FriendViewHolder friendViewHolder;
				if (convertView == null) {
					LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = inflater.inflate(R.layout.main_friend_item, parent, false);

					friendViewHolder = new FriendViewHolder();
					friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);
					friendViewHolder.newMessageCountView = (TextView) convertView.findViewById(R.id.newMessageCount);
					convertView.setTag(friendViewHolder);
				}
				else {
					friendViewHolder = (FriendViewHolder) convertView.getTag();
				}

				Friend item1 = (Friend) getItem(position);

				if ((item1.getFlags() & Friend.ACTIVE_CHAT) == Friend.ACTIVE_CHAT) {
					convertView.setBackgroundColor(Color.WHITE);
				}
				else {
					convertView.setBackgroundColor(Color.rgb(0xee, 0xee, 0xee));
				}

				if (item1.getMessageCount() > 0) {
					friendViewHolder.newMessageCountView.setText(item1.getMessageCount().toString());
				}
				else {
					friendViewHolder.newMessageCountView.setText("");
				}

				if ((item1.getFlags() & Friend.NEW_FRIEND) == Friend.NEW_FRIEND) {
					friendViewHolder.tvName.setText(item1.getName() + " [new]");
					friendViewHolder.tvName.setTypeface(null, Typeface.ITALIC);
				}
				else {
					friendViewHolder.tvName.setText(item1.getName());
					friendViewHolder.tvName.setTypeface(null, Typeface.NORMAL);
				}

				break;

		}

		return convertView;
	}

	private OnClickListener FriendInviteResponseListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			final String action = (String) v.getTag();
			final int position = ((ListView) v.getParent().getParent()).getPositionForView((View) v.getParent());
			final String friendname = ((FriendInvite) getItem(position)).getName();

			NetworkController.respondToInvite(friendname, action, new AsyncHttpResponseHandler() {
				public void onSuccess(String arg0) {

					Log.d(TAG, "Invitation acted upon successfully: " + action);

					// delete invite
					removeItem(position);
					if (action.equals("accept")) {
						addNewFriend(friendname);
					}
				}

				public void onFailure(Throwable error, String content) {
					Log.e(TAG, content);
				};
			});
		}
	};

	private void removeItem(int position) {
		if (position < mInvites.size()) {
			mInvites.remove(position);
		}
		else {
			mFriends.remove(position - mInvites.size());
		}
		notifyDataSetChanged();
	}

	public static class NotificationViewHolder {
		public TextView tvName;
	}

	public static class FriendViewHolder {
		public TextView tvName;
		public TextView newMessageCountView;
		public View itemLayout;
		public View newFriendView;
		public View activeChatView;
	}

}
