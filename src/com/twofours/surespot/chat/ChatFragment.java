package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatFragment extends SherlockFragment {
	private ChatArrayAdapter chatAdapter;
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
		// reget the messages in case any were added while we were gone
		Log.v(TAG, "onResume, mUsername:  " + mUsername);
		// make sure the public key is there
		// TODO move this into network controller
		SurespotApplication.getEncryptionController().hydratePublicKey(mUsername, new IAsyncCallback<Void>() {
			@Override
			public void handleResponse(Void result) {
				// get the list of friends
				NetworkController.getMessages(mUsername, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here
						if (getActivity() != null) {
							List<JSONObject> messages = new ArrayList<JSONObject>();
							try {
								for (int i = 0; i < jsonArray.length(); i++) {
									messages.add(new JSONObject(jsonArray.getString(i)));
								}
							}
							catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							chatAdapter = new ChatArrayAdapter(getActivity(), messages);
							mListView.setAdapter(chatAdapter);
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
		mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
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
