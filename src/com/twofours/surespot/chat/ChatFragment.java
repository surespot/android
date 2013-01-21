package com.twofours.surespot.chat;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatFragment extends SherlockFragment {
	private ChatAdapter mChatAdapter;
	private String mUsername;
	private ListView mListView;
	private static final String TAG = "ChatFragment";
	// private String mLastMessageId;
	private EditText mEditText;
	private BroadcastReceiver mSocketConnectionStatusReceiver;

	public String getUsername() {
		if (mUsername == null) {
			mUsername = getArguments().getString("username");
		}
		return mUsername;
	}

	public void setUsername(String mUsername) {
		this.mUsername = mUsername;
	}

	public static ChatFragment newInstance(String username) {
		ChatFragment cf = new ChatFragment();
		Bundle bundle = new Bundle();
		bundle.putString("username", username);
		cf.setArguments(bundle);
		return cf;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.chat_fragment, container, false);

		mListView = (ListView) view.findViewById(R.id.message_list);
		ensureChatAdapter();

		setUsername(getArguments().getString("username"));

		// load messages from local storage
		loadMessages();

		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage();
			}
		});
		mEditText = (EditText) view.findViewById(R.id.etMessage);
		mEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					//
					sendMessage();
					handled = true;
				}
				return handled;
			}
		});

		// if the connection status changed we need to reload any messages we missed, without showing a progress dialog
		// (sshh)
		mSocketConnectionStatusReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				boolean connected = intent.getBooleanExtra(SurespotConstants.ExtraNames.CONNECTED, false);
				if (connected) {
					getLatestMessages(null);
				}
			}
		};

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		// reget the messages in case any were added while we were gone
		Log.v(TAG, "onResume, mUsername:  " + mUsername);

		LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(mSocketConnectionStatusReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.SOCKET_CONNECTION_STATUS_CHANGED));

		// make sure the public key is there
		// TODO move this into network controller

		// if the socket is connected, load new messages
		// if it's not connected we'll load them when it connects
		ChatActivity chatActivity = (ChatActivity) getActivity();

		if (chatActivity.chatConnected()) {
			if (this.isVisible()) {
				chatActivity.startLoadingMessagesProgress();
			}

			getLatestMessages(new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(Boolean result) {
					// TODO Auto-generated method stub

					if (ChatFragment.this.isVisible()) {
						((ChatActivity) getActivity()).stopLoadingMessagesProgress();
					}
					mListView.setEmptyView(getView().findViewById(R.id.message_list_empty));
				}
			});

		}

	}

	@Override
	public void onPause() {

		super.onPause();

		Log.v(TAG, "onPause, mUsername:  " + mUsername);
		// saveMessages();
		LocalBroadcastManager.getInstance(this.getActivity()).unregisterReceiver(mSocketConnectionStatusReceiver);
	}

	@Override
	public void onDestroy() {

		super.onDestroy();
		Log.v(TAG, "onDestroy");
		saveMessages();

	}

	private void getLatestMessages(final IAsyncCallback<Boolean> callback) {

		// get the list of messages
		String lastMessageId = getLastMessageId();

		Log.v(TAG, "Asking server for messages after messageId: " + lastMessageId);
		NetworkController.getMessages(mUsername, lastMessageId, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				// on async http request, response seems to come back
				// after app is destroyed sometimes
				// (ie. on rotation on gingerbread)
				// so check for null here

				if (getActivity() != null) {

					// ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
					ChatMessage message = null;
					try {
						for (int i = 0; i < jsonArray.length(); i++) {
							JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
							message = ChatMessage.toChatMessage(jsonMessage);
							mChatAdapter.addOrUpdateMessage(message, false);
						}
					} catch (JSONException e) {
						Log.e(TAG, "Error creating chat message: " + e.toString());
					}

					Log.v(TAG, "loaded: " + jsonArray.length() + " messages from the server.");
					mChatAdapter.notifyDataSetChanged();
					if (callback != null) {
						callback.handleResponse(true);
					}
					mEditText.requestFocus();

				}
			}

			@Override
			public void onFailure(Throwable error, String content) {
				Log.e(TAG, "getMessages: " + error.getMessage());
				if (callback != null) {
					callback.handleResponse(false);
				}
			}

		});
	}

	public String getLastMessageId() {
		ChatMessage lastMessage = mChatAdapter.getLastMessageWithId();
		String lastMessageId = null;

		if (lastMessage != null) {
			lastMessageId = lastMessage.getId();
		}
		return lastMessageId;
	}

	private void sendMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		if (message.length() > 0) {

			EncryptionController.eccEncrypt(mUsername, message, new IAsyncCallback<String>() {
				@Override
				public void handleResponse(String result) {

					ChatMessage chatMessage = new ChatMessage();
					chatMessage.setFrom(EncryptionController.getIdentityUsername());
					chatMessage.setTo(mUsername);
					chatMessage.setCipherText(result);

					mChatAdapter.addOrUpdateMessage(chatMessage, true);

					((ChatActivity) getActivity()).sendMessage(mUsername, result);
					TextKeyListener.clear(etMessage.getText());
				}
			});
		}
	}

	private void ensureChatAdapter() {
		if (mChatAdapter == null) {
			mChatAdapter = new ChatAdapter(getActivity());
			mListView.setAdapter(mChatAdapter);
		}
	}

	public void addMessage(final JSONObject jsonMessage) {
		ensureChatAdapter();
		try {
			ChatMessage message = ChatMessage.toChatMessage(jsonMessage);
			mChatAdapter.addOrUpdateMessage(message, true);
		} catch (JSONException e) {
			Log.e(TAG, "Error adding chat message.");
		}
	}

	private void saveMessages() {
		Log.v(TAG, "saving messages to shared prefs");
		Utils.putSharedPrefsString("messages_" + mUsername, Utils.chatMessagesToJson(mChatAdapter.getMessages())
				.toString());
	}

	private void loadMessages() {
		ensureChatAdapter();
		String sMessages = Utils.getSharedPrefsString("messages_" + mUsername);
		if (sMessages != null && !sMessages.isEmpty()) {
			ArrayList<ChatMessage> messages = Utils.jsonStringToChatMessages(sMessages);
			Log.v(TAG, "Loaded: " + messages.size() + " messages from local storage.");
			mChatAdapter.addMessages(messages);
			// Utils.putSharedPrefsString("messages_" + mUsername, null);
		} else {
			Log.v(TAG, "Loaded: no messages from local storage.");
		}
	}

}
