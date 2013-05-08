package com.twofours.surespot.contacts;

import java.util.ArrayList;

import com.twofours.surespot.R;
import com.twofours.surespot.R.id;
import com.twofours.surespot.R.layout;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class ContactListAdapter extends BaseAdapter {
	private static final int VIEW_TYPE_HEADER = 0;
	private static final int VIEW_TYPE_ITEM = 1;
	private static final int VIEW_TYPE_COUNT = 2;

	private final static String TAG = "ContactListAdapter";
	private ArrayList<ContactData> mSurespotContacts;

	private Context mContext;

	public ContactListAdapter(Context context, ArrayList<ContactData> contacts) {
		mContext = context;
		mSurespotContacts = contacts;

	}

	public void toggleSelected(int position) {

		ContactData contactData = (ContactData) getItem(position);
		boolean selected = contactData.isSelected();
		contactData.setSelected(!selected);
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return mSurespotContacts.size();
	}

	@Override
	public Object getItem(int position) {

		return mSurespotContacts.get(position);

	}

	@Override
	public boolean isEnabled(int position) {
		return !isHeader(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getViewTypeCount() {
		return VIEW_TYPE_COUNT;
	}

	@Override
	public int getItemViewType(int position) {
		if (isHeader(position)) {
			return VIEW_TYPE_HEADER;
		}
		else {
			return VIEW_TYPE_ITEM;
		}
	}

	private boolean isHeader(int position) {
		return mSurespotContacts.get(position).getType().equals("header");
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final int type = getItemViewType(position);
		final ContactDataViewHolder viewHolder;
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		ContactData contact = (ContactData) getItem(position);
		if (convertView == null) {
			viewHolder = new ContactDataViewHolder();

			switch (type) {
			case VIEW_TYPE_HEADER:
				convertView = inflater.inflate(R.layout.contact_entry_header, parent, false);
				break;
			case VIEW_TYPE_ITEM:
				convertView = inflater.inflate(R.layout.contact_entry_data, parent, false);
				viewHolder.tvType = (TextView) convertView.findViewById(R.id.contactDataType);
				viewHolder.cbSelected = (CheckBox) convertView.findViewById(R.id.contactSelected);
				break;
			}

			viewHolder.tvData = (TextView) convertView.findViewById(R.id.contactData);

			convertView.setTag(viewHolder);
		}
		else {
			viewHolder = (ContactDataViewHolder) convertView.getTag();
		}

		if (type == VIEW_TYPE_ITEM) {
			viewHolder.tvType.setText(contact.getType());
			viewHolder.cbSelected.setChecked(contact.isSelected());

		}

		viewHolder.tvData.setText(contact.getData());
		return convertView;
	}

	public static class ContactDataViewHolder {
		public CheckBox cbSelected;
		public TextView tvData;
		public TextView tvType;
	}

	public ArrayList<ContactData> getContacts() {
		return mSurespotContacts;
	}

	public void setAllSelected(boolean selected) {
		for (ContactData contact : mSurespotContacts) {
			if (!contact.getType().equals("header")) {
				contact.setSelected(selected);
			}
		}
		notifyDataSetChanged();
	}

}
