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
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatFragment extends SherlockFragment {
	private ChatAdapter mChatAdapter;
	private String mUsername;
	private ListView mListView;
	private static final String TAG = "ChatFragment";
	private String mLastMessageId;
	private EditText mEditText;

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

		return view;
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
		EncryptionController.hydratePublicKey(mUsername, new IAsyncCallback<Boolean>() {
			@Override
			public void handleResponse(Boolean result) {
				if (result) {
					// get the list of messages
					NetworkController.getMessages(mUsername, mLastMessageId, new JsonHttpResponseHandler() {
						@Override
						public void onSuccess(JSONArray jsonArray) {
							// on async http request, response seems to come back
							// after app is destroyed sometimes
							// (ie. on rotation on gingerbread)
							// so check for null here

							if (getActivity() != null) {

								ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
								ChatMessage message = null;
								try {
									for (int i = 0; i < jsonArray.length(); i++) {
										JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
										message = ChatMessage.toChatMessage(jsonMessage);										
										messages.add(message);
									}
								} catch (JSONException e) {
									Log.e(TAG, "Error creating chat message: " + e.toString());
								}

								mChatAdapter.addMessages(messages);
								mListView.setAdapter(mChatAdapter);
								mListView.setEmptyView(getView().findViewById(R.id.message_list_empty));

								if (message != null) {
									mLastMessageId = message.getId();
								}

								mEditText.requestFocus();
							}
						}

						@Override
						public void onFailure(Throwable error, String content) {
							Log.e(TAG, "getMessages: " + error.getMessage());
						}

						@Override
						public void onFinish() {
							if (ChatFragment.this.isVisible()) {
								Log.v(TAG, "Tearing down a progress dialog: " + getUsername());
								((ChatActivity) getActivity()).stopLoadingMessagesProgress();

							}
						}
					});
				} else {
					Log.v(TAG, "couldn't get public key, closing tab:  " + mUsername);
					// can't do anything without a public key so close the tab
					if (ChatFragment.this.isVisible()) {
						((ChatActivity) getActivity()).stopLoadingMessagesProgress();
					}

					((ChatActivity) getActivity()).closeChat(mUsername);
				}
			}
		});

	}

	@Override
	public void onPause() {

		super.onPause();
		Log.v(TAG, "onPause, mUsername:  " + mUsername);
	}

	@Override
	public void onDestroy() {

		super.onDestroy();
		Log.v(TAG, "onDestroy");
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

					mChatAdapter.addOrUpdateMessage(chatMessage);													
					
					ChatController.sendMessage(mUsername, result);
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
			mChatAdapter.addOrUpdateMessage(message);
			mLastMessageId = message.getId();

		} catch (JSONException e) {
			Log.e(TAG, "Error adding chat message.");
		}
	}

}
