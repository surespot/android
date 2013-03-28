package com.twofours.surespot.friends;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.activities.MainActivity;

public class FriendMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "FriendMenuFragment";
	private Friend mFriend;
	private MainActivity mActivity;
	private String[] mMenuItemArray;
	

	public void setActivityAndMessage(MainActivity activity, Friend friend) {
		mFriend = friend;
		mActivity = activity;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		mMenuItemArray = null;

		mMenuItemArray = new String[1];
		mMenuItemArray[0] = "delete all messages";
		//mMenuItemArray[1] = "delete friend";

		builder.setItems(mMenuItemArray, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogi, int which) {
				if (mFriend == null)
					return;

				
				switch (which) {
				case 0:
					mActivity.getChatController().deleteMessages(mFriend);
				case 1:
				}
			}
		});

		AlertDialog dialog = builder.create();	
		return dialog;
	}

}
