package com.twofours.surespot.chat;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.UIUtils;

public class TextMessageMenuFragment extends DialogFragment {
    protected static final String TAG = "TextMessageMenuFragment";
    private SurespotMessage mMessage;
    private String mUsername;
    private String[] mMenuItemArray;

    public static DialogFragment newInstance(String username, SurespotMessage message) {
        TextMessageMenuFragment f = new TextMessageMenuFragment();

        Bundle args = new Bundle();
        args.putString("message", message.toJSONObject(false).toString());

        // plain text is not converted to json string so store it separately
        if (message.getPlainData() != null) {
            args.putString("messageText", message.getPlainData().toString());
        }
        args.putString("username", username);

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

        String username = getArguments().getString("username");
        if (username != null) {
            mUsername = username;
        }

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
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(
                                    Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("surespot text", finalMessageText);
                            clipboard.setPrimaryClip(clip);

                        }
                        break;
                    case 1:

                        final ChatController cc = ChatManager.getChatController(mUsername);
                        if (cc != null) {
                            SharedPreferences sp = getActivity().getSharedPreferences(mUsername, Context.MODE_PRIVATE);
                            boolean confirm = sp.getBoolean("pref_delete_message", true);

                            if (confirm) {
                                AlertDialog dialog = UIUtils.createAndShowConfirmationDialog(mActivity, getString(R.string.delete_message_confirmation_title),
                                        getString(R.string.delete_message), getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                            public void handleResponse(Boolean result) {
                                                if (result) {
                                                    cc.deleteMessage(mMessage, true);
                                                }
                                                else {
                                                    dialogi.cancel();
                                                }
                                            }

                                            ;
                                        });
                                mActivity.setChildDialog(dialog);
                            }
                            else {
                                cc.deleteMessage(mMessage, true);
                            }
                        }
                        else {
                            dialogi.cancel();
                        }
                        break;
                }
            }
        });

        AlertDialog dialog = builder.create();
        return dialog;
    }

}
