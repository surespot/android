package com.twofours.surespot.fragments;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.Utils;
import com.twofours.surespot.layout.ChatArrayAdapter;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatFragment extends SherlockFragment {

	private ChatArrayAdapter chatAdapter;
	private static final String TAG = "ChatFragment";
	private String mUsername;
	private ListView mListView;

	public String getUsername() {
		return mUsername;
	}

	public void setUsername(String mUsername) {
		this.mUsername = mUsername;
	}

	public ChatFragment(String username) {
		setUsername(username);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		// listView.setEmptyView(view.findViewById(R.id.friend_list_empty));
		
		//make sure the public key is there
		SurespotApplication.getEncryptionController().hydratePublicKey(mUsername, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {

				// get the list of friends
				SurespotApplication.getNetworkController().getMessages(mUsername, new IAsyncCallback<List<JSONObject>>() {

					@Override
					public void handleResponse(List<JSONObject> result) {
						if (result == null) {
							return;
						}
						
						chatAdapter = new ChatArrayAdapter(getActivity(), result);
						mListView.setAdapter(chatAdapter);

					}
				});
				
			}
		});


		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				final String message = ((EditText) view.findViewById(R.id.etMessage)).getText().toString();

				if (message.length() > 0) {
					// TODO encrypt and send it
					SurespotApplication.getEncryptionController().eccEncrypt(mUsername, message,
							new IAsyncCallback<String>() {

								@Override
								public void handleResponse(String result) {
									SurespotApplication.getChatController().sendMessage(mUsername, result);

								}
							});
				}

			}
		});

		return view;
	}

	private void ensureChatAdapter() {
		if (chatAdapter == null) {
			chatAdapter = new ChatArrayAdapter(getActivity(), new ArrayList<JSONObject>());
			mListView.setAdapter(chatAdapter);
		}
	}

	public void addMessage(final JSONObject message) {
		ensureChatAdapter();
		chatAdapter.add(message);		
	}
}
