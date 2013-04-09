package com.twofours.surespot.chat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Observable;
import java.util.Observer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;

public class MessageDialogMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "MessageDialogMenuFragment";
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
		AlertDialog dialog = (AlertDialog) MessageDialogMenuFragment.this.getDialog();
		ListView listview = dialog.getListView();

		if (!mMyMessage) {
			listview.getChildAt(0).setEnabled(mMessage.isShareable() && FileUtils.isExternalStorageMounted());
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		mMenuItemArray = null;

		if (mMessage != null && !mMessage.getFrom().equals(IdentityController.getLoggedInUser())
				&& mMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
			mMenuItemArray = new String[2];
			mMenuItemArray[0] = "save to gallery";
			mMenuItemArray[1] = "delete";
		}
		else {
			if (mMessage != null && mMessage.getId() != null && mMessage.getFrom().equals(IdentityController.getLoggedInUser())
					&& mMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
				mMenuItemArray = new String[2];
				mMenuItemArray[0] = mMessage.isShareable() ? "lock" : "unlock";
				mMenuItemArray[1] = "delete";
				mMyMessage = true;
			}
			else {
				mMenuItemArray = new String[1];
				mMenuItemArray[0] = "delete";
				mDeleteOnly = true;
			}
		}

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogi, int which) {
				if (mMessage == null)
					return;

				mMessage.deleteObservers();

				AlertDialog dialog = (AlertDialog) MessageDialogMenuFragment.this.getDialog();
				ListView listview = dialog.getListView();

				if (!listview.getChildAt(which).isEnabled()) {
					return;
				}

				if (mDeleteOnly || which == 1) {
					getMainActivity().getChatController().deleteMessage(mMessage);
				}
				else {
					if (mMyMessage) {
						getMainActivity().getChatController().toggleMessageShareable(mMessage);
					}
					else {

						// Utils.makeToast(mActivity, "saving image in gallery");
						new AsyncTask<Void, Void, Boolean>() {

							@Override
							protected Boolean doInBackground(Void... params) {
								try {

									File galleryFile = FileUtils.createGalleryImageFile(".jpg");
									FileOutputStream fos = new FileOutputStream(galleryFile);
									InputStream imageStream = MainActivity.getNetworkController().getFileStream(mActivity,
											mMessage.getData());

									EncryptionController.runDecryptTask(mMessage.getOurVersion(), mMessage.getOtherUser(),
											mMessage.getTheirVersion(), mMessage.getIv(), new BufferedInputStream(imageStream), fos);

									FileUtils.galleryAddPic(mActivity, galleryFile.getAbsolutePath());
									return true;

								}

								catch (IOException e) {
									SurespotLog.w(TAG, "onCreateDialog", e);

								}
								return false;
							}

							protected void onPostExecute(Boolean result) {
								if (result) {
									Utils.makeToast(mActivity, "image saved to gallery");
								}
								else {
									Utils.makeToast(mActivity, "error saving image to gallery");
								}
							};
						}.execute();

					}
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

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

}
