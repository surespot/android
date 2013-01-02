package com.twofours.surespot.layout;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.twofours.surespot.R;


public class ChatArrayAdapter extends ArrayAdapter<JSONObject> {
	private final Context context;
	private final List<JSONObject> values;
	private static final String TAG = "ChatArrayAdapter";
	
	static class MessageViewHolder {
		TextView messageText;
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
		MessageViewHolder messageViewHolder;
		if (rowView == null) {
			
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.message_list_item, parent, false);
			
			messageViewHolder = new MessageViewHolder();
			messageViewHolder.messageText = (TextView) rowView.findViewById(R.id.messageText);
			
			rowView.setTag(messageViewHolder);

		}
		else {
			messageViewHolder = (MessageViewHolder) rowView.getTag();
		}
		
		

		JSONObject message = values.get(position);
		//String room = Utils.getOtherUser(message.getString("from"), message.getString("to"));
		try {
			messageViewHolder.messageText.setText(message.getString("plaintext"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// SurespotApplication.getEncryptionController().eccDecrypt(room, message.getString("text"));
		// decrypt
		/*
		 * new IAsyncCallback<String>() {
		 * 
		 * @Override public void handleResponse(String result) { try { //JSON textView.setText(
		 * values.get(position).getJSONObject("data")); } catch (JSONException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 * 
		 * } };
		 */

		return rowView;
	}

	
}
