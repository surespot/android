package com.twofours.surespot.chat;

import java.util.Observable;
import java.util.Observer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class TextMessageMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "TextMessageMenuFragment";
	private SurespotMessage mMessage;
	private MainActivity mActivity;
	private String[] mMenuItemArray;
	private Observer mMessageObserver;
	private boolean mMyMessage;
	private boolean mDeleteOnly;

	public void setActivityAndMessage(MainActivity activity, SurespotMessage message) {
		mMessage = message;
		mActivity = activity;
	}

	private void setButtonVisibility() {
		if (mDeleteOnly) {
			return;
		}
		AlertDialog dialog = (AlertDialog) TextMessageMenuFragment.this.getDialog();
		ListView listview = dialog.getListView();

		if (!mMyMessage) {
			listview.getChildAt(0).setEnabled(mMessage.isShareable() && FileUtils.isExternalStorageMounted());
		}
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

				mMessage.deleteObservers();

				AlertDialog dialog = (AlertDialog) TextMessageMenuFragment.this.getDialog();
				ListView listview = dialog.getListView();

				if (!listview.getChildAt(which).isEnabled()) {
					return;
				}

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
		dialog.setOnShowListener(new OnShowListener() {

			@Override
			public void onShow(DialogInterface dialog) {
				setButtonVisibility();

			}
		});

		// TODO listen to message control events and handle delete as well
		mMessageObserver = new Observer() {

			@Override
			public void update(Observable observable, Object data) {
				setButtonVisibility();

			}
		};
		if (mMessage != null) {
			mMessage.addObserver(mMessageObserver);
		}
		return dialog;
	}

}
