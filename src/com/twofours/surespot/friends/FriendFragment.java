package com.twofours.surespot.friends;

import java.util.Timer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ListView;

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
		//
		// Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		// addFriendButton.setOnClickListener(new View.OnClickListener() {
		// @Override
		// public void onClick(View v) {
		// inviteFriend();
		// }
		// });
		//
		// EditText editText = (EditText) view.findViewById(R.id.etFriend);
		// editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH),
		// new LetterOrDigitInputFilter() });
		// editText.setOnEditorActionListener(new OnEditorActionListener() {
		// @Override
		// public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		// boolean handled = false;
		// if (actionId == EditorInfo.IME_ACTION_DONE) {
		// //
		// inviteFriend();
		// handled = true;
		// }
		// return handled;
		// }
		// });

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
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// view.findViewById(R.id.main_list_empty).setVisibility(View.GONE);
						// only show the dialog if we haven't loaded within 500 ms
						// mTimer = new Timer();
						// mTimer.schedule(new TimerTask() {
						//
						// @Override
						// public void run() {
						//
						// Handler handler = MainActivity.getMainHandler();
						// if (handler != null) {
						// handler.post(new Runnable() {
						//
						// @Override
						// public void run() {
						// if (loading) {
						// SurespotLog.v(TAG, "showing progress");
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// }
						// }
						// });
						// }
						//
						// }
						// }, 200);

						// }
						// else {
						if (mTimer != null) {
							mTimer.cancel();
							mTimer = null;
						}

						SurespotLog.v(TAG, "tearing progress down");
						mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
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

		if (selection.equals("close tab")) {
			activity.getChatController().closeTab(friend.getName());
		}
		else {

			if (selection.equals("assign image")) {
				activity.uploadFriendImage(friend.getName());
			}
			else {

				if (selection.equals("delete all messages")) {

					SharedPreferences sp = activity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
					if (confirm) {
						UIUtils.createAndShowConfirmationDialog(activity, "are you sure you wish to delete all messages?",
								"delete all messages", "ok", "cancel", new IAsyncCallback<Boolean>() {
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
					if (selection.equals("delete friend")) {
						UIUtils.createAndShowConfirmationDialog(activity, "are you sure you wish to delete friend: " + friend.getName()
								+ "?", "delete friend", "ok", "cancel", new IAsyncCallback<Boolean>() {
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
