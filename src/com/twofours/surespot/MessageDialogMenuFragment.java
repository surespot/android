package com.twofours.surespot;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class MessageDialogMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "MessageDialogMenuFragment";
	private SurespotMessage mMessage;
	private Activity mActivity;

	public void setActivityAndMessage(Activity activity, SurespotMessage message) {
		mMessage = message;
		mActivity = activity;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		ArrayList<String> menuItems = new ArrayList<String>();
		boolean allowSave = false;
		if (mMessage != null && !mMessage.getFrom().equals(IdentityController.getLoggedInUser()) && mMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
			allowSave = true;
			menuItems.add("save to gallery");
		}

		menuItems.add("delete");

		final boolean finalAllowSave = allowSave;

		String[] itemArray = new String[menuItems.size()];
		menuItems.toArray(itemArray);
		builder.setItems(itemArray, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (mMessage == null)
					return;
				switch (which) {
				case 0:
					if (finalAllowSave) {
					//	Utils.makeToast(mActivity, "saving image in gallery");
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
					else {
						// if it hasn't been deleted, show popup
						getMainActivity().getChatController().deleteMessage(mMessage);
					}
					break;
				case 1:
					getMainActivity().getChatController().deleteMessage(mMessage);
					break;
				}
			}
		});

		return builder.create();
	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

}
