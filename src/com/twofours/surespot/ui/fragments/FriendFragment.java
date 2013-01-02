package com.twofours.surespot.ui.fragments;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.network.NetworkController;

public class FriendFragment extends SherlockFragment {

	private ArrayAdapter<String> friendAdapter;
	private static final String TAG = "FriendFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.v(TAG, "onCreateView");
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);
		final ListView listView = (ListView) view.findViewById(R.id.friend_list);
		listView.setEmptyView(view.findViewById(R.id.friend_list_empty));

		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// send show chat event
				Intent intent = new Intent(SurespotConstants.EventFilters.SHOW_CHAT_EVENT);
				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, friendAdapter.getItem(position));
				LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

			}
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				addFriend();
			}
		});

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				ensureFriendAdapter();

				friendAdapter.add(intent.getStringExtra(SurespotConstants.ExtraNames.FRIEND_ADDED));

			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_ADDED_EVENT));

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String friendname = intent.getExtras().getString(SurespotConstants.ExtraNames.FRIEND_INVITE_NAME);
				String action = intent.getExtras().getString(SurespotConstants.ExtraNames.FRIEND_INVITE_ACTION);
				// TODO show pending and delete when ignored?
				if (action.equals("accept")) {
					ensureFriendAdapter();

					friendAdapter.add(friendname);
				}

			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_INVITE_EVENT));

		EditText editText = (EditText) view.findViewById(R.id.etFriend);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					addFriend();
					handled = true;
				}
				return handled;
			}

		});

		return view;

	}

	@Override
	public void onStart() {

		super.onStart();

		// get the list of friends
		NetworkController.getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {

				List<String> friends = new ArrayList<String>();
				if (jsonArray.length() > 0) {
					try {
						for (int i = 0; i < jsonArray.length(); i++) {
							friends.add(jsonArray.getString(i));
						}
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				Log.v(TAG, "friends: " + friends);
				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, friends);
				ListView listView = (ListView) getView().findViewById(R.id.friend_list);
				listView.setAdapter(friendAdapter);

			}
		});

	}

	private void addFriend() {

		final EditText etFriend = ((EditText) getView().findViewById(R.id.etFriend));
		String friend = etFriend.getText().toString();

		if (friend.length() > 0 && !friend.equals(SurespotApplication.getUserData().getUsername())) {

			NetworkController.invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(String arg0) { // TODO indicate in the UI that the request is pending somehow
					TextKeyListener.clear(etFriend.getText());

				}
			});
		}

	}

	private void ensureFriendAdapter() {
		if (friendAdapter == null) {
			friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
					new ArrayList<String>());
			ListView listView = (ListView) getView().findViewById(R.id.friend_list);
			listView.setAdapter(friendAdapter);
		}
	}
}
