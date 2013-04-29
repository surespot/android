package com.twofours.surespot.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class EmojiAdapter extends BaseAdapter {
	private Context mContext;

	public EmojiAdapter(Context context) {
		mContext = context;
	}

	public int getCount() {
		return EmojiParser.Emojis.getCount();
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return 0;
	}

	// create a new ImageView for each item referenced by the Adapter
	public View getView(int position, View convertView, ViewGroup parent) {
		ImageView imageView;
		if (convertView == null) { // if it's not recycled, initialize some attributes
			imageView = new ImageView(mContext);
			imageView.setLayoutParams(new GridView.LayoutParams(64, 64));
			imageView.setAdjustViewBounds(true);
			imageView.setPadding(8, 8, 8, 8);
		}
		else {
			imageView = (ImageView) convertView;
		}

		imageView.setImageResource(EmojiParser.Emojis.getEmojiResource(position));
		return imageView;
	}

	

}
