package com.twofours.surespot;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import com.twofours.surespot.common.SurespotLog;

public class ImageSelectActivity extends Activity {
	private static final int CAMERA_REQUEST = 1888;
	private static final String TAG = "ImageSelectActivity";

	private ImageView mImageView;
	private Button mOKButton;
	private Button mCancelButton;
	private String mCurrentPhotoPath;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);
		mImageView = (ImageView) this.findViewById(R.id.image);
		mOKButton = (Button) this.findViewById(R.id.ok);
		mOKButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent dataIntent = new Intent();
				dataIntent.setData(Uri.fromFile(new File(mCurrentPhotoPath)));
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
				finish();
			}
		});

		Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

		try {
			cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(createImageFile()));
			startActivityForResult(cameraIntent, CAMERA_REQUEST);
		}
		catch (IOException e) {
			// TODO tell user
			SurespotLog.w(TAG, "selectImage", e);
			finish();
		}

	}

	private File createImageFile() throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "surespot" + timeStamp + "_";
		File image = File.createTempFile(imageFileName, "jpg", getAlbumDir());
		mCurrentPhotoPath = image.getAbsolutePath();
		return image;
	}

	private File getAlbumDir() {
		File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		return storageDir;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_image_select, menu);
		return true;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {

			mImageView.setImageURI(Uri.fromFile(new File(mCurrentPhotoPath)));

		}
		else {
			finish();
		}
	}
}
