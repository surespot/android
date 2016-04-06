package com.twofours.surespot.friends;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.FriendImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;

public class FriendAdapter extends BaseAdapter {
	private final static String TAG = "FriendAdapter";

	ArrayList<Friend> mFriends = new ArrayList<Friend>();
	private NotificationManager mNotificationManager;
	private OnClickListener mClickListener;
	private OnLongClickListener mLongClickListener;
	private FriendAliasDecryptor mFriendAliasDecryptor;
	private boolean mLoading;
	private boolean mLoaded;
	private IAsyncCallback<Void> mFriendAliasChangedCallback;

	public boolean isLoaded() {
		return mLoaded;
	}

	private IAsyncCallback<Boolean> mLoadingCallback;

	private Context mContext;

	public FriendAdapter(Context context) {
		mContext = context;
		mFriendAliasDecryptor = new FriendAliasDecryptor(this);

		// clear invite notifications
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

	}

	public void setItemListeners(OnClickListener clickListener, OnLongClickListener longClickListener) {
		mClickListener = clickListener;
		mLongClickListener = longClickListener;
	}

	public synchronized boolean isLoading() {
		return mLoading;
	}

	public synchronized void setLoading(boolean loading) {
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

	public synchronized void addNewFriend(String name) {
		Friend friend = getFriend(name);
		if (friend == null) {
			friend = new Friend(name);
			mFriends.add(friend);
		}

		friend.setNewFriend(true);

		Collections.sort(mFriends);
		notifyDataSetChanged();
	}

	public synchronized boolean addFriendInvited(String name) {
		Friend friend = getFriend(name);
		if (friend == null) {
			friend = new Friend(name);
			mFriends.add(friend);
		}
		friend.setInvited(true);
		Collections.sort(mFriends);
		notifyDataSetChanged();
		return true;

	}

	public synchronized void addFriendInviter(String name) {
		Friend friend = getFriend(name);
		if (friend == null) {
			friend = new Friend(name);
			mFriends.add(friend);
		}
		friend.setInviter(true);
		Collections.sort(mFriends);
		notifyDataSetChanged();

	}

	public synchronized void setChatActive(String name, boolean b) {
		Friend friend = getFriend(name);
		if (friend != null) {
			friend.setChatActive(b);
			Collections.sort(mFriends);
			notifyDataSetChanged();
		}
	}

	public synchronized void setFriends(List<Friend> friends) {
		if (friends != null) {
			SurespotLog.v(TAG, "setFriends, adding friends to adapter: " + this + ", count: " + friends.size());
			mFriends.clear();
			mFriends.addAll(friends);
			decryptAliases();
		}
	}

	public synchronized void addFriends(Collection<Friend> friends) {
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
		decryptAliases();
	}

	private void decryptAliases() {
		SurespotLog.d(TAG, "decryptAliases");
		for (Friend friend : mFriends) {
			if (friend.hasFriendAliasAssigned() && TextUtils.isEmpty(friend.getAliasPlain())) {
				String plainText = EncryptionController.symmetricDecrypt(friend.getAliasVersion(), IdentityController.getLoggedInUser(),
						friend.getAliasVersion(), friend.getAliasIv(), friend.isAliasHashed(), friend.getAliasData());

				SurespotLog.d(TAG, "setting alias for %s", friend.getName());
				friend.setAliasPlain(plainText);
			}
		}
	
		sort();
		notifyDataSetChanged();
		notifyFriendAliasChanged();
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

	public synchronized void removeFriend(String name) {
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

			((TextView) convertView.findViewById(R.id.notificationItemAccept)).setOnClickListener(FriendInviteResponseListener);
			((TextView) convertView.findViewById(R.id.notificationItemBlock)).setOnClickListener(FriendInviteResponseListener);
			((TextView) convertView.findViewById(R.id.notificationItemIgnore)).setOnClickListener(FriendInviteResponseListener);

			friendViewHolder = new FriendViewHolder();
			friendViewHolder.statusLayout = convertView.findViewById(R.id.statusLayout);
			friendViewHolder.statusLayout.setOnClickListener(mClickListener);
			friendViewHolder.statusLayout.setOnLongClickListener(mLongClickListener);

			friendViewHolder.friendActive = convertView.findViewById(R.id.friendActive);
			friendViewHolder.friendInactive = convertView.findViewById(R.id.friendInactive);
			friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);

			friendViewHolder.vgInvite = convertView.findViewById(R.id.inviteLayout);
			friendViewHolder.tvStatus = (TextView) convertView.findViewById(R.id.friendStatus);
			friendViewHolder.vgActivity = convertView.findViewById(R.id.messageActivity);
			friendViewHolder.avatarImage = (ImageView) convertView.findViewById(R.id.friendAvatar);
			convertView.setTag(friendViewHolder);

		}
		else {
			friendViewHolder = (FriendViewHolder) convertView.getTag();
		}

		friendViewHolder.statusLayout.setTag(friend);

		friendViewHolder.tvName.setText(friend.getNameOrAlias());
		// if alias not decrypted decrypt it
		if (TextUtils.isEmpty(friend.getAliasPlain()) && friend.hasFriendAliasAssigned()) {
			mFriendAliasDecryptor.decrypt(friendViewHolder.tvName, friend);
		}

		friendViewHolder.tvName.setTextColor(mContext.getResources().getColor(
				SurespotConfiguration.isBackgroundImageSet() ? R.color.surespotGrey : android.R.color.black));

		if (friend.hasFriendImageAssigned()) {
			FriendImageDownloader.download(friendViewHolder.avatarImage, friend);
		}
		else {
			friendViewHolder.avatarImage.setImageDrawable(friendViewHolder.avatarImage.getResources().getDrawable(android.R.drawable.sym_def_app_icon));
		}

		if (friend.isInvited() || friend.isNewFriend() || friend.isInviter() || friend.isDeleted()) {
			friendViewHolder.tvStatus.setTypeface(null, Typeface.ITALIC);
			friendViewHolder.tvStatus.setVisibility(View.VISIBLE);
			// TODO expose flags and use switch

			// add a space to workaround text clipping (so weak)
			// http://stackoverflow.com/questions/4353836/italic-textview-with-wrap-contents-seems-to-clip-the-text-at-right-edge
			if (friend.isDeleted()) {
				friendViewHolder.tvStatus.setText(mContext.getString(R.string.friend_status_is_deleted) + " ");
			}
			if (friend.isInvited()) {
				friendViewHolder.tvStatus.setText(mContext.getString(R.string.friend_status_is_invited) + " ");
			}

			if (friend.isInviter()) {
				friendViewHolder.tvStatus.setText(mContext.getString(R.string.friend_status_is_inviting) + " ");
			}

			if (friend.isNewFriend()) {
				friendViewHolder.tvStatus.setText("");
			}

		}
		else {
			friendViewHolder.tvStatus.setVisibility(View.GONE);
		}

		if (friend.isInviter()) {
			friendViewHolder.statusLayout.setEnabled(false);
			friendViewHolder.vgInvite.setVisibility(View.VISIBLE);
			friendViewHolder.vgActivity.setVisibility(View.GONE);
			friendViewHolder.friendActive.setVisibility(View.GONE);
			friendViewHolder.friendInactive.setVisibility(View.VISIBLE);
		}
		else {
			friendViewHolder.statusLayout.setEnabled(true);
			friendViewHolder.vgInvite.setVisibility(View.GONE);

			if (friend.isChatActive()) {
				friendViewHolder.friendActive.setVisibility(View.VISIBLE);
				friendViewHolder.friendInactive.setVisibility(View.GONE);
			}
			else {
				friendViewHolder.friendActive.setVisibility(View.GONE);
				friendViewHolder.friendInactive.setVisibility(View.VISIBLE);
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
					friend.setInviter(false);
					if (action.equals("accept")) {
						friend.setNewFriend(true);
					}
					else {
						if (action.equals("block") || action.equals("ignore")) {

							if (!friend.isDeleted()) {
								mFriends.remove(position);
							}
						}
					}
					mNotificationManager.cancel(IdentityController.getLoggedInUser() + ":" + friendname,
							SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
					Collections.sort(mFriends);
					notifyDataSetChanged();
				}

				public void onFailure(Throwable error, String content) {
					SurespotLog.i(TAG, error, "respondToInvite");
					Utils.makeToast(MainActivity.getContext(), mContext.getString(R.string.could_not_respond_to_invite));
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
		public View statusLayout;
		public ImageView avatarImage;
		public View friendActive;
		public View friendInactive;
	}

	public synchronized void sort() {
		if (mFriends != null) {
			Collections.sort(mFriends);
		}
	}

	public synchronized Collection<String> getFriendNames() {
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

	public synchronized ArrayList<Friend> getActiveChatFriends() {
		if (mFriends == null)
			return null;
		ArrayList<Friend> friends = new ArrayList<Friend>();
		for (Friend friend : mFriends) {
			if (friend.isChatActive()) {
				friends.add(friend);
			}
		}
		return friends;
	}

	public synchronized int getFriendCount() {
		if (mFriends == null)
			return 0;
		return mFriends.size();

	}

	public void registerFriendAliasChangedCallback(IAsyncCallback<Void> iAsyncCallback) {
		mFriendAliasChangedCallback = iAsyncCallback;
	}

	public void notifyFriendAliasChanged() {
		if (mFriendAliasChangedCallback != null) {
			mFriendAliasChangedCallback.handleResponse(null);
		}
	}
}
