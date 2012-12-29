package com.twofours.surespot;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class NotificationArrayAdapter extends ArrayAdapter<JSONObject> {
	private final Context context;
	private final List<JSONObject> values;
	
	public NotificationArrayAdapter(Context context, List<JSONObject> values) {
		
		super(context, R.layout.activity_notifications,  values);
		this.context = context;
		this.values = values;
	}
	
	@Override
	public int getCount() {
		return values.size();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.activity_notifications,  parent, false); 
		TextView textView = (TextView) rowView.findViewById(R.id.notificationsRow);
		try {
			textView.setText(values.get(position).getString("data"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rowView;
	}
}
