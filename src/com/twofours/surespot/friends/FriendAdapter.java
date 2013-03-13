package com.twofours.surespot.friends;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class FriendAdapter extends BaseAdapter {
	private final static String TAG = "FriendAdapter";

	ArrayList<Friend> mFriends = new ArrayList<Friend>();
	private NotificationManager mNotificationManager;
	private boolean mLoading;
	private boolean mLoaded;

	public boolean isLoaded() {
		return mLoaded;
	}

	private IAsyncCallback<Boolean> mLoadingCallback;

	private Context mContext;

	public FriendAdapter(Context context) {
		mContext = context;

		// clear invite notifications
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

	}

	public boolean isLoading() {
		return mLoading;
	}

	public void setLoading(boolean loading) {
		mLoading = loading;
		mLoaded = true;
		if (mLoadingCallback != null) {
			mLoadingCallback.handleResponse(loading);
		}
	}

	public void setLoadingCallback(IAsyncCallback<Boolean> callback) {
		mLoadingCallback = callback;
	}

	public Friend getFriend(String friendName) {
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
			friend = new Friend(name);
			mFriends.add(friend);
			friend.setName(name);
		}

		friend.setNewFriend(true);
		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public boolean addFriendInvited(String name) {
		// if it's there already the other party must have invited at the same time in which
		// case the invitation is automatically accepted so it will appear in the friends list through those means
		Friend friend = getFriend(name);
		if (friend != null) {
			return false;
		}

		friend = new Friend(name);
		friend.setInvited(true);
		mFriends.add(friend);
		Collections.sort(mFriends);
		notifyDataSetChanged();
		return true;

	}

	public void addFriendInviter(String name) {
		Friend friend = new Friend(name);
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

	// public void setMessageActivity(String username, boolean activity) {
	// Friend friend = getFriend(username);
	// if (friend != null) {
	// friend.setMessageActivity(activity);
	// }
	// Collections.sort(mFriends);
	// notifyDataSetChanged();
	//
	// }

	public void setFriends(List<Friend> friends) {
		SurespotLog.v(TAG, "setFriends, adding friends to adapter: " + this + ", count: " + friends.size());

		mFriends.clear();
		mFriends.addAll(friends);
		sort();
		notifyDataSetChanged();
	}

	public void addFriends(Collection<Friend> friends) {
		SurespotLog.v(TAG, "addFriends, adding friends to adapter: " + this + ", count: " + friends.size());

		for (Friend friend : friends) {

			int index = mFriends.indexOf(friend);
			if (index == -1) {
				mFriends.add(friend);
			}
			else {
				Friend incumbent = mFriends.get(index);
				incumbent.update(friend);
			}
		}

		sort();
		notifyDataSetChanged();
	}

	// public void clearFriends(boolean notify) {
	// mFriends.clear();
	// if (notify) {
	// notifyDataSetChanged();
	// }
	// }

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
			friendViewHolder.vgActivity = convertView.findViewById(R.id.messageActivity);
			convertView.setTag(friendViewHolder);

		}
		else {
			friendViewHolder = (FriendViewHolder) convertView.getTag();
		}

		friendViewHolder.tvName.setText(friend.getName());

		if (friend.isInvited() || friend.isNewFriend() || friend.isInviter()) {
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
		}

		if (friend.isInviter()) {
			friendViewHolder.vgInvite.setVisibility(View.VISIBLE);
			friendViewHolder.vgActivity.setVisibility(View.GONE);
		}
		else {
			friendViewHolder.vgInvite.setVisibility(View.GONE);

			if (friend.isChatActive()) {
				convertView.setBackgroundColor(Color.WHITE);
			}
			else {
				convertView.setBackgroundColor(Color.rgb(0xee, 0xee, 0xee));
			}

			friendViewHolder.vgActivity.setVisibility(friend.isMessageActivity() ? View.VISIBLE : View.GONE);

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

			MainActivity.getNetworkController().respondToInvite(friendname, action, new AsyncHttpResponseHandler() {
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
					Collections.sort(mFriends);
					notifyDataSetChanged();
				}

				public void onFailure(Throwable error, String content) {
					SurespotLog.w(TAG, "respondToInvity", error);
					Utils.makeToast(MainActivity.getContext(), "Could not respond to invite, please try again later.");
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
		public View vgActivity;
	}

	public void sort() {
		if (mFriends != null) {
			Collections.sort(mFriends);
		}
	}

	public Collection<String> getFriendNames() {
		if (mFriends == null)
			return null;
		ArrayList<String> names = new ArrayList<String>();
		for (Friend friend : mFriends) {
			names.add(friend.getName());
		}
		return names;
	}

	public ArrayList<Friend> getFriends() {
		return mFriends;
	}

	public ArrayList<String> getActiveChats() {
		if (mFriends == null)
			return null;
		ArrayList<String> names = new ArrayList<String>();
		for (Friend friend : mFriends) {
			if (friend.isChatActive()) {
				names.add(friend.getName());
			}
		}
		return names;
	}

}
