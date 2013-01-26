package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.ListIterator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.BitmapCache;
import com.twofours.surespot.ImageDownloader;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;
	private final BitmapCache mBitmapCache = new BitmapCache();
	private final ImageDownloader mImageDownloader = new ImageDownloader();

	public ChatAdapter(Context context) {
		Log.v(TAG, "Constructor.");
		mContext = context;
	}

	public void evictCache() {
		mBitmapCache.evictAll();
	}

	public ArrayList<SurespotMessage> getMessages() {
		return mMessages;
	}

	// get the last message that has an id
	public SurespotMessage getLastMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			SurespotMessage message = iterator.previous();
			if (message.getId() != null) {
				return message;
			}
		}
		return null;
	}

	public SurespotMessage getFirstMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(0); iterator.hasNext();) {
			SurespotMessage message = iterator.next();
			if (message.getId() != null) {
				return message;
			}
		}
		return null;
	}

	// public void setMessages(ArrayList<ChatMessage> messages) {
	// mMessages = messages;
	// }

	// update the id and sent status of the message once we received
	private void addOrUpdateMessage(SurespotMessage message) {
		// if the id is null we're sending the message so just add it
		if (message.getId() == null) {
			mMessages.add(message);
		} else {
			int index = mMessages.indexOf(message);
			if (index == -1) {
				// Log.v(TAG, "addMessage, could not find message");

				//
				mMessages.add(message);
			} else {
				// Log.v(TAG, "addMessage, updating message");
				SurespotMessage updateMessage = mMessages.get(index);
				updateMessage.setId(message.getId());
			}
		}
	}

	private void insertMessage(SurespotMessage message) {
		mMessages.add(0, message);
	}

	public void addMessages(ArrayList<SurespotMessage> messages) {
		if (messages.size() > 0) {
			mMessages.clear();
			mMessages.addAll(messages);
			// notifyDataSetChanged();
		}
	}

	//
	// public void clearMessages(boolean notify) {
	// mMessages.clear();
	// if (notify) {
	// notifyDataSetChanged();
	// }
	// }

	@Override
	public int getCount() {
		return mMessages.size();
	}

	@Override
	public Object getItem(int position) {
		return mMessages.get(position);
	}

	@Override
	public int getItemViewType(int position) {
		SurespotMessage message = mMessages.get(position);
		String otherUser = Utils.getOtherUser(message.getFrom(), message.getTo());
		if (otherUser.equals(message.getFrom())) {
			return TYPE_THEM;
		} else {
			return TYPE_US;
		}
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// Log.v(TAG, "getView, pos: " + position);

		final int type = getItemViewType(position);
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final ChatMessageViewHolder chatMessageViewHolder;
		if (convertView == null) {
			chatMessageViewHolder = new ChatMessageViewHolder();

			switch (type) {
			case TYPE_US:
				convertView = inflater.inflate(R.layout.message_list_item_us, parent, false);
				chatMessageViewHolder.vMessageSending = convertView.findViewById(R.id.messageSending);
				chatMessageViewHolder.vMessageSent = convertView.findViewById(R.id.messageSent);
				break;
			case TYPE_THEM:
				convertView = inflater.inflate(R.layout.message_list_item_them, parent, false);
				break;
			}

			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
			chatMessageViewHolder.imageView = (ImageView) convertView.findViewById(R.id.messageImage);
			convertView.setTag(chatMessageViewHolder);
		} else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final SurespotMessage item = (SurespotMessage) getItem(position);

		if (item.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {
			chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);
			chatMessageViewHolder.imageView.setVisibility(View.GONE);			
			chatMessageViewHolder.imageView.clearAnimation();
			
			if (item.getPlainData() != null) {
				chatMessageViewHolder.tvText.setText(item.getPlainData());
			} else {

				if (!item.isLoading()) {
					item.setLoading(true);
					
					chatMessageViewHolder.tvText.setText(item.getCipherData());

					//decrypt on background thread
					new AsyncTask<Void, Void, String>() {

						@Override
						protected String doInBackground(Void... params) {
							// decrypt
							String result = EncryptionController.symmetricDecryptSync((type == TYPE_US ? item.getTo() : item.getFrom()),
									item.getIv(), item.getCipherData());
							return result;
						}

						protected void onPostExecute(String result) {

							if (result != null) {
								item.setPlainData(result);
								chatMessageViewHolder.tvText.setText(result);

							} else {
								chatMessageViewHolder.tvText.setText("Could not decrypt message.");
							}

							item.setLoading(false);
						}
					}.execute();

				}
			}
		} else {
			
			chatMessageViewHolder.tvText.setVisibility(View.GONE);
			chatMessageViewHolder.tvText.setText("");
			chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);

			if (item.getHeight() > 0) {
				Log.v(TAG, "Setting height: " + item.getHeight());
				chatMessageViewHolder.imageView.getLayoutParams().height = item.getHeight();
			}

			mImageDownloader.download(item.getCipherData(), item.getId(), item.getIv(), Utils.getOtherUser(item.getFrom(), item.getTo()),
					chatMessageViewHolder.imageView);

		}

		if (type == TYPE_US) {
			chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
			chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
		}

		return convertView;
	}

	public static class ChatMessageViewHolder {
		// public TextView tvUser;
		public TextView tvText;
		public View vMessageSending;
		public View vMessageSent;
		public ImageView imageView;
	}

	public void addOrUpdateMessage(SurespotMessage message, boolean notify) {
		addOrUpdateMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

	public void insertMessage(SurespotMessage message, boolean notify) {
		insertMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

}
