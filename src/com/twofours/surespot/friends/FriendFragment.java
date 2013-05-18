package com.twofours.surespot.friends;

import java.util.Timer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.ui.UIUtils;

public class FriendFragment extends SherlockFragment {
	private FriendAdapter mMainAdapter;

	protected static final String TAG = "FriendFragment";
	// private MultiProgressDialog mMpdInviteFriend;
	// private ChatController mChatController;
	private ListView mListView;
	private Timer mTimer;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);

		// mMpdInviteFriend = new MultiProgressDialog(this.getActivity(), "inviting friend", 750);

		mListView = (ListView) view.findViewById(R.id.main_list);
		mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
		
		TextView tvBackupWarning = (TextView) view.findViewById(R.id.backupIdentitiesWarning);		
		Spannable s1 = new SpannableString(getString(R.string.help_backupIdentities1));
	    s1.setSpan(new ForegroundColorSpan(Color.RED), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    
	    tvBackupWarning.setText(s1);
	    
	    TextView tvWelcome = (TextView) view.findViewById(R.id.tvWelcome);
		UIUtils.setHtml(getActivity(), tvWelcome, R.string.welcome_to_surespot);
		

		ChatController chatController = getMainActivity().getChatController();
		if (chatController != null) {
			mMainAdapter = chatController.getFriendAdapter();
			mMainAdapter.setItemListeners(mClickListener, mLongClickListener);

			mListView.setAdapter(mMainAdapter);

			if (!mMainAdapter.isLoaded()) {
				SurespotLog.v(TAG, "setting progressbarvisible");
				view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
			}

			SurespotLog.v(TAG, "friend adapter set, : " + mMainAdapter);
			SurespotLog.v(TAG, "setting loading callback");
			mMainAdapter.setLoadingCallback(new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(final Boolean loading) {

					if (!loading) {
						if (mTimer != null) {
							mTimer.cancel();
							mTimer = null;
						}

						SurespotLog.v(TAG, "tearing progress down");						
						view.findViewById(R.id.progressBar).setVisibility(View.GONE);
					}
				}
			});
		}

		return view;
	}

	OnClickListener mClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			Friend friend = (Friend) view.getTag();
			if (friend.isFriend()) {

				ChatController chatController = getMainActivity().getChatController();
				if (chatController != null) {

					chatController.setCurrentChat(friend.getName());
				}
			}
		}
	};

	OnLongClickListener mLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			Friend friend = (Friend) view.getTag();

			if (!friend.isInviter()) {
				FriendMenuFragment dialog = new FriendMenuFragment();

				dialog.setActivityAndFriend(friend, new IAsyncCallbackTriplet<DialogInterface, Friend, String>() {
					public void handleResponse(DialogInterface dialogi, Friend friend, String selection) {
						handleMenuSelection(dialogi, friend, selection);
					};
				});

				dialog.show(getActivity().getSupportFragmentManager(), "FriendMenuFragment");
			}
			return true;

		}

	};

	private void handleMenuSelection(final DialogInterface dialogi, final Friend friend, String selection) {
		final MainActivity activity = this.getMainActivity();

		if (selection.equals(getString(R.string.menu_close_tab))) {
			activity.getChatController().closeTab(friend.getName());
		}
		else {

			if (selection.equals(getString(R.string.menu_assign_image))) {
				activity.uploadFriendImage(friend.getName());
			}
			else {

				if (selection.equals(getString(R.string.menu_delete_all_messages))) {

					SharedPreferences sp = activity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
					if (confirm) {
						UIUtils.createAndShowConfirmationDialog(activity, getString(R.string.delete_all_confirmation),
								getMainActivity().getString(R.string.delete_all_title), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
									public void handleResponse(Boolean result) {
										if (result) {
											activity.getChatController().deleteMessages(friend);
										}

									};
								});
					}
					else {
						activity.getChatController().deleteMessages(friend);
					}
				}
				else {
					if (selection.equals(getString(R.string.menu_delete_friend))) {
						UIUtils.createAndShowConfirmationDialog(activity, getMainActivity().getString(R.string.delete_friend_confirmation, friend.getName()), getMainActivity().getString(R.string.menu_delete_friend), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
							public void handleResponse(Boolean result) {
								if (result) {
									activity.getChatController().deleteFriend(friend);
								}
								else {
									dialogi.cancel();
								}
							};
						});
					}
				}
			}
		}

	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}
}
