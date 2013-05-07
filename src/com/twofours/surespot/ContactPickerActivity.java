package com.twofours.surespot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.activities.ExternalInviteActivity;
import com.twofours.surespot.common.Utils;

public class ContactPickerActivity extends SherlockActivity {

	private static final String TAG = "ContactPickerActivity";
	private ListView mContactList;
	private boolean mSelectEmail;
	private ContactListAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contact_picker);
		mContactList = (ListView) findViewById(R.id.contactList);
		mContactList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mAdapter.toggleSelected(position);

			}
		});

		Button bSelectAll = (Button) findViewById(R.id.bSelectAll);
		bSelectAll.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mAdapter.setAllSelected(true);

			}
		});

		Button bSelectNone = (Button) findViewById(R.id.bSelectNone);
		bSelectNone.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mAdapter.setAllSelected(false);

			}
		});

		Button bSelect = (Button) findViewById(R.id.bSelectContacts);
		bSelect.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent dataIntent = new Intent();

				dataIntent.putStringArrayListExtra("data", getSelectedContactData());
				setResult(Activity.RESULT_OK, dataIntent);
				finish();

			}
		});

		int type = 0;
		ArrayList<String> savedPreviouslySelected = null;
		if (savedInstanceState != null) {
			savedPreviouslySelected = savedInstanceState.getStringArrayList("data");
			type = savedInstanceState.getInt("type");
		}
		else {
			type = getIntent().getIntExtra("type", 0);
			if (type != ExternalInviteActivity.SHARE_EMAIL && type != ExternalInviteActivity.SHARE_SMS) {
				finish();
				return;
			}
		}

		mSelectEmail = type == ExternalInviteActivity.SHARE_EMAIL;
		Utils.configureActionBar(this, "select contacts", ExternalInviteActivity.typeToString(type), true);
		populateContactList(savedPreviouslySelected);
	}

	/**
	 * Populate the contact list based on account currently selected in the account spinner.
	 */
	private void populateContactList(ArrayList<String> savedPreviouslySelected) {
		// Build adapter with contact entries

		// Cursor cur = getContactNames();

		// if (cur.getCount() > 0) {
		//
		// while (cur.moveToNext()) {
		// String columns[] = cur.getColumnNames();
		// for (String column : columns) {
		// int index = cur.getColumnIndex(column);
		// SurespotLog.v(TAG, "Column: " + column + " == [" + cur.getString(index) + "]");
		// }
		//
		// String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
		//
		// if (name != null) {
		// SurespotContact contact = new SurespotContact();
		// String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
		// contact.setId(id);
		//
		// contact.setName(name.toLowerCase());
		// contacts.put(id, contact);
		// }
		//
		// }
		// }

		SortedSet<String> previouslySelected = new TreeSet<String>();

		if (savedPreviouslySelected != null) {
			previouslySelected.addAll(savedPreviouslySelected);
		}
		else {
			ArrayList<String> intentPreviouslySelected = getIntent().getStringArrayListExtra("data");
			if (intentPreviouslySelected != null) {
				previouslySelected.addAll(intentPreviouslySelected);
			}
		}

		// cur.close();
		HashMap<String, SurespotContact> contacts = new HashMap<String, SurespotContact>();

		Cursor cur = null;

		if (!mSelectEmail) {
			cur = getContactPhones();

			if (cur.getCount() > 0) {

				while (cur.moveToNext()) {

					String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

					if (name != null) {
						String id = cur.getString(cur.getColumnIndex("contact_id"));
						SurespotContact contact = contacts.get(id);
						if (contact == null) {
							contact = new SurespotContact();
							contact.setId(id);
							contact.setName(name);
							contacts.put(id, contact);
						}

						String number = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
						int type = Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)));
						String label = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL));

						ContactData pNumber = new ContactData();
						pNumber.setData(number);

						String s = (String) Phone.getTypeLabel(this.getResources(), type, label);

						pNumber.setType(s.toLowerCase());
						contact.getPhoneNumbers().add(pNumber);
					}

				}
			}

			cur.close();
		}
		else {
			cur = getContactEmails();
			if (cur.getCount() > 0) {

				while (cur.moveToNext()) {

					// String columns[] = cur.getColumnNames();
					// for (String column : columns) {
					// int index = cur.getColumnIndex(column);
					// SurespotLog.v(TAG, "Column: " + column + " == [" + cur.getString(index) + "]");
					// }

					String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

					if (name != null) {

						String id = cur.getString(cur.getColumnIndex("contact_id"));
						SurespotContact contact = contacts.get(id);

						if (contact == null) {
							contact = new SurespotContact();
							contact.setId(id);
							contact.setName(name);
							contacts.put(id, contact);
						}

						String email = cur.getString(cur.getColumnIndex(ContactsContract.Data.DATA1));
						ContactData cd = new ContactData();
						cd.setData(email);

						String contactTypeString = "email";
						String typeString = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));

						if (typeString != null) {
							int type = Integer.parseInt(typeString);
							String label = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));
							contactTypeString = ((String) Email.getTypeLabel(this.getResources(), type, label)).toLowerCase();
						}
						cd.setType(contactTypeString);
						contact.getEmails().add(cd);

					}
				}
			}
			cur.close();
		}

		ArrayList<ContactData> contactsList = new ArrayList<ContactData>(contacts.size());
		ArrayList<SurespotContact> sortedList = new ArrayList<SurespotContact>(contacts.values());

		Collections.sort(sortedList);
		// don't show contacts with no email or phone
		for (SurespotContact contact : sortedList) {
			if (contact.getEmails().size() > 0 || contact.getPhoneNumbers().size() > 0) {
				// add header
				ContactData header = new ContactData();
				header.setType("header");
				header.setData(contact.getName().toLowerCase());

				contactsList.add(header);

				for (ContactData data : contact.getEmails()) {
					if (previouslySelected != null && previouslySelected.contains(data.getData())) {
						data.setSelected(true);
					}

					contactsList.add(data);
				}

				for (ContactData data : contact.getPhoneNumbers()) {
					if (previouslySelected != null && previouslySelected.contains(data.getData())) {
						data.setSelected(true);
					}

					contactsList.add(data);
				}

			}
		}

		mAdapter = new ContactListAdapter(this, contactsList);
		mContactList.setAdapter(mAdapter);

	}

	private ArrayList<String> getSelectedContactData() {
		//prevent duplicates
		ArrayList<String> selectedEmails = new ArrayList<String>();
		Set<String> emails = new HashSet<String>();
		for (ContactData contact : mAdapter.getContacts()) {
			if (contact.isSelected()) {
				if (emails.add(contact.getData())) {
					selectedEmails.add(contact.getData());
				}
			}
		}

		return selectedEmails;
	}

	/**
	 * Obtains the contact list for the currently selected account.
	 * 
	 * @return A cursor for for accessing the contact list.
	 */
	private Cursor getContactNames() {

		// Run query
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[] { ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME,
				ContactsContract.Contacts.HAS_PHONE_NUMBER };
		String selection = ContactsContract.Contacts.IN_VISIBLE_GROUP + " = ?";
		String[] selectionArgs = new String[] { "1" };
		// String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		ContentResolver cr = getContentResolver();
		return cr.query(uri, projection, selection, selectionArgs, null);
	}

	private Cursor getContactPhones() {

		// Run query
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		String[] projection = new String[] { "contact_id", ContactsContract.Contacts.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE,
				ContactsContract.CommonDataKinds.Phone.LABEL };
		String selection = ContactsContract.Contacts.IN_VISIBLE_GROUP + " = ?";
		String[] selectionArgs = new String[] { "1" };
		// String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		ContentResolver cr = getContentResolver();
		return cr.query(uri, projection, selection, selectionArgs, null);

	}

	private Cursor getContactEmails() {

		// Run query
		Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
		String[] projection = new String[] { "contact_id", ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Data.DATA1,
				ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.LABEL };
		String selection = ContactsContract.Contacts.IN_VISIBLE_GROUP + " = ?";
		String[] selectionArgs = new String[] { "1" };
		// String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		ContentResolver cr = getContentResolver();
		return cr.query(uri, projection, selection, selectionArgs, null);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.contact_picker, menu);
		return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putStringArrayList("data", getSelectedContactData());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}
}
