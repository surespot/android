package com.twofours.surespot.chat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.IdentityController;
import com.twofours.surespot.ImageDownloader;
import com.twofours.surespot.MessageDecryptor;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;
	private boolean mLoading;
	private IAsyncCallback<Boolean> mLoadingCallback;

	public ChatAdapter(Context context) {
		SurespotLog.v(TAG, "Constructor.");
		mContext = context;

	}

	public boolean isLoading() {
		return mLoading;
	}

	public void setLoading(boolean loading) {
		mLoading = loading;
		if (mLoadingCallback != null) {
			mLoadingCallback.handleResponse(loading);
		}
	}

	public void setLoadingCallback(IAsyncCallback<Boolean> callback) {
		mLoadingCallback = callback;
	}

	public void evictCache() {
		ImageDownloader.evictCache();
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

	// update the id and sent status of the message once we received
	private boolean addOrUpdateMessage(SurespotMessage message, boolean sort) {
		int index = mMessages.indexOf(message);
		boolean added = false;
		if (index == -1) {
			// SurespotLog.v(TAG, "addMessage, could not find message");

			//
			mMessages.add(message);
			added = true;

		}
		else {
			// SurespotLog.v(TAG, "addMessage, updating message");
			SurespotMessage updateMessage = mMessages.get(index);
			if (message.getId() != null) {
				updateMessage.setId(message.getId());
			}
			if (message.getDateTime() != null) {
				updateMessage.setDateTime(message.getDateTime());
			}
			if (message.getCipherData() != null) {
				updateMessage.setCipherData(message.getCipherData());
			}

		}

		if (sort) {
			sort();
		}
		return added;
	}

	private void insertMessage(SurespotMessage message) {
		// if (mMessages.indexOf(message) == -1) {
		mMessages.add(0, message);
		// }
		// else {
		// SurespotLog.v(TAG, "insertMessage, message already present");
		// }
	}

	public void addMessages(ArrayList<SurespotMessage> messages) {
		if (messages.size() > 0) {
			// mMessages.clear();
			mMessages.addAll(messages);

			// notifyDataSetChanged();
		}
	}

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
		String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
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

			chatMessageViewHolder.tvTime = (TextView) convertView.findViewById(R.id.messageTime);
			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
			chatMessageViewHolder.imageView = (ImageView) convertView.findViewById(R.id.messageImage);

			convertView.setTag(chatMessageViewHolder);
		}
		else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final SurespotMessage item = (SurespotMessage) getItem(position);

		if (item.getId() == null) {
			chatMessageViewHolder.tvTime.setText("sending...");
		}
		else {
			// if the sender deleted it, or we deleted their message, don't show the data
			if (item.getDeletedFrom() || (item.getDeletedTo() && !item.getFrom().equals(IdentityController.getLoggedInUser()))) {
				item.setPlainData("deleted");
			}

			if (item.getPlainData() == null) {
				chatMessageViewHolder.tvTime.setText("loading and decrypting...");
			}
			else {
				if (item.getDateTime() != null) {

					chatMessageViewHolder.tvTime.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
							item.getDateTime()));
				}
				else {
					chatMessageViewHolder.tvTime.setText("");
				}
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
				MessageDecryptor.decrypt(chatMessageViewHolder.tvText, item);
			}
		}
		else {
			chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvText.clearAnimation();
			chatMessageViewHolder.tvText.setVisibility(View.GONE);
			chatMessageViewHolder.tvText.setText("");
			ImageDownloader.download(chatMessageViewHolder.imageView, item);
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

	public boolean addOrUpdateMessage(SurespotMessage message, boolean sort, boolean notify) {
		boolean added = false;
		added = addOrUpdateMessage(message, sort);
		if (notify) {
			notifyDataSetChanged();
		}
		return added;

	}

	public void insertMessage(SurespotMessage message, boolean notify) {

		insertMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

	public SurespotMessage deleteMessageByIv(String iv) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			if (message.getIv().equals(iv)) {
				iterator.remove();
				break;
			}
		}
		notifyDataSetChanged();
		return message;
	}

	// public SurespotMessage deleteMessageById(Integer id) {
	// SurespotMessage message = null;
	// for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
	// message = iterator.previous();
	//
	// Integer localId = message.getId();
	// if (localId != null && localId.equals(id)) {
	// SurespotLog.v(TAG,"deleting message");
	// iterator.remove();
	//
	// break;
	// }
	// }
	// notifyDataSetChanged();
	// return message;
	// }

	public SurespotMessage getMessageById(Integer id) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			Integer localId = message.getId();
			if (localId != null && localId.equals(id)) {
				// SurespotLog.v(TAG, "deleting message");
				// message.setCipherData("deleted");
				// if (message.getTo().equals(IdentityController.getLoggedInUser())) {
				// message.setDeletedTo(true);
				// }

				break;
			}
		}
		// notifyDataSetChanged();
		return message;
	}

	public void sort() {
		Collections.sort(mMessages);

	}

}
