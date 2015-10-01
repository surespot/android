package com.twofours.surespot.images;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;

import com.actionbarsherlock.app.SherlockActivity;
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
	private ImageViewTouch mImageView;
	private Button mSendButton;
	private Button mCancelButton;
	private ArrayList<File> mCompressedImagePaths;
	private ArrayList<String> mPaths;
	private String mTo;
	private int mSize;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);
		Bundle extras = getIntent().getExtras();

		mImageView = (ImageViewTouch) this.findViewById(R.id.imageViewer);
		mSendButton = (Button) this.findViewById(R.id.send);

		mCancelButton = (Button) this.findViewById(R.id.cancel);

		mSendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendImages();
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
			mPaths = savedInstanceState.getStringArrayList("paths");
			mTo = savedInstanceState.getString("to");
			mSize = savedInstanceState.getInt("size");

			setTitle();
			setButtonText();
			if (mPaths != null && mPaths.size() > 0) {
				mCompressedImagePaths = new ArrayList<File>();
				for (String path : mPaths) {
					mCompressedImagePaths.add(new File(path));
				}
				// TODO: show "and more images..."?
				setImage(BitmapFactory.decodeFile(mPaths.get(mPaths.size() - 1)), true);
			}
		}

		boolean start = getIntent().getBooleanExtra("start", false);
		if (start) {
			getIntent().putExtra("start", false);
			mTo = getIntent().getStringExtra("to");
			mSize = getIntent().getIntExtra("size", IMAGE_SIZE_LARGE);

			setTitle();
			setButtonText();

			// TODO paid version allows any file
			String plural = "";
			Intent intent = new Intent();
			intent.setType("image/*");
			int currentapiVersion = android.os.Build.VERSION.SDK_INT;
			if (currentapiVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				plural = "s";
			}
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image) + plural), SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
		}

	}

	private void sendImages() {
		if (mCompressedImagePaths == null || mCompressedImagePaths.size() == 0) {
            setResult(RESULT_CANCELED);
            finish();
        }
        else {
            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... params) {
                    Intent dataIntent = new Intent();
                    dataIntent.putExtra("to", mTo);
                    dataIntent.setData(Uri.fromFile(mCompressedImagePaths.get(mCompressedImagePaths.size() - 1)));
                    StringBuilder sb = new StringBuilder();
                    for (File file : mCompressedImagePaths) {
                        sb.append(file.toURI().toString());
                        sb.append("~~~~");
                    }
                    dataIntent.putExtra("uris", sb.toString());
                    setResult(Activity.RESULT_OK, dataIntent);
                    finish();
                    return null;
                };

            }.execute();
        }
	}

	private void setTitle() {

		if (mSize == IMAGE_SIZE_LARGE) {
			Utils.configureActionBar(this, getString(R.string.select_image), mTo, false);
		}
		else {
			Utils.configureActionBar(this, getString(R.string.assign_image), mTo, false);
		}

	}

	private void setButtonText() {
		mSendButton.setText(mSize == IMAGE_SIZE_LARGE ? R.string.send : R.string.assign);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:
				mImageView.setVisibility(View.VISIBLE);

				mPaths = new ArrayList<String>();
				new AsyncTask<Void, Void, ArrayList<Bitmap>>() {
					@Override
					protected ArrayList<Bitmap> doInBackground(Void... params) {
						ArrayList<Bitmap> bitmaps = new ArrayList<Bitmap>();

						if (data.getData() != null) {
							Uri uri = (Uri) data.getData();
							// scale, compress and save the image
							BitmapAndFile result = compressImage(uri, -1, -1);

							mPaths.add(result.mFile.toString());
							bitmaps.add(result.mBitmap);
						} else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
							handleMultipleImageSelection(bitmaps, data);
						} else {
							SurespotLog.i(TAG, "Not able to support multiple image selection and no appropriate data returned from image picker");
						}
						return bitmaps;
					}

					protected void onPostExecute(ArrayList<Bitmap> results) {
						if (results != null) {
							if (results.size() == 1) {
								setImage(results, true);
							}
							else {
								// just send them
								sendImages();
							}
						} else {
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

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void handleMultipleImageSelection(ArrayList<Bitmap> bitmaps, Intent data) {
		ClipData clipData = data.getClipData();

		for (int n = 0; n < clipData.getItemCount(); n++) {
            Uri uri = clipData.getItemAt(n).getUri();
            // scale, compress and save the image
            BitmapAndFile result = compressImage(uri, n, -1);
            mPaths.add(result.mFile.toString()); // TODO: is this right?
            bitmaps.add(result.mBitmap);
        }
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
		mImageView.setDisplayType(DisplayType.FIT_TO_SCREEN);
		mImageView.setImageBitmap(bitmap);
		mSendButton.setEnabled(true);

		if (mSize == IMAGE_SIZE_SMALL) {
			mImageView.zoomTo((float) .5, 2000);
		}
	}

	private void setImage(ArrayList<Bitmap> bitmaps, boolean animate) {
		if (animate) {
			Animation fadeIn = new AlphaAnimation(0, 1);
			fadeIn.setDuration(1000);
			mImageView.startAnimation(fadeIn);

		}
		else {
			mImageView.clearAnimation();
		}
		mImageView.setDisplayType(DisplayType.FIT_TO_SCREEN);
		mImageView.setImageBitmap(bitmaps.get(bitmaps.size() - 1));
		mSendButton.setEnabled(true);

		// TODO: show "and x more images..."??
		if (mSize == IMAGE_SIZE_SMALL) {
			mImageView.zoomTo((float) .5, 2000);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putStringArrayList("paths", mPaths);
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
		if (mCompressedImagePaths != null) {
			for (File file : mCompressedImagePaths) {
				file.delete();
			}
			mCompressedImagePaths = null;
		}
	}

	private class BitmapAndFile {
		public File mFile;
		public Bitmap mBitmap;
	}

	private BitmapAndFile compressImage(final Uri uri, int n, final int rotate) {
		final Uri finalUri;
		File f;
		try {
			if (mCompressedImagePaths == null) {
				mCompressedImagePaths = new ArrayList<File>();
			}
			f = createImageFile(COMPRESS_SUFFIX + n);
			// if it's an external image save it first
			if (uri.getScheme().startsWith("http")) {
				FileOutputStream fos = new FileOutputStream(f);
				InputStream is = new URL(uri.toString()).openStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) != -1) {
					fos.write(buffer, 0, len);
				}

				fos.close();
				finalUri = Uri.fromFile(f);
			} else {
				finalUri = uri;
			}
		}
		catch (IOException e1) {
			SurespotLog.w(TAG, e1, "compressImage");
			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					Utils.makeLongToast(ImageSelectActivity.this, getString(R.string.could_not_load_image));
				}
			};

			this.runOnUiThread(runnable);
			setResult(RESULT_CANCELED);
			finish();
			return null;
		}

		// scale, compress and save the image
		int maxDimension = (mSize == IMAGE_SIZE_LARGE ? SurespotConstants.MESSAGE_IMAGE_DIMENSION : SurespotConstants.FRIEND_IMAGE_DIMENSION);

		Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(ImageSelectActivity.this, finalUri, rotate, maxDimension);
		try {

			if (bitmap != null) {
				// SurespotLog.v(TAG, "compressingImage to: " + mCompressedImagePath);
				FileOutputStream fos = new FileOutputStream(f);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
				fos.close();
				mCompressedImagePaths.add(f);

				// SurespotLog.v(TAG, "done compressingImage to: " + mCompressedImagePath);
				BitmapAndFile result = new BitmapAndFile();
				result.mBitmap = bitmap;
				result.mFile = f;
				return result;
			}
			else {
				Runnable runnable = new Runnable() {

					@Override
					public void run() {
						Utils.makeLongToast(ImageSelectActivity.this, getString(R.string.could_not_load_image));
					}
				};

				this.runOnUiThread(runnable);

				setResult(RESULT_CANCELED);
				finish();
				return null;
			}
		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "onActivityResult");
			if (f != null) {
				f.delete();
			}
			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					Utils.makeLongToast(ImageSelectActivity.this, getString(R.string.could_not_load_image));
				}
			};

			this.runOnUiThread(runnable);
			setResult(RESULT_CANCELED);
			finish();
			return null;
		}
	}
}
