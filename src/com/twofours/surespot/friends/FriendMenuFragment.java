package com.twofours.surespot.friends;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class FriendMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "FriendMenuFragment";
	private Friend mFriend;
	private MainActivity mActivity;
	private String[] mMenuItemArray;

	public void setActivityAndFriend(MainActivity activity, Friend friend) {
		mFriend = friend;
		mActivity = activity;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		mMenuItemArray = null;

		//
		if (!mFriend.isInviter()) {
			mMenuItemArray = new String[2];
			mMenuItemArray[1] = "delete friend";
		}
		else {
			mMenuItemArray = new String[1];
		}

		mMenuItemArray[0] = "delete all messages";

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mFriend == null)
					return;

				switch (which) {
				case 0:

					SharedPreferences sp = mActivity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
					if (confirm) {
						UIUtils.createAndShowConfirmationDialog(mActivity, "are you sure you wish to delete all messages?",
								"delete all messages", "ok", "cancel", new IAsyncCallback<Boolean>() {
									public void handleResponse(Boolean result) {
										if (result) {
											mActivity.getChatController().deleteMessages(mFriend);
										}
										else {
											dialogi.cancel();
										}
									};
								});
					}
					else {
						mActivity.getChatController().deleteMessages(mFriend);
					}

					break;
				case 1:

					UIUtils.createAndShowConfirmationDialog(mActivity, "are you sure you wish to delete friend: " + mFriend.getName() + "?",
							"delete friend", "ok", "cancel", new IAsyncCallback<Boolean>() {
								public void handleResponse(Boolean result) {
									if (result) {
										mActivity.getChatController().deleteFriend(mFriend);
									}
									else {
										dialogi.cancel();
									}
								};
							});
					break;
				}
			}
		});

		AlertDialog dialog = builder.create();
		return dialog;
	}

}
