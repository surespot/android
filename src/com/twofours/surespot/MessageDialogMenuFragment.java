package com.twofours.surespot;

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
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class MessageDialogMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "MessageDialogMenuFragment";
	private SurespotMessage mMessage;
	private MainActivity mActivity;
	private String[] mMenuItemArray;
	private Observer mMessageObserver;

	public void setActivityAndMessage(MainActivity activity, SurespotMessage message) {
		mMessage = message;

		// TODO listen to message control events and handle delete as well
		mMessageObserver = new Observer() {

			@Override
			public void update(Observable observable, Object data) {
				setButtonVisibility();

			}
		};
		mActivity = activity;
	}

	private void setButtonVisibility() {
		//TODO make custom adapter and hide items

		AlertDialog dialog = (AlertDialog) MessageDialogMenuFragment.this.getDialog();

		ListView listview = dialog.getListView();
//		ListAdapter adapter = listview.getAdapter();

		if (mMessage != null && mMessage.isShareable() && !mMessage.getFrom().equals(IdentityController.getLoggedInUser())
				&& mMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
			listview.getChildAt(0).setEnabled(mMessage.isShareable());
		}
		else {
			listview.getChildAt(0).setEnabled(false);
		}

		if (mMessage != null && mMessage.getId() != null && mMessage.getFrom().equals(IdentityController.getLoggedInUser())
				&& mMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {

			listview.getChildAt(1).setEnabled(true);
		}
		else {
			listview.getChildAt(1).setEnabled(false);
		}
		//listview.getChildAt(2).setVisibility(View.VISIBLE);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		mMenuItemArray = new String[] { "save to gallery", mMessage.isShareable() ? "lock" : "unlock", "delete" };

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (mMessage == null)
					return;

				switch (which) {
				case 0:
					// Utils.makeToast(mActivity, "saving image in gallery");
					new AsyncTask<Void, Void, Boolean>() {

						@Override
						protected Boolean doInBackground(Void... params) {
							try {

								File galleryFile = FileUtils.createGalleryImageFile(".jpg");
								FileOutputStream fos = new FileOutputStream(galleryFile);
								InputStream imageStream = MainActivity.getNetworkController().getFileStream(mActivity, mMessage.getData());

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
					break;
				case 1:
					getMainActivity().getChatController().toggleMessageShareable(mMessage);
					break;
				case 2:
					getMainActivity().getChatController().deleteMessage(mMessage);
					break;
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
		return dialog;
	}

	@Override
	public void onResume() {
		super.onResume();

		mMessage.addObserver(mMessageObserver);
	}

	@Override
	public void onPause() {
		super.onPause();
		mMessage.deleteObservers();
	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

}
