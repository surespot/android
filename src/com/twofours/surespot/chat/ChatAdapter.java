package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
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
	private final static String TAG = "FriendAdapter";
	private List<ChatMessage> mMessages = new ArrayList<ChatMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;

	public ChatAdapter(Context context) {
		mContext = context;
	}

	public void addMessage(ChatMessage message) {
		mMessages.add(message);
		notifyDataSetChanged();
	}

	public void addMessages(ArrayList<ChatMessage> messages) {
		mMessages = messages;
		notifyDataSetChanged();
	}

	public void clearMessages(boolean notify) {
		mMessages.clear();
		if (notify) {
			notifyDataSetChanged();
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
		ChatMessage message = mMessages.get(position);
		String otherUser = Utils.getOtherUser(message.getFrom(), message.getTo());
		if (otherUser.equals(message.getFrom())) {			
			return TYPE_US;
		}
		else {
			return TYPE_THEM;
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

			switch (type) {
				case TYPE_US:

					convertView = inflater.inflate(R.layout.message_list_item_us, parent, false);

					break;
				case TYPE_THEM:

					convertView = inflater.inflate(R.layout.message_list_item_them, parent, false);

					break;
			}

			chatMessageViewHolder = new ChatMessageViewHolder();
			chatMessageViewHolder.tvUser = (TextView) convertView.findViewById(R.id.messageUser);
			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);

			convertView.setTag(chatMessageViewHolder);
		}
		else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final ChatMessage item = (ChatMessage) getItem(position);
		chatMessageViewHolder.tvUser.setText(item.getFrom());
		if (item.getPlainText() != null) {
			chatMessageViewHolder.tvText.setText(item.getPlainText());
		}
		else {
			// decrypt
			EncryptionController.eccDecrypt((type == TYPE_THEM ? item.getTo() : item.getFrom()), item.getCipherText(),
					new IAsyncCallback<String>() {

						@Override
						public void handleResponse(String result) {

							item.setPlainText(result);
							chatMessageViewHolder.tvText.setText(result);

						
						}

					});
		}

		return convertView;
	}

	public static class ChatMessageViewHolder {
		public TextView tvUser;
		public TextView tvText;
	}

}
