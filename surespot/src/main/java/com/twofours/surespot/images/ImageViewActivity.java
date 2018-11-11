package com.twofours.surespot.images;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

public class ImageViewActivity extends Activity {

    private static final String TAG = "ImageViewActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UIUtils.setTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image_view);
        Utils.configureActionBar(this, null, getString(R.string.pan_and_zoom), true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        String sjmessage = getIntent().getStringExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE);
        final String ourUsername = getIntent().getStringExtra("ourUsername");

        if (sjmessage != null) {
            final SurespotMessage message = SurespotMessage.toSurespotMessage(sjmessage);

            if (message != null) {
                new AsyncTask<Void, Void, Bitmap>() {

                    @Override
                    protected Bitmap doInBackground(Void... params) {

                        InputStream imageStream = NetworkManager.getNetworkController(ImageViewActivity.this, ourUsername).getFileStream(message.getData());

                        Bitmap bitmap = null;
                        PipedOutputStream out = new PipedOutputStream();
                        PipedInputStream inputStream = null;
                        try {
                            inputStream = new PipedInputStream(out);

                            EncryptionController.runDecryptTask(ImageViewActivity.this, ourUsername, message.getOurVersion(ourUsername), message.getOtherUser(ourUsername), message.getTheirVersion(ourUsername), message.getIv(), message.isHashed(),
                                    new BufferedInputStream(imageStream), out);

                            bitmap = BitmapFactory.decodeStream(inputStream);

                        }
                        catch (IOException e) {
                            SurespotLog.w(TAG, e, "ImageViewActivity");
                        }
                        finally {

//                            try {
//                                if (imageStream != null) {
//                                    imageStream.close();
//                                }
//                            }
//                            catch (IOException e) {
//                                SurespotLog.w(TAG, e, "ImageViewActivity");
//                            }

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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_blank, menu);
        return true;
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
