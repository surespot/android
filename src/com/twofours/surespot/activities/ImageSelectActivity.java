package com.twofours.surespot.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ImageSelectActivity extends SherlockActivity {
	private static final String TAG = "ImageSelectActivity";
	public static final int REQUEST_EXISTING_IMAGE = 1;
	public static final int REQUEST_CAPTURE_IMAGE = 2;

	private ImageView mImageView;
	private Button mOKButton;
	private Button mCancelButton;
	private File mCurrentPhotoPath;
	private int mSource;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);

		final String to = getIntent().getStringExtra("to");

		mImageView = (ImageView) this.findViewById(R.id.image);
		mOKButton = (Button) this.findViewById(R.id.ok);
		mOKButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent dataIntent = new Intent();
				dataIntent.setData(Uri.fromFile(mCurrentPhotoPath));

				// dataIntent.setData(mUri);
				dataIntent.putExtra("to", to);
				dataIntent.putExtra("filename", mCurrentPhotoPath.getPath());
				// if (getParent() == null) {
				setResult(Activity.RESULT_OK, dataIntent);
				// }
				// else {
				// getParent().setResult(Activity.RESULT_OK, data);
				// }
				finish();

			}

		});

		mCancelButton = (Button) this.findViewById(R.id.cancel);
		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				deleteImageFile();
				finish();
			}
		});

		mSource = getIntent().getIntExtra("source", 0);

		switch (mSource) {
		case REQUEST_EXISTING_IMAGE:

			try {
				createImageFile();
			}
			catch (IOException e1) {
				// TODO tell user
				SurespotLog.w(TAG, "selectImage", e1);
				finish();
			}
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			Utils.configureActionBar(this, "select image", to, true);
			startActivityForResult(Intent.createChooser(intent, "select Image"), REQUEST_EXISTING_IMAGE);
			break;

		case REQUEST_CAPTURE_IMAGE:
			Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
			Utils.configureActionBar(this, "capture image", to, true);
			try {
				createImageFile();
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mCurrentPhotoPath));
				startActivityForResult(cameraIntent, REQUEST_CAPTURE_IMAGE);
			}
			catch (IOException e) {
				// TODO tell user
				SurespotLog.w(TAG, "selectImage", e);
				finish();
			}
			break;

		default:
			finish();
		}

	}

	@SuppressWarnings("static-access")
	private void createImageFile() throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp;

		File dir = FileUtils.getImageCaptureDir();
		if (FileUtils.ensureDir(dir)) {
			mCurrentPhotoPath = new File(dir.getPath(), imageFileName);
		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}
	}

	private void deleteImageFile() {
		if (mCurrentPhotoPath != null) {
			mCurrentPhotoPath.delete();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.activity_image_select, menu);
		return true;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			// TODO on thread
			Uri uri = null;
			if (requestCode == REQUEST_CAPTURE_IMAGE) {
				// scale the image down

				uri = Uri.fromFile(mCurrentPhotoPath);
			}
			else {
				uri = data.getData();
			}

			Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(this, uri);

			// save the image to file
			// File file = mCurrentPhotoPath;
			// uri = Uri.fromFile(file);
			try {
				FileOutputStream fos = new FileOutputStream(mCurrentPhotoPath);

				if (bitmap != null) {
					bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
					bitmap = null;
				}
				fos.close();
			}
			catch (IOException e) {
				SurespotLog.w(TAG, "onActivityResult", e);
				deleteImageFile();
				uri = null;
				return;
			}

			mImageView.setImageURI(uri);
		}
		else {
			deleteImageFile();
			finish();
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
