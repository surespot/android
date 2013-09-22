package com.twofours.surespot.chat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;
import com.twofours.surespot.voice.VoiceMessageMenuFragment;

public class TextMessageMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "TextMessageMenuFragment";
	private SurespotMessage mMessage;
	private String[] mMenuItemArray;

	public static SherlockDialogFragment newInstance(SurespotMessage message) {
		VoiceMessageMenuFragment f = new VoiceMessageMenuFragment();

		Bundle args = new Bundle();
		args.putString("message", message.toJSONObject().toString());
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

		
		mMenuItemArray = new String[1];
		mMenuItemArray[0] = getString(R.string.menu_delete_message);

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mMessage == null) {
					return;
				}

				SharedPreferences sp = getActivity().getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
				boolean confirm = sp.getBoolean("pref_delete_message", true);
				if (confirm) {
					UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
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
