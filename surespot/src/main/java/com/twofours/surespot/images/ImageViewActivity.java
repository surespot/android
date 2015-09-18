package com.twofours.surespot.images;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.WindowManager.LayoutParams;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class ImageViewActivity extends SherlockActivity {

	private static final String TAG = "ImageViewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// PROD Gingerbread does not like FLAG_SECURE
		if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.FROYO
				|| android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);
		}

		setContentView(R.layout.activity_image_view);
		Utils.configureActionBar(this, "", getString(R.string.pan_and_zoom), true);

		String sjmessage = getIntent().getStringExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE);

		if (sjmessage != null) {
			final SurespotMessage message = SurespotMessage.toSurespotMessage(sjmessage);

			if (message != null) {
				new AsyncTask<Void, Void, Bitmap>() {

					@Override
					protected Bitmap doInBackground(Void... params) {

						InputStream imageStream = MainActivity.getNetworkController().getFileStream(ImageViewActivity.this, message.getData());

						Bitmap bitmap = null;
						PipedOutputStream out = new PipedOutputStream();
						PipedInputStream inputStream = null;
						try {
							inputStream = new PipedInputStream(out);

							EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(), message.getTheirVersion(), message.getIv(), message.isHashed(),
									new BufferedInputStream(imageStream), out);

							bitmap = BitmapFactory.decodeStream(inputStream);

						}
						catch (IOException e) {
							SurespotLog.w(TAG, e, "ImageViewActivity");
						}
						finally {

							try {
								if (imageStream != null) {
									imageStream.close();
								}
							}
							catch (IOException e) {
								SurespotLog.w(TAG, e, "ImageViewActivity");
							}

							try {
								if (inputStream != null) {
									inputStream.close();
								}
							}
							catch (IOException e) {
								SurespotLog.w(TAG, e, "ImageViewActivity");
							}
						}

						return bitmap;

					}

					protected void onPostExecute(Bitmap result) {

						ImageViewTouch imageView = (ImageViewTouch) findViewById(R.id.imageViewer);
						imageView.setDisplayType(DisplayType.FIT_TO_SCREEN);
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
