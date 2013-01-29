package com.twofours.surespot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ImageView;

import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;

public class ImageViewActivity extends Activity {

	private static final String TAG = "ImageViewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_view);
		
		String sjmessage = getIntent().getStringExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE);
		

		SurespotMessage message = SurespotMessage.toSurespotMessage(sjmessage);
		
		
		
		//TODO use streaming network get
		String imageData = NetworkController.getFileSync(message.getCipherData());
		InputStream inStream = new ByteArrayInputStream(imageData.getBytes());
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		EncryptionController.symmetricBase64DecryptSync(message.getSpot(), message.getIv(), inStream, outStream);

		Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(outStream.toByteArray()));
		
		ImageView imageView = (ImageView) findViewById(R.id.imageViewer);
		imageView.setImageBitmap(bitmap);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_image_view, menu);
		return true;
	}

}
