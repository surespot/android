package com.twofours.surespot.friends;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.ui.UIUtils;

public class FriendMenuFragment extends SherlockDialogFragment {
	protected static final String TAG = "FriendMenuFragment";
	private Friend mFriend;
	private ArrayList<String> mItems;
	private IAsyncCallbackTriplet<DialogInterface, Friend, String> mSelectionCallback;

	public void setActivityAndFriend(Friend friend, IAsyncCallbackTriplet<DialogInterface, Friend, String> selectionCallback) {
		mFriend = friend;
		mSelectionCallback = selectionCallback;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// builder.setTitle(R.string.pick_color);

		if (savedInstanceState != null) {
			try {
				String sFriend = savedInstanceState.getString("friend");
				if (sFriend != null) {
					mFriend = Friend.toFriend(new JSONObject(sFriend));
				}
			}
			catch (JSONException e) {
				SurespotLog.e(TAG, e, "could not create friend from saved instance state");
				return null;
			}
		}

		if (mFriend == null) {
			SurespotLog.w(TAG, "there is no friend assigned");
			return null;
		}

		mItems = new ArrayList<String>(5);

		if (mFriend.isFriend()) {
			if (mFriend.isChatActive()) {
				mItems.add(getString(R.string.menu_close_tab));
			}

			mItems.add(getString(R.string.menu_delete_all_messages));
			if (!mFriend.isDeleted()) {
				mItems.add(getString(R.string.verify_key_fingerprints));

				// if we have image assigned, show remove instead
				if (mFriend.hasFriendImageAssigned()) {
					mItems.add(getString(R.string.menu_remove_friend_image));
				}
				else {
					mItems.add(getString(R.string.menu_assign_image));
				}

				if (mFriend.hasFriendAliasAssigned()) {
					mItems.add(getString(R.string.menu_remove_friend_alias));
				}
				else {
					mItems.add(getString(R.string.menu_assign_alias));
				}

			}
		}
		if (!mFriend.isInviter()) {
			mItems.add(getString(R.string.menu_delete_friend));
		}

		builder.setItems(mItems.toArray(new String[mItems.size()]), new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogi, int which) {
				if (mFriend == null || mSelectionCallback == null)
					return;

				String itemText = mItems.get(which);
				mSelectionCallback.handleResponse(dialogi, mFriend, itemText);
			}
		});
		
		builder.setTitle(UIUtils.buildAliasString(mFriend.getName(), mFriend.getAliasPlain()));

		AlertDialog dialog = builder.create();
		return dialog;
	}

	@Override
	public void onSaveInstanceState(Bundle arg0) {
		super.onSaveInstanceState(arg0);
		arg0.putString("friend", mFriend.toJSONObject().toString());
	}

}
