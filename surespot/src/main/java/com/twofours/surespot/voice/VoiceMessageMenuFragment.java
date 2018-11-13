package com.twofours.surespot.voice;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.UIUtils;

import java.util.ArrayList;

public class VoiceMessageMenuFragment extends DialogFragment {
    protected static final String TAG = "VoiceMessageMenuFragment";
    private SurespotMessage mMessage;
    private String mUsername;
    private ArrayList<String> mItems;
//	private BillingController mBillingController;

    public static DialogFragment newInstance(String username, SurespotMessage message) {
        VoiceMessageMenuFragment f = new VoiceMessageMenuFragment();

        Bundle args = new Bundle();
        args.putString("message", message.toJSONObject(false).toString());
        args.putString("username", username);
        f.setArguments(args);

        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        String messageString = getArguments().getString("message");
        if (messageString != null) {
            mMessage = SurespotMessage.toSurespotMessage(messageString);
        }

        String username = getArguments().getString("username");
        if (username != null) {
            mUsername = username;
        }

        final MainActivity mActivity = (MainActivity) getActivity();

        //	mBillingController = SurespotApplication.getBillingController();

        mItems = new ArrayList<String>(2);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // builder.setTitle(R.string.pick_color);

        // nag nag nag
//		if (!mBillingController.hasVoiceMessaging()) {
//			mItems.add(getString(R.string.menu_purchase_voice_messaging));
//		}

        // can always delete
        mItems.add(getString(R.string.menu_delete_message));

        builder.setItems(mItems.toArray(new String[mItems.size()]), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialogi, int which) {
                if (mMessage == null)
                    return;

                String itemText = mItems.get(which);

                if (itemText.equals(getString(R.string.menu_delete_message))) {
                    SharedPreferences sp = mActivity.getSharedPreferences(mUsername, Context.MODE_PRIVATE);
                    boolean confirm = sp.getBoolean("pref_delete_message", true);
                    final ChatController cc = ChatManager.getChatController(mUsername);
                    if (cc != null) {
                        if (confirm) {
                            AlertDialog dialog = UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
                                    getString(R.string.delete_message), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                        public void handleResponse(Boolean result) {
                                            if (result) {
                                                cc.deleteMessage(mMessage, true);
                                            } else {
                                                dialogi.cancel();
                                            }
                                        }

                                        ;
                                    });
                            mActivity.setChildDialog(dialog);
                        } else {
                            cc.deleteMessage(mMessage, true);
                        }
                    } else {
                        dialogi.cancel();
                    }

                    return;
                }

//				if (itemText.equals(getString(R.string.menu_purchase_voice_messaging))) {
//					mActivity.showVoicePurchaseDialog(false);
//					return;
//				}

            }
        });

        AlertDialog dialog = builder.create();
        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.setButtonText();
        }
    }
}
