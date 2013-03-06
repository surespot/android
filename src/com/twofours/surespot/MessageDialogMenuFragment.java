package com.twofours.surespot;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;

public class MessageDialogMenuFragment extends SherlockDialogFragment {
	private SurespotMessage mMessage;	
	
	public void setMessage(SurespotMessage message) {
		mMessage = message;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		builder.setItems(new String[] { "delete message" }, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (mMessage == null) return;
				switch (which) {
				case 0:
					// make sure it's our message
					if (mMessage.getFrom().equals(IdentityController.getLoggedInUser())) {
						getMainActivity().getChatController().deleteMessage(mMessage);
					}
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
