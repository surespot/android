package com.twofours.surespot.chat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.ListIterator;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.ImageDownloader;
import com.twofours.surespot.ImageViewActivity;
import com.twofours.surespot.MessageDecryptor;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.Utils;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;
	private final ImageDownloader mImageDownloader = new ImageDownloader();
	private final MessageDecryptor mTextDecryptor = new MessageDecryptor();
	private OnClickListener mOnImageClickListener;

	public ChatAdapter(Context context) {
		SurespotLog.v(TAG, "Constructor.");
		mContext = context;
		mOnImageClickListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				// tag should be set to the download task
				SurespotMessage message = (SurespotMessage) v.getTag();

				// pull the message out
				if (message != null) {
					Intent newIntent = new Intent(mContext, ImageViewActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE, message.toJSONObject().toString());
					mContext.startActivity(newIntent);
				}
			}
		};

	}

	public void evictCache() {
		mImageDownloader.evictCache();
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
		}
		else {
			int index = mMessages.indexOf(message);
			if (index == -1) {
				// SurespotLog.v(TAG, "addMessage, could not find message");

				//
				mMessages.add(message);
			}
			else {
				// SurespotLog.v(TAG, "addMessage, updating message");
				SurespotMessage updateMessage = mMessages.get(index);
				updateMessage.setId(message.getId());
				updateMessage.setDateTime(message.getDateTime());
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
		}
		else {
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
		// SurespotLog.v(TAG, "getView, pos: " + position);

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

			// chatMessageViewHolder.tvUser = (TextView) convertView.findViewById(R.id.messageUser);
			chatMessageViewHolder.tvTime = (TextView) convertView.findViewById(R.id.messageTime);
			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
			chatMessageViewHolder.imageView = (ImageView) convertView.findViewById(R.id.messageImage);
			chatMessageViewHolder.imageView.setOnClickListener(mOnImageClickListener);

			convertView.setTag(chatMessageViewHolder);
		}
		else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final SurespotMessage item = (SurespotMessage) getItem(position);

		if (item.getDateTime() != null) {
			chatMessageViewHolder.tvTime.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvTime.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
					item.getDateTime()));
		}
		else {
			if (item.getId() == null) {
				chatMessageViewHolder.tvTime.setVisibility(View.VISIBLE);
				chatMessageViewHolder.tvTime.setText("sending...");
			}
			else {
				chatMessageViewHolder.tvTime.setVisibility(View.VISIBLE);
				chatMessageViewHolder.tvTime.setText("");
			}
		}

		if (item.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {
			chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);
			chatMessageViewHolder.imageView.setVisibility(View.GONE);
			chatMessageViewHolder.imageView.clearAnimation();
			chatMessageViewHolder.imageView.setImageBitmap(null);
			if (item.getPlainData() != null) {
				chatMessageViewHolder.tvText.clearAnimation();
				chatMessageViewHolder.tvText.setText(item.getPlainData());
			}
			else {
				chatMessageViewHolder.tvText.setText("");
				mTextDecryptor.decrypt(chatMessageViewHolder.tvText, item);
			}
		}
		else {
			chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvText.clearAnimation();
			chatMessageViewHolder.tvText.setVisibility(View.GONE);
			chatMessageViewHolder.tvText.setText("");
			mImageDownloader.download(chatMessageViewHolder.imageView, item);
		}

		if (type == TYPE_US) {
			chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
			chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
		}

		return convertView;
	}

	public static class ChatMessageViewHolder {
		public TextView tvText;
		public TextView tvUser;
		public View vMessageSending;
		public View vMessageSent;
		public ImageView imageView;
		public TextView tvTime;
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
