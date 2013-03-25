package com.twofours.surespot;

import java.util.ArrayList;

public class SurespotContact implements Comparable<SurespotContact> {
	private String mId;
	private String mName;
	private ArrayList<ContactData> mEmails;
	private ArrayList<ContactData> mPhoneNumbers;
	
	
	public SurespotContact() {
		mEmails = new ArrayList<ContactData>();
		mPhoneNumbers = new ArrayList<ContactData>();		
	}
	
	
	
	public String getId() {
		return mId;
	}
	public void setId(String id) {
		mId = id;
	}
	public String getName() {
		return mName;
	}
	public void setName(String name) {
		mName = name;
	}
	public ArrayList<ContactData> getEmails() {
		return mEmails;
	}

	public ArrayList<ContactData> getPhoneNumbers() {
		return mPhoneNumbers;
	}



	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mId == null) ? 0 : mId.hashCode());
		return result;
	}



	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SurespotContact))
			return false;
		SurespotContact other = (SurespotContact) obj;
		if (mId == null) {
			if (other.mId != null)
				return false;
		}
		else if (!mId.equals(other.mId))
			return false;
		return true;
	}



	@Override
	public int compareTo(SurespotContact another) {
		return this.getName().compareTo(another.getName());
	}
	
	
	
	public static class ContactData {
		private String mType;
		private String mNumber;
		public String getType() {
			return mType;
		}
		public void setType(String type) {
			mType = type;
		}
		public String getData() {
			return mNumber;
		}
		public void setData(String number) {
			mNumber = number;
		}
		
		
	}
	
	
}
