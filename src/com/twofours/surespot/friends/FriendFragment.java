package com.twofours.surespot.friends;

import java.lang.reflect.Field;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
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
	private AlertDialog mDialog;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);

		Button tvShareLink = (Button) view.findViewById(R.id.tvShareInvite);
		tvShareLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
		tvShareLink.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				UIUtils.sendInvitation(getActivity(), MainActivity.getNetworkController());

			}
		});

		Button tvHelp = (Button) view.findViewById(R.id.tvHelp);
		tvHelp.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
		tvHelp.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mDialog = UIUtils.showHelpDialog(getActivity(), false);

			}
		});
		// mMpdInviteFriend = new MultiProgressDialog(this.getActivity(), "inviting friend", 750);

		mListView = (ListView) view.findViewById(R.id.main_list);
		mListView.setEmptyView(view.findViewById(R.id.main_list_empty));

		TextView tvWelcome = (TextView) view.findViewById(R.id.tvWelcome);
		UIUtils.setHtml(getActivity(), tvWelcome, R.string.welcome_to_surespot);

		ChatController chatController = getMainActivity().getChatController();
		if (chatController != null) {
			mMainAdapter = chatController.getFriendAdapter();
			mMainAdapter.setItemListeners(mClickListener, mLongClickListener);

			mListView.setAdapter(mMainAdapter);
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
				if (selection.equals(getString(R.string.menu_remove_friend_image))) {
					activity.removeFriendImage(friend.getName());
				}
				else {
					if (selection.equals(getString(R.string.menu_assign_alias))) {
						activity.assignFriendAlias(friend.getName());
					}
					else {
						if (selection.equals(getString(R.string.menu_remove_friend_alias))) {
							activity.removeFriendAlias(friend.getName());
						}

						else {
							if (selection.equals(getString(R.string.verify_key_fingerprints))) {
								UIUtils.showKeyFingerprintsDialog(activity, friend.getName(), friend.getAliasPlain());
							}
							else {

								if (selection.equals(getString(R.string.menu_delete_all_messages))) {

									SharedPreferences sp = activity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
									boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
									if (confirm) {
										mDialog = UIUtils.createAndShowConfirmationDialog(activity, getString(R.string.delete_all_confirmation),
												getMainActivity().getString(R.string.delete_all_title), getString(R.string.ok), getString(R.string.cancel),
												new IAsyncCallback<Boolean>() {
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
										mDialog = UIUtils.createAndShowConfirmationDialog(
												activity,
												getMainActivity().getString(R.string.delete_friend_confirmation,
														UIUtils.buildAliasString(friend.getName(), friend.getAliasPlain())),
												getMainActivity().getString(R.string.menu_delete_friend), getString(R.string.ok), getString(R.string.cancel),
												new IAsyncCallback<Boolean>() {
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
				}
			}
		}
	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

	@Override
	public void onDetach() {
		super.onDetach();

		try {
			Field childFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
			childFragmentManager.setAccessible(true);
			childFragmentManager.set(this, null);

		}
		catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mDialog != null && mDialog.isShowing()) {
			mDialog.dismiss();
		}
	}
}
