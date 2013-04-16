package com.twofours.surespot.chat;

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

public class TextMessageMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "TextMessageMenuFragment";
	private SurespotMessage mMessage;
	private MainActivity mActivity;
	private String[] mMenuItemArray;

	public void setActivityAndMessage(MainActivity activity, SurespotMessage message) {
		mMessage = message;
		mActivity = activity;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		mMenuItemArray = new String[1];
		mMenuItemArray[0] = "delete message";

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mMessage == null)
					return;
				
				SharedPreferences sp = mActivity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
				boolean confirm = sp.getBoolean("pref_delete_message", true);
				if (confirm) {
					UIUtils.createAndShowConfirmationDialog(mActivity, "are you sure you wish to delete this message?", "delete message",
							"ok", "cancel", new IAsyncCallback<Boolean>() {
								public void handleResponse(Boolean result) {
									if (result) {
										mActivity.getChatController().deleteMessage(mMessage);
									}
									else {
										dialogi.cancel();
									}
								};
							});
				}
				else {
					mActivity.getChatController().deleteMessage(mMessage);
				}

			}
		});

		AlertDialog dialog = builder.create();
		return dialog;
	}

}
