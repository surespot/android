package com.twofours.surespot.chat;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class TextMessageMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "TextMessageMenuFragment";
	private SurespotMessage mMessage;
	private String[] mMenuItemArray;

	public static SherlockDialogFragment newInstance(SurespotMessage message) {
		TextMessageMenuFragment f = new TextMessageMenuFragment();

		Bundle args = new Bundle();
		args.putString("message", message.toJSONObject().toString());

		// plain text is not converted to json string so store it separately
		if (message.getPlainData() != null) {
			args.putString("messageText", message.getPlainData().toString());
		}
		f.setArguments(args);

		return f;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		final MainActivity mActivity = (MainActivity) getActivity();

		String messageString = getArguments().getString("message");
		if (messageString != null) {
			mMessage = SurespotMessage.toSurespotMessage(messageString);
		}

		String messageText = getArguments().getString("messageText");
		if (messageText == null) {
			messageText = "";
		}
		
		final String finalMessageText = messageText;
			

		mMenuItemArray = new String[2];
		mMenuItemArray[0] = getString(R.string.menu_copy);
		mMenuItemArray[1] = getString(R.string.menu_delete_message);

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			@SuppressWarnings("deprecation")
			@SuppressLint("NewApi")
			public void onClick(final DialogInterface dialogi, int which) {
				if (mMessage == null) {
					return;
				}

				if (getActivity() == null) {
					return;
				}

				switch (which) {
				case 0:
					if (finalMessageText != null) {
						int sdk = android.os.Build.VERSION.SDK_INT;
						if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
							android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
							clipboard.setText(new SpannableString(finalMessageText));
						}
						else {
							android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(
									Context.CLIPBOARD_SERVICE);
							android.content.ClipData clip = android.content.ClipData.newPlainText("surespot text", finalMessageText);
							clipboard.setPrimaryClip(clip);
						}
					}
					break;
				case 1:
					SharedPreferences sp = getActivity().getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_message", true);
					if (confirm) {
						AlertDialog dialog = UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
								getString(R.string.delete_message), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
									public void handleResponse(Boolean result) {
										if (result) {
											mActivity.getChatController().deleteMessage(mMessage);
										}
										else {
											dialogi.cancel();
										}
									};
								});
						mActivity.setChildDialog(dialog);
					}

					else {
						mActivity.getChatController().deleteMessage(mMessage);
					}
					break;

				}

			}
		});

		AlertDialog dialog = builder.create();
		return dialog;
	}

}
