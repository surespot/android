package com.twofours.surespot.friends;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.utils.UIUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class FriendMenuFragment extends DialogFragment {
    protected static final String TAG = "FriendMenuFragment";
    private Friend mFriend;
    private ArrayList<String> mMenuItems;
    private ArrayList<Integer> mIdItems;
    private IAsyncCallbackTriplet<DialogInterface, Friend, Integer> mSelectionCallback;

    public void setActivityAndFriend(Friend friend, IAsyncCallbackTriplet<DialogInterface, Friend, Integer> selectionCallback) {
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

        mMenuItems = new ArrayList<String>(6);
        mIdItems = new ArrayList<Integer>(6);

        if (mFriend.isFriend()) {
            if (!mFriend.isDeleted()) {
                if (mFriend.isMuted()) {
                    mMenuItems.add(getString(R.string.unmute));
                    mIdItems.add(R.string.unmute);
                }
                else {
                    mMenuItems.add(getString(R.string.mute));
                    mIdItems.add(R.string.mute);
                }
            }

            if (mFriend.isChatActive()) {
				mMenuItems.add(getString(R.string.menu_close_tab));
                mIdItems.add(R.string.menu_close_tab);
            }

			mMenuItems.add(getString(R.string.menu_delete_all_messages));
            mIdItems.add(R.string.menu_delete_all_messages);

            if (!mFriend.isDeleted()) {
				mMenuItems.add(getString(R.string.verify_key_fingerprints));
                mIdItems.add(R.string.verify_key_fingerprints);

                // if we have image assigned, show remove instead
                if (mFriend.hasFriendImageAssigned()) {
					mMenuItems.add(getString(R.string.menu_remove_friend_image));
                    mIdItems.add(R.string.menu_remove_friend_image);
                }
                else {
					mMenuItems.add(getString(R.string.menu_assign_image));
                    mIdItems.add(R.string.menu_assign_image);
                }

                if (mFriend.hasFriendAliasAssigned()) {
					mMenuItems.add(getString(R.string.menu_remove_friend_alias));
                    mIdItems.add(R.string.menu_remove_friend_alias);
                }
                else {
					mMenuItems.add(getString(R.string.menu_assign_alias));
                    mIdItems.add(R.string.menu_assign_alias);
                }
            }
        }
        if (!mFriend.isInviter()) {
			mMenuItems.add(getString(R.string.menu_delete_friend));
            mIdItems.add(R.string.menu_delete_friend);
        }

        builder.setItems(mMenuItems.toArray(new String[mMenuItems.size()]), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialogi, int which) {
                if (mFriend == null || mSelectionCallback == null)
                    return;

                int id = mIdItems.get(which);
                mSelectionCallback.handleResponse(dialogi, mFriend, id);
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
