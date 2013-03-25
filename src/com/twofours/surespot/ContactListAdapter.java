package com.twofours.surespot;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.twofours.surespot.SurespotContact.ContactData;

public class ContactListAdapter extends BaseAdapter {
	private final static String TAG = "ContactListAdapter";
	private ArrayList<SurespotContact> mSurespotContacts;

	private Context mContext;

	public ContactListAdapter(Context context, ArrayList<SurespotContact> contacts) {
		mContext = context;
		mSurespotContacts = contacts;

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
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		SurespotContact contact = (SurespotContact) getItem(position);
		// SurespotContactViewHolder friendViewHolder;

		// if (convertView == null) {
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		convertView = inflater.inflate(R.layout.contact_entry, parent, false);

		//
		// ((Button) convertView.findViewById(R.id.notificationItemAccept)).setOnClickListener(SurespotContactInviteResponseListener);
		// ((Button) convertView.findViewById(R.id.notificationItemIgnore)).setOnClickListener(SurespotContactInviteResponseListener);
		//
		// friendViewHolder = new SurespotContactViewHolder();
		// friendViewHolder.tvName = (TextView) convertView.findViewById(R.id.friendName);
		// friendViewHolder.vgInvite = convertView.findViewById(R.id.inviteLayout);
		// friendViewHolder.tvStatus = (TextView) convertView.findViewById(R.id.friendStatus);
		// friendViewHolder.vgActivity = convertView.findViewById(R.id.messageActivity);
		// convertView.setTag(friendViewHolder);

		// }
		// else {
		// friendViewHolder = (SurespotContactViewHolder) convertView.getTag();
		// }

		TextView tvId = (TextView) convertView.findViewById(R.id.contactId);
		TextView tvName = (TextView) convertView.findViewById(R.id.contactName);
		TextView tvEmailLabel = (TextView) convertView.findViewById(R.id.contactEmailLabel);
		TextView tvPhoneNumber = (TextView) convertView.findViewById(R.id.contactPhoneLabel);
		LinearLayout emails = (LinearLayout) convertView.findViewById(R.id.contactEmails);
		LinearLayout phoneNumbers = (LinearLayout) convertView.findViewById(R.id.contactPhoneNumbers);

		tvId.setText(contact.getId());
		tvName.setText(contact.getName());

		boolean hasEmails = contact.getEmails().size() > 0;
		emails.setVisibility(hasEmails ? View.VISIBLE : View.GONE);
		tvEmailLabel.setVisibility(hasEmails ? View.VISIBLE : View.GONE);

		boolean hasPhones = contact.getPhoneNumbers().size() > 0;
		phoneNumbers.setVisibility(hasPhones ? View.VISIBLE : View.GONE);
		tvPhoneNumber.setVisibility(hasPhones ? View.VISIBLE : View.GONE);

		if (contact.getPhoneNumbers().size() > 0) {
			for (ContactData number : contact.getPhoneNumbers()) {				
				LinearLayout phoneContactLayout = (LinearLayout) inflater.inflate(R.layout.contact_entry_data, null, false);
				TextView tvPhone = (TextView) phoneContactLayout.findViewById(R.id.contactData);
				TextView tvType = (TextView) phoneContactLayout.findViewById(R.id.contactDataType);
				tvPhone.setText(number.getData());
				tvType.setText(number.getType());
				phoneNumbers.addView(phoneContactLayout);
			}
		}
		
		if (contact.getEmails().size() > 0) {
			for (ContactData email : contact.getEmails()) {				
				LinearLayout emailLayout = (LinearLayout) inflater.inflate(R.layout.contact_entry_data, null, false);
				TextView tvEmail = (TextView) emailLayout.findViewById(R.id.contactData);				
				TextView tvType = (TextView) emailLayout.findViewById(R.id.contactDataType);
				tvEmail.setText(email.getData());
				tvType.setText(email.getType());
				emails.addView(emailLayout);
			}
		}

		return convertView;
	}

	private OnClickListener SurespotContactInviteResponseListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			final String action = (String) v.getTag();
			final int position = ((ListView) v.getParent().getParent().getParent()).getPositionForView((View) v.getParent());
			final SurespotContact friend = (SurespotContact) getItem(position);
			final String friendname = friend.getName();

		}
	};

	public static class NotificationViewHolder {
		public TextView tvName;
	}

	public static class SurespotContactViewHolder {
		public TextView tvName;
		public TextView tvStatus;
		public View vgInvite;
		public View vgActivity;
	}

	public ArrayList<SurespotContact> getSurespotContacts() {
		return mSurespotContacts;
	}

}
