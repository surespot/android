package com.twofours.surespot.friends;

import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class FriendAdapter extends BaseAdapter {
	private final static String TAG = "FriendAdapter";

	private final ArrayList<Friend> mFriends = new ArrayList<Friend>();
//	private ArrayList<String> mActiveChats = new ArrayList<String>();
	private NotificationManager mNotificationManager;

	private Context mContext;

	public FriendAdapter(Context context) {
		mContext = context;
		// refreshActiveChats();
		// clear invite notifications
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

	}

//	public void refreshActiveChats() {
//		mActiveChats = SurespotApplication.getStateController().loadActiveChats();
//	}
//
//	public ArrayList<String> getActiveChats() {
//		return mActiveChats;
//	}

	/*
	 * public void refreshFlags() { for (Friend friend : mFriends) { if (mActiveChats.contains(friend.getName())) {
	 * friend.setFlags(friend.getFlags() | ~Friend.ACTIVE_CHAT ); } else { friend.setFlags(friend.getFlags() & ~Friend.ACTIVE_CHAT ); } }
	 * notifyDataSetChanged(); }
	 */

	public void messageReceived(String name) {
		SurespotLog.v(TAG, "message received");
		Friend friend = getFriend(name);
		if (friend != null) {
			friend.incMessageCount(1);
//			if (!mActiveChats.contains(name)) {
//				mActiveChats.add(name);
//			}
			Collections.sort(mFriends);
			notifyDataSetChanged();
		}
	}

	public void messageDeltaReceived(String name, int delta) {
		Friend friend = getFriend(name);
		if (friend != null) {
			friend.setMessageCount(delta);
			Collections.sort(mFriends);
//			if (delta > 0 && !mActiveChats.contains(name)) {
//				mActiveChats.add(name);
//			}
			notifyDataSetChanged();
		}
	}

	private Friend getFriend(String friendName) {
		for (Friend friend : mFriends) {
			if (friend.getName().equals(friendName)) {
				return friend;
			}
		}
		return null;
	}
	
	public void addActiveFriend(String name) {

		Friend friend = getFriend(name);
		if (friend == null) {
			friend = new Friend();
			mFriends.add(friend);
			friend.setName(name);
		}

		friend.setChatActive(true);
		Collections.sort(mFriends);
		notifyDataSetChanged();
		
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
	
	public void setChatActive(String name, boolean b) {
		Friend friend = getFriend(name);
		if (friend != null) {
			friend.setChatActive(b);
		}
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public void addFriends(JSONArray friends) {
		try {
			for (int i = 0; i < friends.length(); i++) {
				JSONObject jsonFriend = friends.getJSONObject(i);
				Friend friend = Friend.toFriend(jsonFriend);
			//	friend.setChatActive(mActiveChats.contains(friend.getName()));
				int index = mFriends.indexOf(friend);
				if (index == -1) {
					mFriends.add(friend);
				}
				else {
					friend = mFriends.get(index);
					//friend.setChatActive(mActiveChats.contains(friend.getName()));
					friend.update(jsonFriend);

				}

			}
		}
		catch (JSONException e) {
			SurespotLog.e(TAG, e.toString(), e);
		}

		sort();
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

			((Button) convertView.findViewById(R.id.notificationItemAccept)).setOnClickListener(FriendInviteResponseListener);
			((Button) convertView.findViewById(R.id.notificationItemIgnore)).setOnClickListener(FriendInviteResponseListener);

			friendViewHolder = new FriendViewHolder();
			friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);
			friendViewHolder.vgInvite = convertView.findViewById(R.id.inviteLayout);
			friendViewHolder.tvStatus = (TextView) convertView.findViewById(R.id.friendStatus);
			convertView.setTag(friendViewHolder);

		}
		else {
			friendViewHolder = (FriendViewHolder) convertView.getTag();
		}

		friendViewHolder.tvName.setText(friend.getName());

		int messageCount = friend.getMessageCount();

		// TODO cleanup this logic
		if (friend.isInvited() || friend.isNewFriend() || friend.isInviter() || messageCount > 0) {
			friendViewHolder.tvStatus.setTypeface(null, Typeface.ITALIC);
			friendViewHolder.tvStatus.setVisibility(View.VISIBLE);
			// TODO expose flags and use switch
			if (friend.isInvited()) {
				friendViewHolder.tvStatus.setText("invited");
			}
			if (friend.isNewFriend()) {
				friendViewHolder.tvStatus.setText("invitation accepted");
			}
			if (friend.isInviter()) {
				friendViewHolder.tvStatus.setText("is inviting you to be friends");
			}

		}
		else {
			friendViewHolder.tvStatus.setVisibility(View.GONE);
			// friendViewHolder.tvName.setTypeface(null, Typeface.NORMAL);
		}

		if (friend.isInviter()) {
			friendViewHolder.vgInvite.setVisibility(View.VISIBLE);
		}
		else {
			friendViewHolder.vgInvite.setVisibility(View.GONE);

			if (friend.isChatActive()) {
				convertView.setBackgroundColor(Color.WHITE);
			}
			else {
				convertView.setBackgroundColor(Color.rgb(0xee, 0xee, 0xee));
			}

			if (messageCount > 0) {

				String currentStatus = friendViewHolder.tvStatus.getText().toString();
				String messageCountString = messageCount + " unread message" + (messageCount > 1 ? "s" : "");

				if (!currentStatus.isEmpty()) {
					if (currentStatus.contains("unread message")) {
						currentStatus = currentStatus.replaceAll("\\d* unread messages?", messageCountString);

					}
					else {
						currentStatus += "\n" + messageCountString;
					}

				}
				else {
					currentStatus = messageCountString;
				}
				friendViewHolder.tvStatus.setText(currentStatus);
			}

		}

		return convertView;
	}

	private OnClickListener FriendInviteResponseListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			final String action = (String) v.getTag();
			final int position = ((ListView) v.getParent().getParent().getParent()).getPositionForView((View) v.getParent());
			final Friend friend = (Friend) getItem(position);
			final String friendname = friend.getName();

			SurespotApplication.getNetworkController().respondToInvite(friendname, action, new AsyncHttpResponseHandler() {
				public void onSuccess(String arg0) {

					SurespotLog.d(TAG, "Invitation acted upon successfully: " + action);
					if (action.equals("accept")) {
						friend.setInvited(false);
						friend.setNewFriend(true);
					}
					else {
						mFriends.remove(position);
					}
					mNotificationManager.cancel(friendname, SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
					notifyDataSetChanged();
				}

				public void onFailure(Throwable error, String content) {
					SurespotLog.w(TAG, "respondToInvity", error);
					Utils.makeToast(SurespotApplication.getContext(), "Could not respond to invite, please try again later.");
				};
			});
		}
	};

	public static class NotificationViewHolder {
		public TextView tvName;
	}

	public static class FriendViewHolder {
		public TextView tvName;
		public TextView tvStatus;
		public View vgInvite;
	}
//
//	public ArrayList<String> getFriends() {
//		return mActiveChats;
//	}

	public void sort() {
		if (mFriends != null) {
			Collections.sort(mFriends);
		}
	}

	

	

}
