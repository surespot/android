package com.twofours.surespot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.Menu;
import android.widget.ListView;

import com.twofours.surespot.SurespotContact.ContactData;

public class ContactPickerActivity extends Activity {

	private static final String TAG = "ContactPickerActivity";
	private ListView mContactList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contact_picker);
		mContactList = (ListView) findViewById(R.id.contactList);

	
		populateContactList();
	}

	/**
	 * Populate the contact list based on account currently selected in the account spinner.
	 */
	private void populateContactList() {
		// Build adapter with contact entries

		Cursor cur = getContactNames();
		HashMap<String, SurespotContact> contacts = new HashMap<String, SurespotContact>(cur.getCount());
		if (cur.getCount() > 0) {

			while (cur.moveToNext()) {
				String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
				String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

				SurespotContact contact = new SurespotContact();
				contact.setId(id);
				contact.setName(name.toLowerCase());

				contacts.put(id, contact);
			}
		}

		cur.close();

		cur = getContactPhones();
		if (cur.getCount() > 0) {

			while (cur.moveToNext()) {

				// String columns[] = cur.getColumnNames();
				// for (String column : columns) {
				// int index = cur.getColumnIndex(column);
				// SurespotLog.v(TAG, "Column: " + column + " == [" + cur.getString(index) + "]");
				String id = cur.getString(cur.getColumnIndex("contact_id"));
				String number = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
				int type = Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)));
				String label = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL));
				SurespotContact foundContact = contacts.get(id);
				ContactData pNumber = new SurespotContact.ContactData();
				pNumber.setData(number);


				String s = (String) Phone.getTypeLabel(this.getResources(), type, label);

				pNumber.setType(s.toLowerCase());
				foundContact.getPhoneNumbers().add(pNumber);


			}
		}

		cur = getContactEmails();
		if (cur.getCount() > 0) {

			while (cur.moveToNext()) {

				// String columns[] = cur.getColumnNames();
				// for (String column : columns) {
				// int index = cur.getColumnIndex(column);
				// SurespotLog.v(TAG, "Column: " + column + " == [" + cur.getString(index) + "]");
				String id = cur.getString(cur.getColumnIndex("contact_id"));
				String email = cur.getString(cur.getColumnIndex(ContactsContract.Data.DATA1));
				int type = Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)));
				String label = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));

				SurespotContact foundContact = contacts.get(id);

				ContactData cd = new ContactData();
				cd.setData(email);
				cd.setType(((String) Email.getTypeLabel(this.getResources(), type, label)).toLowerCase());
				foundContact.getEmails().add(cd);
			}
		}

		ArrayList<SurespotContact> contactsList = new ArrayList<SurespotContact>(contacts.values());

		Collections.sort(contactsList);
		ContactListAdapter adapter = new ContactListAdapter(this, contactsList);
		mContactList.setAdapter(adapter);
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
		String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		ContentResolver cr = getContentResolver();
		return cr.query(uri, projection, selection, selectionArgs, sortOrder);
	}

	private Cursor getContactPhones() {

		// Run query
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
		String[] projection = new String[] { "contact_id", ContactsContract.CommonDataKinds.Phone.NUMBER,
				ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL };
		String selection = ContactsContract.Contacts.IN_VISIBLE_GROUP + " = ?";
		String[] selectionArgs = new String[] { "1" };
		// String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		ContentResolver cr = getContentResolver();
		return cr.query(uri, projection, selection, selectionArgs, null);

	}

	private Cursor getContactEmails() {

		// Run query
		Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
		String[] projection = new String[] { "contact_id", ContactsContract.CommonDataKinds.Email.ADDRESS,
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
		getMenuInflater().inflate(R.menu.contact_picker, menu);
		return true;
	}

}
