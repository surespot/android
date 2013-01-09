package com.twofours.surespot.chat;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
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
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatFragment extends SherlockFragment {
	private ChatAdapter chatAdapter;
	private String mUsername;
	private ListView mListView;
	private static final String TAG = "ChatFragment";

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
	public void onResume() {
		super.onResume();

		// TODO use dependency injection with interface
		// if this is the fragment that's showing, start progress
		if (this.isVisible()) {			
			((ChatActivity) getActivity()).startLoadingMessagesProgress();
		}
		// reget the messages in case any were added while we were gone
		Log.v(TAG, "onResume, mUsername:  " + mUsername);
		// make sure the public key is there
		// TODO move this into network controller
		EncryptionController.hydratePublicKey(mUsername, new IAsyncCallback<Void>() {
			@Override
			public void handleResponse(Void result) {
				// get the list of messages
				NetworkController.getMessages(mUsername, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here
						if (getActivity() != null) {
							ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
							try {
								for (int i = 0; i < jsonArray.length(); i++) {
									JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
									messages.add(ChatMessage.toChatMessage(jsonMessage));
								}
							}
							catch (JSONException e) {
								Log.e(TAG, "Error creating chat message: " + e.toString());
							}
							chatAdapter = new ChatAdapter(getActivity());
							chatAdapter.addMessages(messages);
							mListView.setAdapter(chatAdapter);
							mListView.setEmptyView(getView().findViewById(R.id.message_list_empty));

						}
					}

					@Override
					public void onFailure(Throwable error, String content) {
						Log.e(TAG, content);
					}

					@Override
					public void onFinish() {
						if (ChatFragment.this.isVisible()) {
							Log.v(TAG, "Tearing down a progress dialog: " + getUsername());
							((ChatActivity) getActivity()).stopLoadingMessagesProgress();
						}
					}
				});
			}
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.chat_fragment, container, false);

		mListView = (ListView) view.findViewById(R.id.message_list);
		setUsername(getArguments().getString("username"));
		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage();
			}
		});
		EditText editText = (EditText) view.findViewById(R.id.etMessage);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
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
		return view;
	}

	private void sendMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		if (message.length() > 0) {
			EncryptionController.eccEncrypt(mUsername, message, new IAsyncCallback<String>() {
				@Override
				public void handleResponse(String result) {
					ChatController.sendMessage(mUsername, result);
					TextKeyListener.clear(etMessage.getText());
				}
			});
		}
	}

	private void ensureChatAdapter() {
		if (chatAdapter == null) {
			chatAdapter = new ChatAdapter(getActivity());
			mListView.setAdapter(chatAdapter);
		}
	}

	public void addMessage(final JSONObject message) {
		ensureChatAdapter();
		try {
			chatAdapter.addMessage(ChatMessage.toChatMessage(message));
		}
		catch (JSONException e) {
			Log.e(TAG, "Error adding chat message.");
		}
	}

	@Override
	public void onPause() {

		super.onPause();
		Log.v(TAG, "onPause, mUsername:  " + mUsername);
	}
}
