package com.twofours.surespot.voice;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class VoiceMessageMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "VoiceMessageMenuFragment";
	private SurespotMessage mMessage;
	private MainActivity mActivity;
	private ArrayList<String> mItems;
	private BillingController mBillingController;

	public void setActivityAndMessage(MainActivity activity, SurespotMessage message) {
		mMessage = message;
		mActivity = activity;
		mBillingController = SurespotApplication.getBillingController();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		if (mMessage == null) {
			return null;
		}

		mItems = new ArrayList<String>(2);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);
		
		//nag nag nag
		if (!mBillingController.hasVoiceMessaging()) {
			mItems.add(getString(R.string.menu_purchase_voice_messaging));
		}
		

		// if we have an errored image we can resend it
		if (mMessage.getFrom().equals(IdentityController.getLoggedInUser()) && mMessage.getErrorStatus() > 0) {
			mItems.add(getString(R.string.menu_resend_message));
		}
			
		// can always delete
		mItems.add(getString(R.string.menu_delete_message));

		builder.setItems(mItems.toArray(new String[mItems.size()]), new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mMessage == null)
					return;

				String itemText = mItems.get(which);
			

				if (itemText.equals(getString(R.string.menu_delete_message))) {
					SharedPreferences sp = mActivity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
					boolean confirm = sp.getBoolean("pref_delete_message", true);
					if (confirm) {
						UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
								getString(R.string.delete_message), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
									public void handleResponse(Boolean result) {
										if (result) {
											mActivity.getChatController().deleteMessage(mMessage);
										} else {
											dialogi.cancel();
										}
									};
								});
					} else {
						mActivity.getChatController().deleteMessage(mMessage);
					}

					return;
				}

				if (itemText.equals(getString(R.string.menu_resend_message))) {
					mActivity.getChatController().resendFileMessage(mMessage);
					return;
				}
				
				if (itemText.equals(getString(R.string.menu_purchase_voice_messaging))) {
					mActivity.showVoicePurchaseDialog(false);
					return;				
				}

			}
		});

		AlertDialog dialog = builder.create();
		return dialog;
	}

}
