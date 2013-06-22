package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.MessageDecryptor;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;
	private boolean mLoading;
	private IAsyncCallback<Boolean> mAllLoadedCallback;
	private boolean mDebugMode;
	private boolean mCheckingSequence;
	private int mCurrentScrollPositionId;
	private MessageDecryptor mMessageDecryptor;
	private MessageImageDownloader mMessageImageDownloader;
	private boolean mLoaded;

	public ChatAdapter(Context context) {
		SurespotLog.v(TAG, "Constructor.");
		mContext = context;
		SharedPreferences pm = context.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
		mDebugMode = pm.getBoolean("pref_debug_mode", false);
		// pm.getBoolean("pref_hide_deleted_messages", false);
		mMessageDecryptor = new MessageDecryptor(this);
		mMessageImageDownloader = new MessageImageDownloader(this);
	}

	public void doneCheckingSequence() {
		mCheckingSequence = false;
	}

	public void setAllLoadedCallback(IAsyncCallback<Boolean> callback) {
		mAllLoadedCallback = callback;
	}

	public void evictCache() {
		MessageImageDownloader.evictCache();
	}

	public ArrayList<SurespotMessage> getMessages() {
		return mMessages;
	}

	// get the last message that has an id
	public SurespotMessage getLastMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			SurespotMessage message = iterator.previous();
			if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
				return message;
			}
		}
		return null;
	}

	public SurespotMessage getFirstMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(0); iterator.hasNext();) {
			SurespotMessage message = iterator.next();
			if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
				return message;
			}
		}
		return null;
	}

	// update the id and sent status of the message once we received
	private boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort) throws SurespotMessageSequenceException {

		// SurespotLog.v(TAG, "addMessage, could not find message");

		// make sure message is in sequence
		//
		// if (!mCheckingSequence && checkSequence && (message.getId() != null)) {
		// SurespotMessage previousMessage = getLastMessageWithId();
		//
		// int previousId = 0;
		// if (previousMessage != null) {
		// previousId = previousMessage.getId();
		// }
		//
		// if (previousId != (message.getId() - 1)) {
		// throw new SurespotMessageSequenceException(previousId);
		// }
		//
		// }

		int index = mMessages.indexOf(message);
		boolean added = false;
		if (index == -1) {

			mMessages.add(message);
			added = true;
		}

		else {
			// SurespotLog.v(TAG, "addMessage, updating message");
			SurespotMessage updateMessage = mMessages.get(index);
			if (message.getId() != null) {
				// if the id is null 'tis the same as adding the message
				added = updateMessage.getId() == null;
				updateMessage.setId(message.getId());
			}
			if (message.getDateTime() != null) {
				updateMessage.setDateTime(message.getDateTime());
			}
			if (message.getData() != null) {
				updateMessage.setData(message.getData());
			}
			updateMessage.setGcm(message.isGcm());
			

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

	public synchronized void setMessages(ArrayList<SurespotMessage> messages) {
		if (messages.size() > 0) {
			mMessages.clear();
			mMessages.addAll(messages);

			// notifyDataSetChanged();
		}

	}

	// public void addMessages(ArrayList<SurespotMessage> messages) {
	// if (messages.size() > 0) {
	// mMessages.addAll(messages);
	//
	// // notifyDataSetChanged();
	// }
	// }

	// thanks to http://www.sherif.mobi/2012/01/listview-with-ability-to-hide-rows.html
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
		return getTypeForMessage(message);
	}

	public int getTypeForMessage(SurespotMessage message) {
		String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
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

	public int getCurrentScrollPositionId() {
		return mCurrentScrollPositionId;
	}

	public void setCurrentScrollPositionId(int currentScrollPositionId) {
		mCurrentScrollPositionId = currentScrollPositionId;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// SurespotLog.v(TAG, "getView, pos: " + position);
		final int type = getItemViewType(position);
		ChatMessageViewHolder chatMessageViewHolder = null;

		// check type again based on http://stackoverflow.com/questions/12018997/why-does-getview-return-wrong-convertview-objects-on-separatedlistadapter
		// and NPE I was getting that would only happen with wrong type

		if (convertView != null) {
			ChatMessageViewHolder currentViewHolder = (ChatMessageViewHolder) convertView.getTag();
			if (currentViewHolder.type != type) {
				SurespotLog.v(TAG, "types do not match, creating new view for the row");
				convertView = null;
			} else {
				chatMessageViewHolder = currentViewHolder;
			}
		}

		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			chatMessageViewHolder = new ChatMessageViewHolder();
			chatMessageViewHolder.type = type;

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
			chatMessageViewHolder.imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();
			
			chatMessageViewHolder.ivNotShareable = (ImageView) convertView.findViewById(R.id.messageImageNotShareable);
			chatMessageViewHolder.ivShareable = (ImageView) convertView.findViewById(R.id.messageImageShareable);

			if (mDebugMode) {
				chatMessageViewHolder.tvId = (TextView) convertView.findViewById(R.id.messageId);
				chatMessageViewHolder.tvToVersion = (TextView) convertView.findViewById(R.id.messageToVersion);
				chatMessageViewHolder.tvFromVersion = (TextView) convertView.findViewById(R.id.messageFromVersion);
				chatMessageViewHolder.tvIv = (TextView) convertView.findViewById(R.id.messageIv);
				chatMessageViewHolder.tvData = (TextView) convertView.findViewById(R.id.messageData);
				chatMessageViewHolder.tvMimeType = (TextView) convertView.findViewById(R.id.messageMimeType);
			}

			convertView.setTag(chatMessageViewHolder);
		}

		final SurespotMessage item = (SurespotMessage) getItem(position);

		if (item.getErrorStatus() > 0) {
			UIUtils.setMessageErrorText(mContext, chatMessageViewHolder.tvTime, item);
		} else {

			if (item.getId() == null) {

				chatMessageViewHolder.tvTime.setText(R.string.message_sending);
				SurespotLog.v(TAG, "getView, item.getId() is null, setting status text to sending...");
			} else {

				if (item.getPlainData() == null) {
					chatMessageViewHolder.tvTime.setText(R.string.message_loading_and_decrypting);
				} else {

					if (item.getDateTime() != null) {
						chatMessageViewHolder.tvTime.setText(DateFormat.getDateFormat(MainActivity.getContext()).format(item.getDateTime()) + " "
								+ DateFormat.getTimeFormat(MainActivity.getContext()).format(item.getDateTime()));
					} else {
						chatMessageViewHolder.tvTime.setText("");
						SurespotLog.v(TAG, "getView, item: %s", item);
					}
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
			} else {
				chatMessageViewHolder.tvText.setText("");
				mMessageDecryptor.decrypt(chatMessageViewHolder.tvText, item);
			}
			chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
			chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
		} else {
			chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvText.clearAnimation();
			chatMessageViewHolder.tvText.setVisibility(View.GONE);
			chatMessageViewHolder.tvText.setText("");
			if (!TextUtils.isEmpty(item.getData())) {
				mMessageImageDownloader.download(chatMessageViewHolder.imageView, item);
			}

			if (item.isShareable()) {
				chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
				chatMessageViewHolder.ivShareable.setVisibility(View.VISIBLE);
			} else {
				chatMessageViewHolder.ivNotShareable.setVisibility(View.VISIBLE);
				chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
			}
		}

		if (type == TYPE_US) {
			chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
			chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
		}

		if (mDebugMode) {
			chatMessageViewHolder.tvId.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvToVersion.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvFromVersion.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvIv.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvData.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvMimeType.setVisibility(View.VISIBLE);

			chatMessageViewHolder.tvId.setText("id: " + item.getId());
			chatMessageViewHolder.tvToVersion.setText("toVersion: " + item.getToVersion());
			chatMessageViewHolder.tvFromVersion.setText("fromVersion: " + item.getFromVersion());
			chatMessageViewHolder.tvIv.setText("iv: " + item.getIv());
			chatMessageViewHolder.tvData.setText("data: " + item.getData());
			chatMessageViewHolder.tvMimeType.setText("mimeType: " + item.getMimeType());
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
		public TextView tvId;
		public TextView tvToVersion;
		public TextView tvFromVersion;
		public TextView tvIv;
		public TextView tvData;
		public TextView tvMimeType;
		public ImageView ivShareable;
		public ImageView ivNotShareable;
		public int type;

	}

	public synchronized boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort, boolean notify)
			throws SurespotMessageSequenceException {
		boolean added = false;
		added = addOrUpdateMessage(message, checkSequence, sort);
		if (notify) {
			notifyDataSetChanged();
		}
		return added;

	}

	public synchronized void insertMessage(SurespotMessage message, boolean notify) {

		insertMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

	public synchronized SurespotMessage deleteMessageByIv(String iv) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			if (message.getIv().equals(iv)) {
				iterator.remove();
				message.setDeleted(true);
				notifyDataSetChanged();
				return message;
			}
		}

		return null;
	}

	public synchronized SurespotMessage deleteMessageById(Integer id) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			message = iterator.previous();

			Integer localId = message.getId();
			if (localId != null && localId.equals(id)) {
				SurespotLog.v(TAG, "deleting message");
				message.setDeleted(true);
				iterator.remove();
				notifyDataSetChanged();
				return message;
			}
		}

		return null;
	}

	public SurespotMessage getMessageById(Integer id) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			Integer localId = message.getId();
			if (localId != null && localId.equals(id)) {

				return message;
			}
		}
		return null;
	}

	public SurespotMessage getMessageByIv(String iv) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			message = iterator.previous();

			String localIv = message.getIv();
			if (localIv != null && localIv.equals(iv)) {
				return message;
			}
		}

		return null;
	}

	public void sort() {
		Collections.sort(mMessages);

	}

	public synchronized void deleteAllMessages(int utaiMessageId) {

		//
		// mMessages.clear();
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			SurespotMessage message = iterator.next();

			if (message.getId() == null || (message.getId() != null && message.getId() <= utaiMessageId)) {
				message.setDeleted(true);
				iterator.remove();
			}
		}
	}

	public synchronized void deleteTheirMessages(int utaiMessageId) {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			SurespotMessage message = iterator.next();

			// if it's not our message, delete it
			if (message.getId() != null && message.getId() <= utaiMessageId && !message.getFrom().equals(IdentityController.getLoggedInUser())) {
				message.setDeleted(true);
				iterator.remove();
			}
		}
	}

	public void userDeleted(boolean delete) {
		if (delete) {
			deleteTheirMessages(Integer.MAX_VALUE);
		}
	}

	// the first time we load the listview doesn't know
	// where to scroll because the items change size
	// so we keep track of which messages we're loading
	// so we know when they're done, and when they are
	// we can scroll to where we need to be
	public void checkLoaded() {
		if (!mLoaded) {
			for (SurespotMessage message : mMessages) {
				if (message.isLoading() && !message.isLoaded()) {
					return;
				}
			}

			mAllLoadedCallback.handleResponse(true);
			mLoaded = true;
		}
	}

	public boolean isLoaded() {
		return mLoaded;
	}

}
