package com.twofours.surespot.chat;

import java.util.Timer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.images.ImageViewActivity;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatFragment extends SherlockFragment {
	private String TAG = "ChatFragment";
	private String mUsername;
	private ListView mListView;

	private boolean mLoading;
	private int mPreviousTotal;

	private Timer mTimer;
	private int mSelectedItem = -1;
	private int mSelectedTop = 0;
	// private int mSelection;
	// private int mTop;
	private boolean mJustLoaded;
	private boolean mIsDeleted;
	private ChatAdapter mChatAdapter;
	private boolean mKeyboardWasOpen;

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
		//cf.setRetainInstance(true);
		return cf;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setUsername(getArguments().getString("username"));
		TAG = TAG + ":" + getUsername();
		SurespotLog.v(TAG, "onCreate");
		
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		SurespotLog.v(TAG, "onCreateView, username: %s, selectedItem: %d",mUsername ,mSelectedItem);

		if (savedInstanceState != null) {
			mSelectedItem = savedInstanceState.getInt("selectedItem");
			mSelectedTop = savedInstanceState.getInt("selectedTop");

			SurespotLog.v(TAG, "loaded selectedItem: " + mSelectedItem);
			SurespotLog.v(TAG, "loaded selectedTop: " + mSelectedTop);

		}

		
		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		mListView.setEmptyView(view.findViewById(R.id.message_list_empty));

		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);

				// pull the message out
				if (message != null) {

					if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
						ImageView imageView = (ImageView) view.findViewById(R.id.messageImage);
						if (!(imageView.getDrawable() instanceof MessageImageDownloader.DownloadedDrawable)) {

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
				if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {

					TextMessageMenuFragment dialog = new TextMessageMenuFragment();
					dialog.setActivityAndMessage(getMainActivity(), message);
					dialog.show(getActivity().getSupportFragmentManager(), "TextMessageMenuFragment");
					return true;
				}
				else {

					ImageMessageMenuFragment dialog = new ImageMessageMenuFragment();
					dialog.setActivityAndMessage(getMainActivity(), message);
					dialog.show(getActivity().getSupportFragmentManager(), "ImageMessageMenuFragment");
					return true;
				}

			}
		});

		ChatController chatController = getMainActivity().getChatController();
		if (chatController != null) {
			mChatAdapter = chatController.getChatAdapter(getMainActivity(), mUsername);
			SurespotLog.v(TAG, "onCreateView settingChatAdapter for: " + mUsername);

			mListView.setAdapter(mChatAdapter);
			mListView.setDividerHeight(1);
			mListView.setOnScrollListener(mOnScrollListener);
			scrollToState();

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
							// mSelection = firstVisibleItem;
							// View v = mListView.getChildAt(0);
							// mTop = (v == null) ? 0 : v.getTop();

							getMainActivity().getChatController().loadEarlierMessages(mUsername, new IAsyncCallback<Void>() {

								@Override
								public void handleResponse(Void nothing) {

									int selection = mListView.getFirstVisiblePosition();
									// mSelection = firstVisibleItem;
									View v = mListView.getChildAt(0);
									int top = (v == null) ? 0 : v.getTop();
									int totalItemCount = mChatAdapter.getCount();
									// will have more items if we loaded them
									if (mLoading && mPreviousTotal > 0 && totalItemCount > mPreviousTotal) {
										// SurespotLog.v(TAG, "mPreviousTotal: " + mPreviousTotal + ", totalItemCount: " + totalItemCount);

										// mChatAdapter.notifyDataSetChanged();

										int loaded = totalItemCount - mPreviousTotal;
										// SurespotLog.v(TAG, "loaded: " + loaded + ", setting selection: " + (mSelection + loaded));
										mListView.setSelectionFromTop(selection + loaded, top);

										// mPreviousTotal = totalItemCount;
										mJustLoaded = true;
										mLoading = false;
										return;
									}
									else {
										mJustLoaded = false;
										mLoading = false;
									}

								}
							});
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

		SurespotLog.v(TAG, "onAttach, mSelectedItem: %d", mSelectedItem );
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
		SurespotLog.v(TAG, "onActivityCreated");

//		if (savedInstanceState != null) {
//			mSelectedItem = savedInstanceState.getInt("selectedItem");
//			mSelectedTop = savedInstanceState.getInt("selectedTop");
//
//			SurespotLog.v(TAG, "loaded selectedItem: " + mSelectedItem);
//			SurespotLog.v(TAG, "loaded selectedTop: " + mSelectedTop);
//
//		}

	}

	@Override
	public void onPause() {
		super.onPause();
		SurespotLog.v(TAG, "onPause, mUsername:  " + mUsername + ", currentScrollId: " + mListView.getFirstVisiblePosition());
		// set the current scroll position so we know how many messages to save


		mChatAdapter.setCurrentScrollPositionId(mListView.getFirstVisiblePosition());
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

	// public void requestFocus() {
	// SurespotLog.v(TAG, "requestFocus");
	// mEditText.clearFocus();
	// mEditText.requestFocus();
	//
	// }

	public void scrollToEnd() {
		SurespotLog.v(TAG, "scrollToEnd");
		if (mChatAdapter != null && mListView != null) {
			mChatAdapter.notifyDataSetChanged();
			mListView.postDelayed(new Runnable() {

				@Override
				public void run() {
					// mChatAdapter.notifyDataSetChanged();
					mListView.setSelection(mChatAdapter.getCount() - 1);
					//
				}
			}, 100);
		}
	}

	public void scrollToState() {
		SurespotLog.v(TAG, "scrollToState, mSelectedItem: " + mSelectedItem);
		if (mChatAdapter != null && mListView != null) {

			if (mSelectedItem > -1 && mSelectedItem != mListView.getSelectedItemPosition()) {

				mListView.post(new Runnable() {

					@Override
					public void run() {

						mListView.setSelectionFromTop(mSelectedItem, mSelectedTop);
					}

				});

			}
			else {
				scrollToEnd();
			}
		}
	}
	
	

	@Override
	public void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);

		if (mListView != null) {
			
			int lastVisiblePosition = mListView.getLastVisiblePosition() ;
			
			SurespotLog.v(TAG, "onSaveInstanceState lastVisiblePosition: %d", lastVisiblePosition);
			SurespotLog.v(TAG, "onSaveInstanceState mListview count() - 1: %d", mListView.getCount() - 1);
			if (lastVisiblePosition == mListView.getCount() - 1) {
				outState.putInt("selectedItem", -1);
				outState.putInt("selectedTop", -1);
			}
			else {

				int selection = mListView.getFirstVisiblePosition();
				// SurespotMessage message = (SurespotMessage) mListView.getItemAtPosition(selection);
				View v = mListView.getChildAt(0);

				int top = (v == null) ? 0 : v.getTop();

				SurespotLog.v(TAG, "saving selected item: %d", selection);

				// if we're at the bottom we want to go back to the bottom

				outState.putInt("selectedItem", selection);//UIUtils.getResumePosition(selection, mListView.getCount()));
				outState.putInt("selectedTop", top);
				// outState.putString("selectedMessage", message.toJSONObject().toString());
			}
		}
	}

}
