package com.twofours.surespot.chat;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
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
import com.twofours.surespot.network.IAsyncCallback;

public class ChatFragment extends SherlockFragment {
	private String TAG = "ChatFragment";
	private String mUsername;
	private ListView mListView;
	private EditText mEditText;
	private boolean mLoading;
	private int mPreviousTotal;
	private Button mSendButton;
	private Timer mTimer;

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
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		SurespotLog.v(TAG, "onCreateView, username: " + mUsername);

		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);

		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);

				// pull the message out
				if (message != null) {

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
		});

		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

				SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);
				if (message.getFrom().equals(IdentityController.getLoggedInUser())) {
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
					MainActivity.getChatController().setCurrentChat(null);
				}
			}
		});

		mSendButton.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (mEditText.getText().toString().length() == 0) {
					MainActivity.getChatController().closeTab();
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
				if (count > 0) {
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

		ChatController chatController = MainActivity.getChatController();
		if (chatController != null) {
			mChatAdapter = chatController.getChatAdapter(MainActivity.getContext(), mUsername);
			SurespotLog.v(TAG, "onCreateView settingChatAdapter for: " + mUsername);

			mListView.setAdapter(mChatAdapter);
			mListView.setDividerHeight(1);
			mListView.setOnScrollListener(mOnScrollListener);
			mChatAdapter.setLoadingCallback(new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(Boolean loading) {
					// mLoading = loading;
					SurespotLog.v(TAG, "chatAdapter loading: " + loading);
					if (loading) {
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// only show the dialog if we haven't loaded within 500 ms
						if (mTimer != null) {
							mTimer.cancel();
						}

						mTimer = new Timer();
						mTimer.schedule(new TimerTask() {

							@Override
							public void run() {
								Handler handler = MainActivity.getMainHandler();
								if (handler != null) {
									handler.post(new Runnable() {

										@Override
										public void run() {
											SurespotLog.v(TAG, "chat fragment showing progress");
											view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
										}
									});
								}

							}
						}, 250);

					}
					else {
						SurespotLog.v(TAG, "chat fragment tearing progress down");
						if (mTimer != null) {
							mTimer.cancel();
							mTimer = null;
						}

						view.findViewById(R.id.progressBar).setVisibility(View.GONE);
						mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
					}
				}
			});
		}

		return view;
	}

	private int mSelection;
	private int mTop;
	private boolean mJustLoaded;
	private OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		//	SurespotLog.v(TAG, "onScroll, mLoadiNG : " + mLoading + ", totalItemCount: " + totalItemCount + ", firstVisibleItem: "
		//			+ firstVisibleItem + ", visibleItemCount: " + visibleItemCount);

			// will have more items if we loaded them
			if (mLoading && mPreviousTotal > 0 && totalItemCount > mPreviousTotal) {
				// SurespotLog.v(TAG, "mPreviousTotal: " + mPreviousTotal + ", totalItemCount: " + totalItemCount);

				int loaded = totalItemCount - mPreviousTotal;
				SurespotLog.v(TAG, "loaded: " + loaded + ", setting selection: " + (mSelection + loaded));
				mListView.setSelectionFromTop(mSelection + loaded, mTop);

				//mPreviousTotal = totalItemCount;
				mJustLoaded = true;
				mLoading = false;
				return;
			}

			if (!mLoading) {
				boolean hint = getUserVisibleHint();
				SurespotLog.v(TAG, "hint: " + hint);
				if (hint) {
					ChatController chatController = MainActivity.getChatController();
					boolean hasEarlier = chatController.hasEarlierMessages(mUsername);
					SurespotLog.v(TAG, "hasEarlier: " + hasEarlier);
					if (chatController != null && hasEarlier && (firstVisibleItem > 0 && firstVisibleItem < 12)) {

						// SurespotLog.v(TAG, "onScroll, totalItemCount: " + totalItemCount + ", firstVisibleItem: " + firstVisibleItem
						// + ", visibleItemCount: " + visibleItemCount);

						// immediately after setting the position above mLoading is false with "firstVisibleItem" set to the pre loading
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

							MainActivity.getChatController().loadEarlierMessages(mUsername);
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
		// mTop = 0;
		// mPreviousTotal = 1;
		//mLoading = false;

	}

	private void sendMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		MainActivity.getChatController().sendMessage(mUsername, message, SurespotConstants.MimeTypes.TEXT);

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
		Bundle extras = intent.getExtras();

		if (action.equals(Intent.ACTION_SEND)) {
			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = extras.getString(Intent.EXTRA_TEXT);
				SurespotLog.v(TAG, "received action send, data: " + sharedText);
				mEditText.append(sharedText);
				requestFocus();
			}
			else if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

				final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

				Utils.makeToast(getActivity(), getString(R.string.uploading_image));

				SurespotLog.v(TAG, "received image data, upload image, uri: " + imageUri);
				final FragmentActivity activity = getActivity();
				ChatUtils.uploadPictureMessageAsync(activity, imageUri, mUsername, true, null, new IAsyncCallback<Boolean>() {

					@Override
					public void handleResponse(final Boolean result) {
						SurespotLog.v(TAG, "upload picture response: " + result);
						activity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								Utils.makeToast(activity, getString(result ? R.string.image_successfully_uploaded
										: R.string.could_not_upload_image));

							}
						});

						getActivity().getIntent().setAction(null);
						getActivity().getIntent().setType(null);

						scrollToEnd();
					}
				});
			}
		}
		else {
			if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
				// TODO implement
			}
		}
	}

	public void requestFocus() {
		mEditText.requestFocus();

	}

	private void scrollToEnd() {
		if (mChatAdapter != null && mListView != null) {
			mListView.setSelection(mChatAdapter.getCount());
		}
	}
}
