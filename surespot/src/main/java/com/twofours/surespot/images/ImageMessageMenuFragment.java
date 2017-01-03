package com.twofours.surespot.images;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.utils.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.UIUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;

public class ImageMessageMenuFragment extends DialogFragment {
	protected static final String TAG = "ImageMessageMenuFragment";
	private SurespotMessage mMessage;
	private String mUsername;
	private ArrayList<String> mItems;
	private Observer mMessageObserver;
	
	public static DialogFragment newInstance(String username, SurespotMessage message) {
		ImageMessageMenuFragment f = new ImageMessageMenuFragment();

		Bundle args = new Bundle();
		args.putString("message", message.toJSONObject(false).toString());
		args.putString("username", username);
		f.setArguments(args);

		return f;
	}

	private void setButtonVisibility() {
		AlertDialog dialog = (AlertDialog) ImageMessageMenuFragment.this.getDialog();

		if (dialog != null) {
			ListView listview = dialog.getListView();

			ListIterator<String> li = mItems.listIterator();
			while (li.hasNext()) {
				String item = li.next();

				if (item.equals(getString(R.string.menu_save_to_gallery))) {
					listview.getChildAt(li.previousIndex()).setEnabled(mMessage.isShareable() && FileUtils.isExternalStorageMounted());
					return;
				}
			}
		}
		else {
			mMessage.deleteObserver(mMessageObserver);
			mMessage = null;
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final MainActivity mActivity = (MainActivity) getActivity();

		String username = getArguments().getString("username");
		if (username != null) {
			mUsername = username;
		}

		final ChatController cc =  ChatManager.getChatController(mUsername);
		if (cc == null) {
			return null;
		}

		String messageString = getArguments().getString("message");
		if (messageString != null) {
			SurespotMessage rebuiltMessage = SurespotMessage.toSurespotMessage(messageString);

			// get the actual message instance to add a listener to
			mMessage = cc.getLiveMessage(rebuiltMessage);

			if (mMessage == null) {
				mMessage = rebuiltMessage;
			}
		}

		mItems = new ArrayList<String>(5);
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		// if it's not our message we can save it to gallery
		if (!mMessage.getFrom().equals(mUsername)) {
			mItems.add(getString(R.string.menu_save_to_gallery));

		}
		// if it's our message and it's been sent we can mark it locked or unlocked
		if (mMessage.getId() != null && mMessage.getFrom().equals(mUsername)) {
			mItems.add(mMessage.isShareable() ? getString(R.string.menu_lock) : getString(R.string.menu_unlock));
		}

		// can always delete
		mItems.add(getString(R.string.menu_delete_message));

		builder.setItems(mItems.toArray(new String[mItems.size()]), new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mMessage == null)
					return;

				mMessage.deleteObservers();

				AlertDialog dialog = (AlertDialog) ImageMessageMenuFragment.this.getDialog();
				ListView listview = dialog.getListView();

				if (!listview.getChildAt(which).isEnabled()) {
					return;
				}

				String itemText = mItems.get(which);

				if (itemText.equals(getString(R.string.menu_lock)) || itemText.equals(getString(R.string.menu_unlock))) {
					cc.toggleMessageShareable(mMessage.getTo(), mMessage.getIv());
					return;
				}

				if (itemText.equals(getString(R.string.menu_save_to_gallery))) {

					// Utils.makeToast(mActivity, "saving image in gallery");
					new AsyncTask<Void, Void, Boolean>() {

						@Override
						protected Boolean doInBackground(Void... params) {
							try {
								if (!mMessage.getDeleted()) {
									File galleryFile = FileUtils.createGalleryImageFile(".jpg");
									FileOutputStream fos = new FileOutputStream(galleryFile);

									InputStream imageStream = NetworkManager.getNetworkController(getActivity(), mUsername).getFileStream(mMessage.getData());

									EncryptionController.runDecryptTask(mUsername, mMessage.getOurVersion(mUsername), mMessage.getOtherUser(mUsername), mMessage.getTheirVersion(mUsername),
											mMessage.getIv(), mMessage.isHashed(), new BufferedInputStream(imageStream), fos);

									FileUtils.galleryAddPic(mActivity, galleryFile.getAbsolutePath());
									return true;
								}
								else {
									return false;
								}
							}

							catch (IOException e) {
								SurespotLog.w(TAG, e, "onCreateDialog");

							}
							return false;
						}

						protected void onPostExecute(Boolean result) {
							if (mActivity != null) {
								if (result) {

									Utils.makeToast(mActivity, mActivity.getString(R.string.image_saved_to_gallery));
								}
								else {
									Utils.makeToast(mActivity, mActivity.getString(R.string.error_saving_image_to_gallery));
								}
							}
						};
					}.execute();
					return;

				}

				if (itemText.equals(getString(R.string.menu_delete_message))) {
					SharedPreferences sp = mActivity.getSharedPreferences(mUsername, Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_message", true);
					if (confirm) {
						AlertDialog adialog = UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
								getString(R.string.delete_message), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
									public void handleResponse(Boolean result) {
										if (result) {
											cc.deleteMessage(mMessage);
										}
										else {
											dialogi.cancel();
										}
									};
								});
						mActivity.setChildDialog(adialog);					}
					else {
						cc.deleteMessage(mMessage);
					}

					return;
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

		// // TODO listen to message control events and handle delete as well
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
