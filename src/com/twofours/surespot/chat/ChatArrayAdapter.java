package com.twofours.surespot.chat;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatArrayAdapter extends ArrayAdapter<JSONObject> {
	private final Context context;
	private final List<JSONObject> values;
	private static final String TAG = "ChatArrayAdapter";

	static class MessageViewHolder {
		TextView messageText;
		TextView messageUser;
	}

	public ChatArrayAdapter(Context context, List<JSONObject> values) {

		super(context, R.layout.message_list_item, values);
		this.context = context;
		this.values = values;
	}

	@Override
	public int getCount() {
		return values.size();
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View rowView = convertView;
		final MessageViewHolder messageViewHolder;
		if (rowView == null) {

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.message_list_item, parent, false);

			messageViewHolder = new MessageViewHolder();
			messageViewHolder.messageText = (TextView) rowView.findViewById(R.id.messageText);
			messageViewHolder.messageUser = (TextView) rowView.findViewById(R.id.messageUser);

			rowView.setTag(messageViewHolder);

		}
		else {
			messageViewHolder = (MessageViewHolder) rowView.getTag();
		}

		final JSONObject message = values.get(position);
		boolean needsDecryption = true;
		try {
			messageViewHolder.messageUser.setText(message.getString("from"));
			if (message.has("plaintext")) {

				messageViewHolder.messageText.setText(message.getString("plaintext"));
				needsDecryption = false;
			}

		}
		catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (needsDecryption) {
			try {
				// decrypt
				EncryptionController.eccDecrypt(

				Utils.getOtherUser(message.getString("from"), message.getString("to")), message.getString("text"),
						new IAsyncCallback<String>() {

							@Override
							public void handleResponse(String result) {
								try {
									message.put("plaintext", result);
									messageViewHolder.messageText.setText(result);

								}
								catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							}

						});

			}
			catch (JSONException j) {
				Log.e(TAG, j.toString());
			}

		}
		return rowView;

	}

}
