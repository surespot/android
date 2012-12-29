package com.twofours.surespot.layout;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.activities.NotificationsActivity;
import com.twofours.surespot.chat.IConnectCallback;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class NotificationArrayAdapter extends ArrayAdapter<JSONObject> {
	private final Context context;
	private final List<JSONObject> values;
	private static final String TAG = "NoficationArrayAdapter";

	public NotificationArrayAdapter(Context context, List<JSONObject> values) {

		super(context, R.layout.activity_notifications, values);
		this.context = context;
		this.values = values;
	}

	@Override
	public int getCount() {
		return values.size();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View rowView = convertView;

		if (rowView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.notification_list_item, parent, false);
			// add button listener
			((Button) rowView.findViewById(R.id.notificationItemAccept)).setOnClickListener(ActionClickListener);
			((Button) rowView.findViewById(R.id.notificationItemIgnore)).setOnClickListener(ActionClickListener);

		}

		TextView textView = (TextView) rowView.findViewById(R.id.notificationItemText);
		try {
			textView.setText(values.get(position).getString("data"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rowView;
	}

	private OnClickListener ActionClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			final String action = (String) v.getTag();
			final int position = ((ListView) v.getParent().getParent()).getPositionForView((View) v.getParent());

			String friendname;
			try {
				friendname = values.get(position).getString("data");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}

			SurespotApplication.getNetworkController().respondToInvite(friendname, action,
					new IAsyncNetworkResultCallback<Boolean>() {

						@Override
						public void handleResponse(Boolean result) {
							
							if (result) {
								Log.d(TAG,"Invitation acted upon successfully: " + action);
								
								//delete this row
								values.remove(position);
								
								if (action == "accept") {}
								// broadcast message to say invite acted upon (if accept add user to friend to avoid web
								// request)

							}
						}
					});

			// Log.d(TAG, "Title clicked, row: " + position + ", action: " + action);
		}
	};
}
