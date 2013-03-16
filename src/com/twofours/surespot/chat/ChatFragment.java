package com.twofours.surespot.chat;

import java.util.Timer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.ImageDownloader;
import com.twofours.surespot.MessageDialogMenuFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.ImageViewActivity;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ChatFragment extends SherlockFragment {
	private String TAG = "ChatFragment";
	private String mUsername;
	private ListView mListView;
	private EditText mEditText;
	private boolean mLoading;
	private int mPreviousTotal;
	private Button mSendButton;
	private Timer mTimer;
	private int mSelectedItem;
	private int mSelection;
	private int mTop;
	private boolean mJustLoaded;

	private ChatAdapter mChatAdapter;

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
		super.onCreate(savedInstanceState);
		setUsername(getArguments().getString("username"));
		TAG = TAG + ":" + getUsername();

		if (savedInstanceState != null) {
			mSelectedItem = savedInstanceState.getInt("selectedItem");
			SurespotLog.v(TAG, "loaded SelectedItem: " + mSelectedItem);
		}

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		SurespotLog.v(TAG, "onCreateView, username: " + mUsername);

		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		mListView.setEmptyView(view.findViewById(R.id.message_list_empty));

		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);

				// pull the message out
				if (message != null) {
					if (!message.getDeletedFrom()
							&& !(message.getDeletedTo() && message.getTo().equals(IdentityController.getLoggedInUser()))) {
						if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
							ImageView imageView = (ImageView) view.findViewById(R.id.messageImage);
							if (!(imageView.getDrawable() instanceof ImageDownloader.DownloadedDrawable)) {

								Intent newIntent = new Intent(ChatFragment.this.getActivity(), ImageViewActivity.class);
								newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								newIntent.putExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE, message.toJSONObject().toString());
								ChatFragment.this.getActivity().startActivity(newIntent);
							}
						}
					}

				}

			}
		});

		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

				SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);
				// if it's our message or it's their message and we deleted it
				if (!message.getDeletedFrom() && !(message.getDeletedTo() && message.getTo().equals(IdentityController.getLoggedInUser()))) {
					MessageDialogMenuFragment dialog = new MessageDialogMenuFragment();
					dialog.setMessage(message);
					dialog.show(getActivity().getSupportFragmentManager(), "MessageDialogMenuFragment");
					return true;
				}
				return false;

			}
		});

		mSendButton = (Button) view.findViewById(R.id.bSend);
		mSendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mEditText.getText().toString().length() > 0) {
					sendMessage();
				}
				else {
					// go to friends
					// InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(
					// ChatFragment.this.getActivity().INPUT_METHOD_SERVICE);
					// imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
					getMainActivity().getChatController().setCurrentChat(null);
				}
			}
		});

		mSendButton.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (mEditText.getText().toString().length() == 0) {
					getMainActivity().getChatController().closeTab();
				}
				return true;
			}
		});
		mEditText = (EditText) view.findViewById(R.id.etMessage);
		mEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;

				if (actionId == EditorInfo.IME_ACTION_SEND) {
					sendMessage();
					handled = true;
				}
				return handled;
			}
		});
		
		

		mEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

				if (mEditText.getText().length() > 0) {
					mSendButton.setText("send");
				}
				else {
					mSendButton.setText("home");
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		ChatController chatController = getMainActivity().getChatController();
		if (chatController != null) {
			mChatAdapter = chatController.getChatAdapter(getMainActivity(), mUsername);
			SurespotLog.v(TAG, "onCreateView settingChatAdapter for: " + mUsername);

			mListView.setAdapter(mChatAdapter);
			mListView.setDividerHeight(1);
			mListView.setOnScrollListener(mOnScrollListener);

		}

		return view;
	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

	@Override
	public void onDetach() {
		// TODO Auto-generated method stub
		super.onDetach();
		SurespotLog.v(TAG, "onDetach: " + mUsername);
	}

	private OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			// SurespotLog.v(TAG, "onScroll, mLoadiNG : " + mLoading + ", totalItemCount: " + totalItemCount + ", firstVisibleItem: "
			// + firstVisibleItem + ", visibleItemCount: " + visibleItemCount);

			// will have more items if we loaded them
			if (mLoading && mPreviousTotal > 0 && totalItemCount > mPreviousTotal) {
				// SurespotLog.v(TAG, "mPreviousTotal: " + mPreviousTotal + ", totalItemCount: " + totalItemCount);

				int loaded = totalItemCount - mPreviousTotal;
				// SurespotLog.v(TAG, "loaded: " + loaded + ", setting selection: " + (mSelection + loaded));
				mListView.setSelectionFromTop(mSelection + loaded, mTop);

				// mPreviousTotal = totalItemCount;
				mJustLoaded = true;
				mLoading = false;
				return;
			}

			if (!mLoading) {
				boolean hint = getUserVisibleHint();
				// SurespotLog.v(TAG, "hint: " + hint);
				if (hint) {
					ChatController chatController = getMainActivity().getChatController();
					if (chatController == null) {
						return;
					}
					boolean hasEarlier = chatController.hasEarlierMessages(mUsername);
					// SurespotLog.v(TAG, "hasEarlier: " + hasEarlier);
					if (chatController != null && hasEarlier && (firstVisibleItem > 0 && firstVisibleItem < 20) && totalItemCount > 29) {

						// SurespotLog.v(TAG, "onScroll, totalItemCount: " + totalItemCount + ", firstVisibleItem: " + firstVisibleItem
						// + ", visibleItemCount: " + visibleItemCount);

						// immediately after setting the position above, mLoading is false with "firstVisibleItem" set to the pre loading
						// value for what seems like one call
						// so handle that here
						if (mJustLoaded) {
							mJustLoaded = false;
							return;
						}

						else {

							mLoading = true;
							mPreviousTotal = mChatAdapter.getCount();
							mSelection = firstVisibleItem;
							View v = mListView.getChildAt(0);
							mTop = (v == null) ? 0 : v.getTop();

							getMainActivity().getChatController().loadEarlierMessages(mUsername);
						}
					}
				}
			}
		}

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {

		}
	};

	@Override
	public void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume: " + mUsername);
		// scrollToState();

		// ChatController chatController = getMainActivity().getChatController();
		// if (chatController != null) {
		// mChatAdapter = chatController.getChatAdapter(getMainActivity().getContext(), mUsername);
		// SurespotLog.v(TAG, "onCreateView settingChatAdapter for: " + mUsername);
		//
		// mListView.setAdapter(mChatAdapter);
		// mListView.setDividerHeight(1);
		// mListView.setOnScrollListener(mOnScrollListener);
		// mChatAdapter.setLoadingCallback(new IAsyncCallback<Boolean>() {
		//
		// @Override
		// public void handleResponse(Boolean loading) {
		// // mLoading = loading;
		// SurespotLog.v(TAG, "chatAdapter loading: " + loading);
		// if (loading) {
		// // view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		// // only show the dialog if we haven't loaded within 500 ms
		// if (mTimer != null) {
		// mTimer.cancel();
		// }
		//
		// mTimer = new Timer();
		// mTimer.schedule(new TimerTask() {
		//
		// @Override
		// public void run() {
		// Handler handler = getMainActivity().getMainHandler();
		// if (handler != null) {
		// handler.post(new Runnable() {
		//
		// @Override
		// public void run() {
		// SurespotLog.v(TAG, "chat fragment showing progress");
		// //view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		// }
		// });
		// }
		//
		// }
		// }, 250);
		//
		// }
		// else {
		// SurespotLog.v(TAG, "chat fragment tearing progress down");
		// if (mTimer != null) {
		// mTimer.cancel();
		// mTimer = null;
		// }
		//
		// //view.findViewById(R.id.progressBar).setVisibility(View.GONE);
		// //view.findViewById(R.id.message_list_empty).setVisibility(View.VISIBLE);
		// // mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
		// }
		// }
		// });
		// }

	};

	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);

		SurespotLog.v(TAG, "onAttach");
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
		SurespotLog.v(TAG, "onActivityCreated");

	}

	@Override
	public void onPause() {
		super.onPause();
		SurespotLog.v(TAG, "onPause, mUsername:  " + mUsername);

		// mListView.removeOnScrollListener()):
	}

	public void onDestroy() {
		super.onDestroy();
		SurespotLog.v(TAG, "onDestroy");
		// ChatController chatController = getMainActivity().getChatController();
		// if (chatController != null) {
		// chatController.destroyChatAdapter(mUsername);
		// }

	}

	private void sendMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		getMainActivity().getChatController().sendMessage(mUsername, message, SurespotConstants.MimeTypes.TEXT);

		//
		TextKeyListener.clear(etMessage.getText());

		// scroll to end
		scrollToEnd();
	}

	// populate the edit box
	public void handleSendIntent() {
		Intent intent = getActivity().getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		// Bundle extras = intent.getExtras();

		if (action.equals(Intent.ACTION_SEND)) {
			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = intent.getExtras().get(Intent.EXTRA_TEXT).toString();
				SurespotLog.v(TAG, "received action send, data: " + sharedText);
				mEditText.append(sharedText);
				requestFocus();
				// clear the intent
				getActivity().getIntent().setAction(null);
				getActivity().getIntent().setType(null);
				if (getActivity().getIntent().getExtras() != null) {
					getActivity().getIntent().getExtras().clear();
				}
			}

			Utils.configureActionBar(ChatFragment.this.getSherlockActivity(), "surespot", IdentityController.getLoggedInUser(), false);
		}

	}

	public void requestFocus() {
		SurespotLog.v(TAG, "requestFocus");
		mEditText.requestFocus();

	}

	public void scrollToEnd() {
		if (mChatAdapter != null && mListView != null) {
			SurespotLog.v(TAG, "scrollToEnd");
			mListView.setSelection(mChatAdapter.getCount());
		}
	}

	public void scrollToState() {

		if (mChatAdapter != null && mListView != null) {

			if (mSelectedItem > 0) {
				SurespotLog.v(TAG, "scrollToState");
				mListView.setSelection(mSelectedItem);
			}
			else {
				scrollToEnd();
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);

		int selction = mListView.getLastVisiblePosition();
		SurespotLog.v(TAG, "saving selected item: " + selction);
		outState.putInt("selectedItem", selction);

	}
	

}
