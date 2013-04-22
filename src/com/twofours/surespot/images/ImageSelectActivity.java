package com.twofours.surespot.images;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ImageSelectActivity extends SherlockActivity {
	private static final String TAG = "ImageSelectActivity";
	public static final int SOURCE_EXISTING_IMAGE = 1;
	public static final int IMAGE_SIZE_LARGE = 0;
	public static final int IMAGE_SIZE_SMALL = 1;
	private static final String COMPRESS_SUFFIX = "compress";
	private ImageView mImageView;
	private Button mSendButton;
	private Button mCancelButton;
	private File mCompressedImagePath;
	private String mPath;
	private String mTo;
	private int mSize;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);

		mImageView = (ImageView) this.findViewById(R.id.imageViewer);
		mSendButton = (Button) this.findViewById(R.id.send);
		mCancelButton = (Button) this.findViewById(R.id.cancel);

		

		mSendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AsyncTask<Void, Void, Void>() {
					protected Void doInBackground(Void... params) {
						Intent dataIntent = new Intent();
						dataIntent.setData(Uri.fromFile(mCompressedImagePath));
						dataIntent.putExtra("to", mTo);
						dataIntent.putExtra("filename", mCompressedImagePath.getPath());
						setResult(Activity.RESULT_OK, dataIntent);
						finish();
						return null;
					};

				}.execute();
			}
		});

		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				deleteCompressedImage();
				finish();
			}
		});

		if (savedInstanceState != null) {
			mPath = savedInstanceState.getString("path");
			mTo = savedInstanceState.getString("to");
			mSize = savedInstanceState.getInt("size");
			Utils.configureActionBar(this, getString(R.string.select_image), "send to " + mTo, false);
			
			if (mPath != null) {
				mCompressedImagePath = new File(mPath);
				setImage(BitmapFactory.decodeFile(mPath), true);
				return;
			}
		}

			
		
		
		boolean start = getIntent().getBooleanExtra("start", false);
		if (start) {
			getIntent().putExtra("start", false);
			mTo = getIntent().getStringExtra("to");
			mSize = getIntent().getIntExtra("size", IMAGE_SIZE_LARGE);
			
			Utils.configureActionBar(this, getString(R.string.select_image), "send to " + mTo, false);
			
			// TODO paid version allows any file
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, "select image"),
					SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);

		}

	}

	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:

				mImageView.setVisibility(View.VISIBLE);

				new AsyncTask<Void, Void, Bitmap>() {
					@Override
					protected Bitmap doInBackground(Void... params) {
						Uri uri = data.getData();

						// scale, compress and save the image
						Bitmap bitmap = compressImage(uri, -1);

						mPath = mCompressedImagePath.toString();
						return bitmap;
					}

					protected void onPostExecute(Bitmap result) {
						if (result != null) {
							setImage(result, true);

						}
						else {
							mSendButton.setEnabled(false);
						}
					}
				}.execute();
			}
		}
		else {
			finish();
		}

		// Utils.clearIntent(getIntent());
	}

	private void setImage(Bitmap bitmap, boolean animate) {
		if (animate) {
			Animation fadeIn = new AlphaAnimation(0, 1);
			fadeIn.setDuration(1000);
			mImageView.startAnimation(fadeIn);

		}
		else {
			mImageView.clearAnimation();
		}
		mImageView.setImageBitmap(bitmap);
		mSendButton.setEnabled(true);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("path", mPath);
		outState.putString("to", mTo);
		outState.putInt("size", mSize);
	}

	private synchronized File createImageFile(String suffix) throws IOException {

		// Create a unique image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp + "_" + suffix;

		File dir = FileUtils.getImageCaptureDir(this);
		if (FileUtils.ensureDir(dir)) {
			File file = new File(dir.getPath(), imageFileName);
			file.createNewFile();
			// SurespotLog.v(TAG, "createdFile: " + file.getPath());
			return file;
		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}

	}

	private void deleteCompressedImage() {
		if (mCompressedImagePath != null) {
			// SurespotLog.v(TAG, "deleteCompressedImage: " + mCompressedImagePath.getPath());
			mCompressedImagePath.delete();
			mCompressedImagePath = null;
		}
	}

	private Bitmap compressImage(final Uri uri, final int rotate) {
		final Uri finalUri;
		try {
			if (mCompressedImagePath == null) {
				mCompressedImagePath = createImageFile(COMPRESS_SUFFIX);
			}

			// if it's an external image save it first
			if (uri.getScheme().startsWith("http")) {
				FileOutputStream fos = new FileOutputStream(mCompressedImagePath);
				InputStream is = new URL(uri.toString()).openStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) != -1) {
					fos.write(buffer, 0, len);
				}

				fos.close();
				finalUri = Uri.fromFile(mCompressedImagePath);
			}
			else {
				finalUri = uri;
			}
		}
		catch (IOException e1) {
			SurespotLog.w(TAG, "compressImage", e1);
			Utils.makeLongToast(this, "could not load image");
			finish();
			return null;
		}

		// scale, compress and save the image
		int maxDimension = (mSize == IMAGE_SIZE_LARGE ? SurespotConstants.MESSAGE_IMAGE_DIMENSION : SurespotConstants.FRIEND_IMAGE_DIMENSION);
		
		Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(ImageSelectActivity.this, finalUri, rotate, maxDimension);
		try {

			if (bitmap != null) {
				// SurespotLog.v(TAG, "compressingImage to: " + mCompressedImagePath);
				FileOutputStream fos = new FileOutputStream(mCompressedImagePath);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
				fos.close();
				// SurespotLog.v(TAG, "done compressingImage to: " + mCompressedImagePath);
			}
			else {
				Utils.makeLongToast(this, "could not load image");
				finish();
			}
			return bitmap;
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "onActivityResult", e);
			if (mCompressedImagePath != null) {
				mCompressedImagePath.delete();
				mCompressedImagePath = null;
			}
			return null;
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
