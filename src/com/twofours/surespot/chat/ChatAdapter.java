package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ListIterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<ChatMessage> mMessages = new ArrayList<ChatMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;

	public ChatAdapter(Context context) {
		mContext = context;
	}

	public Collection<ChatMessage> getMessages() {
		return mMessages;
	}

	// get the last message that has an id
	public ChatMessage getLastMessageWithId() {
		for (ListIterator<ChatMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			ChatMessage message = iterator.previous();
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
	public void addOrUpdateMessage(ChatMessage message) {
		// if the id is null we're sending the message so just add it
		if (message.getId() == null) {
			mMessages.add(message);
		} else {
			int index = mMessages.indexOf(message);
			if (index == -1) {
				Log.v(TAG, "addMessage, could not find message");

				//
				mMessages.add(message);
			} else {
				Log.v(TAG, "addMessage, updating message");
				ChatMessage updateMessage = mMessages.get(index);
				updateMessage.setId(message.getId());
			}
		}
	}

	public void addMessages(ArrayList<ChatMessage> messages) {
		if (messages.size() > 0) {
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
		ChatMessage message = mMessages.get(position);
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

		int type = getItemViewType(position);
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
			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
			convertView.setTag(chatMessageViewHolder);
		} else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final ChatMessage item = (ChatMessage) getItem(position);
		if (type == TYPE_US) {
			chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
			chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
		}

		if (item.getPlainText() != null) {
			chatMessageViewHolder.tvText.setText(item.getPlainText());
		} else {

//			try {
//				JSONObject text = new JSONObject(item.getCipherText());
//				chatMessageViewHolder.tvText.setText(text.getString("ciphertext"));
//			} catch (JSONException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			// decrypt
			EncryptionController.eccDecrypt((type == TYPE_US ? item.getTo() : item.getFrom()), item.getCipherText(),
					new IAsyncCallback<String>() {

						@Override
						public void handleResponse(String result) {

							if (result != null) {
							item.setPlainText(result);
							chatMessageViewHolder.tvText.setText(result);
							}
							else {
								chatMessageViewHolder.tvText.setText("Could not decrypt message.");	
							}

						}

					});
		}

		return convertView;
	}

	public static class ChatMessageViewHolder {
		// public TextView tvUser;
		public TextView tvText;
		public View vMessageSending;
		public View vMessageSent;
	}

	public void addOrUpdateMessage(ChatMessage message, boolean notify) {
		addOrUpdateMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

}
