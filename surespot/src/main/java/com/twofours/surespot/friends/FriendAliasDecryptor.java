package com.twofours.surespot.friends;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.utils.Utils;

import java.lang.ref.WeakReference;

public class FriendAliasDecryptor {
	private static final String TAG = "FriendAliasDecryptor";
	private static Handler mHandler = new Handler(Looper.getMainLooper());
	private FriendAdapter mFriendAdapter;
	private Context mContext;

	public FriendAliasDecryptor(Context context, FriendAdapter friendAdapter) {
		mContext = context;
		mFriendAdapter = friendAdapter;
	}

	/**
	 * Download the specified image from the Internet and binds it to the provided ImageView. The binding is immediate if the image is found in the cache and
	 * will be done asynchronously otherwise. A null bitmap will be associated to the ImageView if an error occurs.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void decrypt(TextView textView, String ourUsername, Friend friend) {
		if (TextUtils.isEmpty(friend.getAliasPlain())) {
			DecryptionTask task = new DecryptionTask(textView, ourUsername, friend);
			DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);
			textView.setTag(decryptionTaskWrapper);
			SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
		}
		else {
			textView.setText(friend.getAliasPlain());
		}
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
	 */
	private DecryptionTask getDecryptionTask(TextView textView) {
		if (textView != null) {
			Object oDecryptionTaskWrapper = textView.getTag();
			if (oDecryptionTaskWrapper instanceof DecryptionTaskWrapper) {
				DecryptionTaskWrapper decryptionTaskWrapper = (DecryptionTaskWrapper) oDecryptionTaskWrapper;
				return decryptionTaskWrapper.getDecryptionTask();
			}
		}
		return null;
	}

	/**
	 * The actual AsyncTask that will asynchronously download the image.
	 */
	class DecryptionTask implements Runnable {
		private Friend mFriend;
		private String mOurUsername;

		private final WeakReference<TextView> textViewReference;

		public DecryptionTask(TextView textView, String ourUsername, Friend friend) {
			mOurUsername = ourUsername;
			textViewReference = new WeakReference<TextView>(textView);
			mFriend = friend;
		}

		@Override
		public void run() {
			final String plainText = EncryptionController.symmetricDecrypt(mContext, mOurUsername, mFriend.getAliasVersion(), mOurUsername,
					mFriend.getAliasVersion(), mFriend.getAliasIv(), mFriend.isAliasHashed(), mFriend.getAliasData());

			mFriend.setAliasPlain(plainText);
			Utils.putAlias(mContext, mOurUsername, mFriend.getName(), plainText);

			if (textViewReference != null) {

				final TextView textView = textViewReference.get();

				DecryptionTask decryptionTask = getDecryptionTask(textView);
				// Change text only if this process is still associated with it
				if ((this == decryptionTask)) {

					final String finalPlainData = plainText;
					mHandler.post(new Runnable() {

						@Override
						public void run() {
							textView.setText(finalPlainData);
							mFriendAdapter.sort();
							mFriendAdapter.notifyDataSetChanged();
							mFriendAdapter.notifyFriendAliasChanged();
						}
					});
				}
			}
		}
	}

	/**
	 * makes sure that only the last started decrypt process can bind its result, independently of the finish order. </p>
	 */
	class DecryptionTaskWrapper {
		private final WeakReference<DecryptionTask> decryptionTaskReference;

		public DecryptionTaskWrapper(DecryptionTask decryptionTask) {
			decryptionTaskReference = new WeakReference<DecryptionTask>(decryptionTask);
		}

		public DecryptionTask getDecryptionTask() {
			return decryptionTaskReference.get();
		}
	}

}
