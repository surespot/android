package com.twofours.surespot;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twofours.surespot.SurespotContact.ContactData;
import com.twofours.surespot.common.SurespotLog;

public class ContactListAdapter extends BaseAdapter {
	private final static String TAG = "ContactListAdapter";
	private ArrayList<SurespotContact> mSurespotContacts;
	private OnClickListener mClickListener;
	private IContactSelectedCallback mCallback;

	private Context mContext;

	public ContactListAdapter(Context context, ArrayList<SurespotContact> contacts, IContactSelectedCallback callback) {
		mContext = context;
		mCallback = callback;
		mSurespotContacts = contacts;
		mClickListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				TextView tv = (TextView) v.findViewById(R.id.contactData);
				if (mCallback != null) {
					mCallback.contactSelected(v.getTag().toString(), tv.getText().toString());
				}
				SurespotLog.v(TAG, "onClick: " + tv.getText().toString() + ", type: " + v.getTag());

			}

		};

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
		return false;
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

		// TextView tvId = (TextView) convertView.findViewById(R.id.contactId);
		TextView tvName = (TextView) convertView.findViewById(R.id.contactName);

		LinearLayout contactDataItems = (LinearLayout) convertView.findViewById(R.id.contactDataItems);		

		// tvId.setText(contact.getId());
		tvName.setText(contact.getName());

		if (contact.getEmails().size() > 0) {
			View vDivider = null;
			for (ContactData email : contact.getEmails()) {
				LinearLayout emailLayout = (LinearLayout) inflater.inflate(R.layout.contact_entry_data, null, false);
				emailLayout.setOnClickListener(mClickListener);
				emailLayout.setTag("email");
				// TextView tvContactType = (TextView) emailLayout.findViewById(R.id.contactType);
				TextView tvEmail = (TextView) emailLayout.findViewById(R.id.contactData);
				TextView tvType = (TextView) emailLayout.findViewById(R.id.contactDataType);
				vDivider = inflater.inflate(R.layout.contact_entry_divider, null, false);
				// tvContactType.setText("email");
				tvEmail.setText(email.getData());
				tvType.setText(email.getType());
				contactDataItems.addView(emailLayout);
				contactDataItems.addView(vDivider);
			}
			if (vDivider != null && contact.getPhoneNumbers().size() == 0) {
				contactDataItems.removeView(vDivider);
			}
		}
		
		if (contact.getPhoneNumbers().size() > 0) {
			View vDivider = null;
			for (ContactData number : contact.getPhoneNumbers()) {
				LinearLayout phoneContactLayout = (LinearLayout) inflater.inflate(R.layout.contact_entry_data, null, false);
				phoneContactLayout.setOnClickListener(mClickListener);
				phoneContactLayout.setTag("phone");
				// TextView tvContactType = (TextView) phoneContactLayout.findViewById(R.id.contactType);
				TextView tvPhone = (TextView) phoneContactLayout.findViewById(R.id.contactData);
				TextView tvType = (TextView) phoneContactLayout.findViewById(R.id.contactDataType);
				vDivider = inflater.inflate(R.layout.contact_entry_divider, null, false);
				// tvContactType.setText("phone");
				tvPhone.setText(number.getData());
				tvType.setText(number.getType());
				contactDataItems.addView(phoneContactLayout);
				contactDataItems.addView(vDivider);
			}
			if (vDivider != null) {
				contactDataItems.removeView(vDivider);
			}
		}



		return convertView;
	}
	

	public static class SurespotContactViewHolder {
		public TextView tvName;
		public TextView tvStatus;
		public View vgInvite;
		public View vgActivity;
	}

}
