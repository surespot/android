package com.twofours.surespot.ui.fragments;

import java.util.ArrayList;
import java.util.List;

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
import android.widget.TextView.OnEditorActionListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.network.IAsyncCallback;

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
		SurespotApplication.getNetworkController().getFriends(new IAsyncCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}

				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result);
				ListView listView = (ListView) getView().findViewById(R.id.friend_list);
				listView.setAdapter(friendAdapter);

			}
		});

	}

	private void addFriend() {

		final EditText etFriend = ((EditText) getView().findViewById(R.id.etFriend));
		String friend = etFriend.getText().toString();

		if (friend.length() > 0 && !friend.equals(SurespotApplication.getUserData().getUsername())) {

			SurespotApplication.getNetworkController().invite(friend, new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(Boolean result) {
					if (result) {
						// TODO indicate in the UI that the request is pending somehow
						TextKeyListener.clear(etFriend.getText());
					}
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
