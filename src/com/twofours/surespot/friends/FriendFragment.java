package com.twofours.surespot.friends;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.network.NetworkController;

public class FriendFragment extends SherlockFragment {
	private FriendAdapter mFriendAdapter;
	private static final String TAG = "FriendFragment";
	private boolean mDisconnectSocket = true;
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.v(TAG, "onCreateView");
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);
		final ListView listView = (ListView) view.findViewById(R.id.friend_list);
		mFriendAdapter = new FriendAdapter(getActivity());
		listView.setAdapter(mFriendAdapter);
		listView.setEmptyView(view.findViewById(R.id.friend_list_empty));
		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// start chat activity

				// don't disconnect the socket io
				mDisconnectSocket = false;

				Intent intent = new Intent(getActivity(),ChatActivity.class);
				
				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, ((Friend) mFriendAdapter.getItem(position)).getName());
				getActivity().startActivity(intent);
				LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

			}
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		// register for friend aded
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mFriendAdapter.addFriend(intent.getStringExtra(SurespotConstants.ExtraNames.FRIEND_ADDED), Friend.NEW_FRIEND);
			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_ADDED_EVENT));

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {

				mFriendAdapter.addFriendInvite(intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION));

			}
		}, new IntentFilter(SurespotConstants.EventFilters.NOTIFICATION_EVENT));

		EditText editText = (EditText) view.findViewById(R.id.etFriend);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					inviteFriend();
					handled = true;
				}
				return handled;
			}
		});
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		// get the list of notifications
		NetworkController.getNotifications(new JsonHttpResponseHandler() {
			public void onSuccess(JSONArray jsonArray) {
				if (getActivity() != null) {

					for (int i = 0; i < jsonArray.length(); i++) {
						try {
							JSONObject json = jsonArray.getJSONObject(i);
							mFriendAdapter.addFriendInvite(json.getString("data"));
						}
						catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

				}
			}
			@Override
			public void onFailure(Throwable arg0) {
				Toast.makeText(FriendFragment.this.getActivity(), "Error getting notifications.", Toast.LENGTH_SHORT).show();
			}
		});
		// get the list of friends
		NetworkController.getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				if (getActivity() != null) {

					if (jsonArray.length() > 0) {
						ArrayList<String> friends = null;
						try {
							friends = new ArrayList<String>(jsonArray.length());
							for (int i = 0; i < jsonArray.length(); i++) {
								friends.add(jsonArray.getString(i));
							}
						}
						catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						mFriendAdapter.clearFriends(false);
						mFriendAdapter.addFriends(friends, Friend.NEW_FRIEND);
					}
					//

				}
			}

			@Override
			public void onFailure(Throwable arg0) {
				Toast.makeText(FriendFragment.this.getActivity(), "Error getting friends.", Toast.LENGTH_SHORT).show();
			}
		});

	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) getView().findViewById(R.id.etFriend));
		String friend = etFriend.getText().toString();
		if (friend.length() > 0 && !friend.equals(SurespotApplication.getUserData().getUsername())) {
			NetworkController.invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(String arg0) { // TODO indicate in the UI
														// that the request is
														// pending somehow
					TextKeyListener.clear(etFriend.getText());
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					Toast.makeText(FriendFragment.this.getActivity(), content, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

}
