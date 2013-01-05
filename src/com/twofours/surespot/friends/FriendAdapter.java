package com.twofours.surespot.friends;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
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
import com.twofours.surespot.network.NetworkController;

public class FriendAdapter extends BaseAdapter {
	private final static String TAG = "FriendAdapter";
	private final List<Friend> mFriends = new ArrayList<Friend>();
	private final List<FriendInvite> mNotifications = new ArrayList<FriendInvite>();
	private Context mContext;
	private final static int TYPE_NOTIFICATION = 0;
	private final static int TYPE_FRIEND = 1;

	public FriendAdapter(Context context) {
		mContext = context;
	}

	public void addFriend(String name, int flags) {
		Friend friend = new Friend();
		friend.setName(name);
		friend.setFlags(flags);
		mFriends.add(friend);
		notifyDataSetChanged();
	}

	public void addFriends(List<String> names, int flags) {
		for (String name : names) {
			Friend friend = new Friend();
			friend.setName(name);		
			friend.setFlags(flags);
			mFriends.add(friend);
		}
		notifyDataSetChanged();
	}

	public void addFriendInvite(String name) {
		FriendInvite friendInvite = new FriendInvite();
		friendInvite.setName(name);
		mNotifications.add(friendInvite);
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
		return mFriends.size() + mNotifications.size();
	}

	@Override
	public Object getItem(int position) {
		if (position < mNotifications.size()) {
			return mNotifications.get(position);
		}
		else {
			return mFriends.get(position - mNotifications.size());
		}
	}

	@Override
	public int getItemViewType(int position) {
		if (position < mNotifications.size()) {
			return TYPE_NOTIFICATION;
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
			case TYPE_NOTIFICATION:
				NotificationViewHolder notificationViewHolder;
				if (convertView == null) {
					LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = inflater.inflate(R.layout.notification_list_item, parent, false);

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
					convertView = inflater.inflate(R.layout.friend_list_item, parent, false);

					friendViewHolder = new FriendViewHolder();
					friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);

					convertView.setTag(friendViewHolder);
				}
				else {
					friendViewHolder = (FriendViewHolder) convertView.getTag();
				}

				Friend item1 = (Friend) getItem(position);
				friendViewHolder.tvName.setText(item1.getName());
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
					addFriend(friendname, Friend.NEW_FRIEND);

				}
			});

			// Log.d(TAG, "Title clicked, row: " + position + ", action: " +
			// action);
		}
	};

	private void removeItem(int position) {
		if (position < mNotifications.size()) {
			mNotifications.remove(position);
		}
		else {
			mFriends.remove(position - mNotifications.size());
		}
	}

	public static class NotificationViewHolder {
		public TextView tvName;
	}

	public static class FriendViewHolder {
		public TextView tvName;
	}

}
