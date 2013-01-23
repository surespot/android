package com.twofours.surespot.chat;

import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
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
	private EditText mEditText;
	private BroadcastReceiver mSocketConnectionStatusReceiver;
	private IAsyncCallback<Boolean> mLatestMessageHandler;
	private boolean mLoading;
	private int mPreviousTotal;
	private boolean mNoEarlierMessages = false;

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
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");

		mChatAdapter = new ChatAdapter(getActivity());
		setUsername(getArguments().getString("username"));
		// load messages from local storage
		loadMessages();

		mLatestMessageHandler = new IAsyncCallback<Boolean>() {

			@Override
			public void handleResponse(Boolean result) {
				// TODO Auto-generated method stub

				((ChatActivity) getActivity()).stopLoadingMessagesProgress();
				// mEditText.requestFocus();
				// TODO move "last viewed" logic to ChatActivity
				String lastMessageId = getLatestMessageId();
				if (lastMessageId != null) {
					((ChatActivity) getActivity()).updateLastViewedMessageId(mUsername, Integer.parseInt(lastMessageId));
				}

				mListView.setEmptyView(getView().findViewById(R.id.message_list_empty));
				mChatAdapter.notifyDataSetChanged();
			}
		};

		Log.v(TAG, "onCreate, username: " + mUsername + ", messageCount: " + mChatAdapter.getCount());

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		mListView.setAdapter(mChatAdapter);

		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendTextMessage();
			}
		});
		mEditText = (EditText) view.findViewById(R.id.etMessage);
		mEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					//
					sendTextMessage();
					handled = true;
				}
				return handled;
			}
		});

		Intent intent = getActivity().getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		String intentName = intent.getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);

		// if the intent is meant for this chat
		if (intentName != null && intentName.equals(mUsername)) {

			if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
				// we have a send action so populate the edit box with the data
				handleSendIntent(action, type, intent.getExtras());

				// intent.setAction(null);
				// intent.setType(null);
				// intent.removeExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
			}

		}

		// if the connection status changed we need to reload any messages we missed, without showing a progress dialog
		// (sshh)
		mSocketConnectionStatusReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				boolean connected = intent.getBooleanExtra(SurespotConstants.ExtraNames.CONNECTED, false);
				if (connected) {
					getLatestMessages(mLatestMessageHandler);
				}
			}
		};

		// listen to scroll changes
		mListView.setOnScrollListener(new OnScrollListener() {

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

				// Log.v(TAG,"onScroll, firstVisibleItem: " + firstVisibleItem + ", visibleItemCount: " + visibleItemCount +
				// ", totalItemCount: " + totalItemCount);
				if (mLoading) {
					// will have more items if we loaded them
					if (totalItemCount > mPreviousTotal) {
						mPreviousTotal = totalItemCount;
						mLoading = false;
					}

				}

				if (!mLoading && !mNoEarlierMessages && (firstVisibleItem <= 7)) {
					Log.v(TAG, "onScroll: Loading more messages.");
					mLoading = true;
					getEarlierMessages();

				}

			}

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {

			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		// reget the messages in case any were added while we were gone
		Log.v(TAG, "onResume, mUsername:  " + mUsername);
		Log.v(TAG, "message count: " + mChatAdapter.getCount());

		LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(mSocketConnectionStatusReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.SOCKET_CONNECTION_STATUS_CHANGED));

		// make sure the public key is there
		// TODO move this into network controller

		// if the socket is connected, load new messages
		// if it's not connected we'll load them when it connects
		ChatActivity chatActivity = (ChatActivity) getActivity();

		if (chatActivity.chatConnected()) {
			chatActivity.startLoadingMessagesProgress();
			getLatestMessages(mLatestMessageHandler);
		}

		if (isVisible()) {
			Log.v(TAG, "onResume we are visible");
			requestFocus();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.v(TAG, "onPause, mUsername:  " + mUsername);
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
		String lastMessageId = getLatestMessageId();

		Log.v(TAG, "Asking server for messages after messageId: " + lastMessageId);
		NetworkController.getMessages(mUsername, lastMessageId, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				// on async http request, response seems to come back
				// after app is destroyed sometimes
				// (ie. on rotation on gingerbread)
				// so check for null here

				if (getActivity() != null) {
					SurespotMessage message = null;
					try {
						for (int i = 0; i < jsonArray.length(); i++) {
							JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
							message = SurespotMessage.toSurespotMessage(jsonMessage);
							mChatAdapter.addOrUpdateMessage(message, false);

						}
					} catch (JSONException e) {
						Log.e(TAG, "Error creating chat message: " + e.toString());
					}

					Log.v(TAG, "loaded: " + jsonArray.length() + " messages from the server.");

					if (callback != null) {
						callback.handleResponse(true);
					}
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

	private void getEarlierMessages() {

		mLoading = true;
		// get the list of messages
		String firstMessageId = getEarliestMessageId();

		if (firstMessageId != null) {
			// todo make all the ints #s
			if (Integer.parseInt(firstMessageId) > 1) {
				Log.v(TAG, "Asking server for messages before messageId: " + firstMessageId);
				NetworkController.getEarlierMessages(mUsername, firstMessageId, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here

						if (getActivity() != null) {
							SurespotMessage message = null;
							try {
								for (int i = jsonArray.length() - 1; i >= 0; i--) {
									JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
									message = SurespotMessage.toSurespotMessage(jsonMessage);
									mChatAdapter.insertMessage(message, false);

								}
							} catch (JSONException e) {
								Log.e(TAG, "Error creating chat message: " + e.toString());
							}

							Log.v(TAG, "loaded: " + jsonArray.length() + " messages from the server.");

							mChatAdapter.notifyDataSetChanged();

						}
					}

					@Override
					public void onFailure(Throwable error, String content) {
						Log.e(TAG, "getEarlierMessages: " + error.getMessage());
					}
				});
			} else {
				Log.v(TAG, "getEarlierMessages: no more messages.");
				ChatFragment.this.mNoEarlierMessages = true;
			}
		}
	}

	public String getLatestMessageId() {
		SurespotMessage lastMessage = mChatAdapter.getLastMessageWithId();
		String lastMessageId = null;

		if (lastMessage != null) {
			lastMessageId = lastMessage.getId();
		}
		return lastMessageId;

	}

	public String getEarliestMessageId() {
		SurespotMessage firstMessage = mChatAdapter.getFirstMessageWithId();
		String firstMessageId = null;

		if (firstMessage != null) {
			firstMessageId = firstMessage.getId();
		}
		return firstMessageId;
	}

	private void sendTextMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		sendMessage(message, SurespotConstants.MimeTypes.TEXT);

		// TODO only clear on success
		TextKeyListener.clear(etMessage.getText());
	}

	private void sendMessage(final String plainText, final String mimeType) {

		if (plainText.length() > 0) {

			EncryptionController.symmetricEncrypt(mUsername, plainText, new IAsyncCallback<String[]>() {
				@Override
				public void handleResponse(String[] result) {
					if (result != null) {
						SurespotMessage chatMessage = Utils.buildMessage(mUsername, mimeType, plainText, result[0], result[1]);
						mChatAdapter.addOrUpdateMessage(chatMessage, true);
						((ChatActivity) getActivity()).sendMessage(chatMessage);
					} else {
						// TODO handle encryption error
					}
				}
			});
		}
	}

	public void addMessage(final SurespotMessage message) {
		Log.v(TAG, "addMessage: " + message.getTo());
		mChatAdapter.addOrUpdateMessage(message, true);
	}

	private void saveMessages() {
		// save last 30? messages
		Log.v(TAG, "saving 30 messages to shared prefs");

		ArrayList<SurespotMessage> messages = mChatAdapter.getMessages();
		int messagesSize = messages.size();
		Utils.putSharedPrefsString("messages_" + mUsername,
				Utils.chatMessagesToJson(messagesSize <= 30 ? messages : messages.subList(messagesSize - 30, messagesSize)).toString());
	}

	private void loadMessages() {
		String sMessages = Utils.getSharedPrefsString("messages_" + mUsername);
		if (sMessages != null && !sMessages.isEmpty()) {
			ArrayList<SurespotMessage> messages = Utils.jsonStringToChatMessages(sMessages);
			Log.v(TAG, "Loaded: " + messages.size() + " messages from local storage.");
			mChatAdapter.addMessages(messages);
		} else {
			Log.v(TAG, "Loaded: no messages from local storage.");
		}
	}

	// populate the edit box
	private void handleSendIntent(String action, final String type, Bundle extras) {
		if (action.equals(Intent.ACTION_SEND)) {

			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = extras.getString(Intent.EXTRA_TEXT);
				Log.v(TAG, "received action send, data: " + sharedText);
				mEditText.append(sharedText);
				requestFocus();
			} else if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {
				Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
				Utils.buildPictureMessage(getActivity(), imageUri, mUsername, new IAsyncCallback<SurespotMessage>() {

					@Override
					public void handleResponse(SurespotMessage result) {
						if (result != null) {
							mChatAdapter.addOrUpdateMessage(result, true);
							((ChatActivity) getActivity()).sendMessage(result);
						}

					}
				});
			}
		} else {
			if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
				// TODO implement
			}
		}

	}

	public void scrollToEnd() {
		Log.v(TAG, "scrollFocus");
		mListView.setSelection(mChatAdapter.getCount() - 1);

	}

	public void requestFocus() {
		mEditText.requestFocus();

	}

}
