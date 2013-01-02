package com.twofours.surespot.ui.fragments;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import android.os.Bundle;
import android.text.method.TextKeyListener;
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
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.adapters.ChatArrayAdapter;

public class ChatFragment extends SherlockFragment {

	private ChatArrayAdapter chatAdapter;
	// private static final String TAG = "ChatFragment";
	private String mUsername;
	private ListView mListView;

	public String getUsername() {
		if (mUsername == null) {
			mUsername = getArguments().getString("username");
		}
		return mUsername;
	}

	public void setUsername(String mUsername) {
		this.mUsername = mUsername;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		mListView.setEmptyView(view.findViewById(R.id.message_list_empty));

		setUsername(getArguments().getString("username"));

		// make sure the public key is there
		SurespotApplication.getEncryptionController().hydratePublicKey(mUsername, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {

				// get the list of friends
				SurespotApplication.getNetworkController().getMessages(mUsername,
						new IAsyncCallback<List<JSONObject>>() {

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

			SurespotApplication.getEncryptionController().eccEncrypt(mUsername, message, new IAsyncCallback<String>() {

				@Override
				public void handleResponse(String result) {
					SurespotApplication.getChatController().sendMessage(mUsername, result);
					TextKeyListener.clear(etMessage.getText());
				}
			});
		}
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
