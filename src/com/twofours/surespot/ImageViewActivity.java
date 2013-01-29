package com.twofours.surespot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;

public class ImageViewActivity extends SherlockActivity {

	private static final String TAG = "ImageViewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_view);

		String sjmessage = getIntent().getStringExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE);

		SurespotMessage message = SurespotMessage.toSurespotMessage(sjmessage);

		// TODO use streaming network get
		String imageData = NetworkController.getFileSync(message.getCipherData());
		InputStream inStream = new ByteArrayInputStream(imageData.getBytes());
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		EncryptionController.symmetricBase64DecryptSync(message.getSpot(), message.getIv(), inStream, outStream);

		Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(outStream.toByteArray()));

		ImageView imageView = (ImageView) findViewById(R.id.imageViewer);
		imageView.setImageBitmap(bitmap);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		View customNav = LayoutInflater.from(this).inflate(R.layout.actionbar_title, null);
		TextView navView = (TextView) customNav.findViewById(R.id.nav);
		TextView userView = (TextView) customNav.findViewById(R.id.user);
		navView.setText("image");
		userView.setText("pan and zoom");
		actionBar.setCustomView(customNav);
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
