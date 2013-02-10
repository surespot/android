package com.twofours.surespot.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;

public class ImageViewActivity extends SherlockActivity {

	private static final String TAG = "ImageViewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_view);
		Utils.configureActionBar(this, "image", "pan and zoom", true);

		String sjmessage = getIntent().getStringExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE);

		if (sjmessage != null) {
			final SurespotMessage message = SurespotMessage.toSurespotMessage(sjmessage);

			if (message != null) {
				new AsyncTask<Void, Void, Bitmap>() {

					@Override
					protected Bitmap doInBackground(Void... params) {

						// TODO use streaming network get
						String imageData = NetworkController.getFileSync(message.getCipherData());
						if (imageData != null) {
							byte[] output = EncryptionController.symmetricBase64DecryptSync(message.getSpot(), message.getIv(), imageData);
							Bitmap bitmap = BitmapFactory.decodeByteArray(output, 0, output.length);
							return bitmap;
						}
						return null;
					}

					protected void onPostExecute(Bitmap result) {

						ImageView imageView = (ImageView) findViewById(R.id.imageViewer);
						if (result != null) {
							imageView.setImageBitmap(result);
						}
						else {
							finish();
						}

					}

				}.execute();
			}
		}

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
