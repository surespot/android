package com.twofours.surespot.images;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

public class ImageSelectActivity extends Activity {
    private static final String TAG = "ImageSelectActivity";
    public static final int SOURCE_EXISTING_IMAGE = 1;
    public static final int IMAGE_SIZE_LARGE = 0;
    public static final int IMAGE_SIZE_SMALL = 1;
    private static final String COMPRESS_SUFFIX = "compress";
    private ImageViewTouch mImageView;
    private Button mSendButton;
    private Button mCancelButton;
    private File mPath;
    private String mTo;
    private String mFrom;
    private String mToAlias;
    private int mSize;
    private boolean mFriendImage;
    private RelativeLayout mFrame;
    private LinearLayout mButtonFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean black = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.BLACK);
        this.setTheme(black ? R.style.TranslucentBlack : R.style.TranslucentDefault);
        SurespotLog.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_select);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        mImageView = (ImageViewTouch) this.findViewById(R.id.imageViewer);
        mSendButton = (Button) this.findViewById(R.id.send);
        mCancelButton = (Button) this.findViewById(R.id.cancel);
        mButtonFrame = (LinearLayout) this.findViewById(R.id.buttonFrame);
        mFrame = (RelativeLayout) this.findViewById(R.id.frame);
        mButtonFrame.setVisibility(View.GONE);

        getActionBar().hide();

        mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendImage();
                finish();
            }
        });

        mCancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                deleteCompressedImage();
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        if (savedInstanceState != null) {
            mTo = savedInstanceState.getString("to");
            mToAlias = savedInstanceState.getString("toAlias");
            mFrom = savedInstanceState.getString("from");
            mSize = savedInstanceState.getInt("size");
            mFriendImage = savedInstanceState.getBoolean("friendImage");
            String path = savedInstanceState.getString("path");
            if (!TextUtils.isEmpty(path)) {
                mPath = new File(path);
                setImage(Uri.fromFile(mPath), true);
            }

            setTitle();
            setButtonText();
        }

        boolean start = getIntent().getBooleanExtra("start", false);
        if (start) {
            getIntent().putExtra("start", false);
            mTo = getIntent().getStringExtra("to");
            mToAlias = getIntent().getStringExtra("toAlias");
            mFrom = getIntent().getStringExtra("from");
            mSize = getIntent().getIntExtra("size", IMAGE_SIZE_LARGE);
            mFriendImage = getIntent().getBooleanExtra("friendImage", false);

            setTitle();
            setButtonText();

            // TODO paid version allows any file
            String plural = "";
            Intent intent = new Intent();
            intent.setType("image/*");
            int currentapiVersion = android.os.Build.VERSION.SDK_INT;
            //if we can select multiple, and we're not selecting a (single) friend image
            if (currentapiVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2 && !mFriendImage) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                plural = "s";
            }
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.setAction(Intent.ACTION_GET_CONTENT);
            SurespotLog.d(TAG, "startActivityForResult, friendImage: %b", mFriendImage);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image) + plural), SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
        }

    }

    private void setTitle() {

        if (mSize == IMAGE_SIZE_LARGE) {
            Utils.configureActionBar(this, getString(R.string.select_image), mToAlias, false);
        } else {
            Utils.configureActionBar(this, getString(R.string.assign_image), mToAlias, false);
        }


    }

    private void setButtonText() {
        mSendButton.setText(mSize == IMAGE_SIZE_LARGE ? R.string.send : R.string.assign);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        SurespotLog.d(TAG, "onActivityResult, requestCode: %d, friendImage: %b", requestCode, mFriendImage);
        if (resultCode == RESULT_OK) {
            if (requestCode == SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE) {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && !mFriendImage && data.getClipData() != null) {
                    handleMultipleImageSelection(data);
                } else if (data.getData() != null) {

                    Uri uri = data.getData();
                    if (!setImage(uri, true)) {
                        Utils.makeLongToast(ImageSelectActivity.this, getString(R.string.could_not_select_image));
                        finish();
                    }
                } else {
                    SurespotLog.i(TAG, "Not able to support multiple image selection and no appropriate data returned from image picker");
                    Utils.makeLongToast(ImageSelectActivity.this, getString(R.string.could_not_select_image));
                    finish();
                }
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private void sendImage() {
        if (mFriendImage) {
            Intent dataIntent = new Intent();
            dataIntent.putExtra("to", mTo);
            dataIntent.setData(Uri.fromFile(mPath));
            setResult(Activity.RESULT_OK, dataIntent);
        } else {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    ChatController cc = ChatManager.getChatController(mFrom);
                    if (cc == null) {
                        //TODO notify user?
                        return null;
                    }
                    ChatUtils.uploadPictureMessageAsync(
                            ImageSelectActivity.this,
                            cc,
                            Uri.fromFile(mPath),
                            mFrom,
                            mTo,
                            false);
                    return null;
                }
            }.execute();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    //returns true if the images were sent
    private void handleMultipleImageSelection(final Intent data) {
        final ClipData clipData = data.getClipData();
        final int itemCount = clipData.getItemCount();

        //handle single image selection
        if (itemCount == 1) {
            Uri uri = clipData.getItemAt(0).getUri();
            if (!setImage(uri, true)) {
                Utils.makeLongToast(ImageSelectActivity.this, String.format(getString(R.string.could_not_select_image)));
                finish();
            }

            return;
        }

        new AsyncTask<Void, Void, Integer>() {
            //returns < 0 if finish
            //0 if handled
            //or > 1 for images not handled
            @Override
            protected Integer doInBackground(Void... params) {
                int errorCount = 0;

                ChatController cc = ChatManager.getChatController(mFrom);
                if (cc == null) {
                    errorCount = itemCount;
                    return errorCount;
                }

                for (int n = 0; n < itemCount; n++) {
                    Uri uri = clipData.getItemAt(n).getUri();
                    // scale, compress and save the image
                    BitmapAndFile result = compressImage(uri, n, -1);
                    if (result != null) {
                        ChatUtils.uploadPictureMessageAsync(
                                ImageSelectActivity.this,
                                cc,
                                Uri.fromFile(result.mFile),
                                mFrom,
                                mTo,
                                false);
                    } else {
                        errorCount++;
                    }
                }
                return errorCount;
            }

            @Override
            protected void onPostExecute(Integer errorCount) {
                if (errorCount > 0) {
                    Utils.makeLongToast(ImageSelectActivity.this, String.format(getString(R.string.did_not_send_x_images), errorCount, itemCount));
                }
                finish();
            }
        }.execute();
    }

    private boolean setImage(Uri uri, boolean animate) {
        SurespotLog.d(TAG, "setImage");

        //  mImageView.setVisibility(View.VISIBLE);
        // mFrame.setVisibility(View.VISIBLE);
        getActionBar().show();
        mFrame.setBackgroundColor(ContextCompat.getColor(this, R.color.background_holo_dark));
        mButtonFrame.setVisibility(View.VISIBLE);
        boolean black = true;//Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.BLACK);
        if (!black) {
            //mSendButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_holo_light));
            //mSendButton.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.drawable.btn_default));
        }

        //     getActionBar().setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.drawable.screen_background_dark));
        //   }
        //   else {
        //        mFrame.setBackgroundColor(ContextCompat.getColor(this, R.color.background_holo_light));
        //getActionBar().setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.drawable.title_bar));
        //      }

        // scale, compress and save the image
        BitmapAndFile result = compressImage(uri, -1, -1);
        if (result == null) {
            return false;
        }

        if (animate) {
            Animation fadeIn = new AlphaAnimation(0, 1);
            fadeIn.setDuration(1000);
            mImageView.startAnimation(fadeIn);

        } else {
            mImageView.clearAnimation();
        }
        mImageView.setDisplayType(DisplayType.FIT_TO_SCREEN);
        mImageView.setImageBitmap(result.mBitmap);
        mSendButton.setEnabled(true);

        if (mSize == IMAGE_SIZE_SMALL) {
            mImageView.zoomTo((float) .5, 2000);
        }
        mPath = result.mFile;

        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPath != null) {
            outState.putString("path", mPath.getAbsolutePath());
        }
        outState.putString("to", mTo);
        outState.putString("toAlias", mToAlias);
        outState.putString("from", mFrom);
        outState.putInt("size", mSize);
        outState.putBoolean("friendImage", mFriendImage);
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
        } else {
            throw new IOException("Could not create image temp file dir: " + dir.getPath());
        }

    }

    private void deleteCompressedImage() {
        if (mPath != null) {
            mPath.delete();
            mPath = null;
        }
    }


    private class BitmapAndFile {
        public File mFile;
        public Bitmap mBitmap;
    }

    private BitmapAndFile compressImage(final Uri uri, int n, final int rotate) {
        final Uri finalUri;
        File f = null;
        try {

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
        } catch (IOException e1) {
            SurespotLog.w(TAG, e1, "compressImage");
            if (f != null) {
                f.delete();
            }
            return null;
        }

        // scale, compress and save the image
        int maxDimension = (mSize == IMAGE_SIZE_LARGE ? SurespotConfiguration.MESSAGE_IMAGE_DIMENSION : SurespotConfiguration.FRIEND_IMAGE_DIMENSION);

        Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(ImageSelectActivity.this, finalUri, rotate, maxDimension);
        try {

            if (bitmap != null) {
                // SurespotLog.v(TAG, "compressingImage to: " + mCompressedImagePath);
                FileOutputStream fos = new FileOutputStream(f);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
                fos.close();

                // SurespotLog.v(TAG, "done compressingImage to: " + mCompressedImagePath);
                BitmapAndFile result = new BitmapAndFile();
                result.mBitmap = bitmap;
                result.mFile = f;
                return result;
            } else {
                if (f != null) {
                    f.delete();
                }
                return null;
            }
        } catch (IOException e) {
            SurespotLog.w(TAG, e, "onActivityResult");
            if (f != null) {
                f.delete();
            }
            return null;
        }
    }
}
