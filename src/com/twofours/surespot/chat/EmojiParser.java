/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twofours.surespot.chat;

import java.util.HashMap;
import java.util.Iterator;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils.CodePoint;
import com.twofours.surespot.common.SurespotLog;

/**
 * A class for annotating a CharSequence with spans to convert textual emoticons to graphical ones.
 */
public class EmojiParser {
	// Singleton stuff
	private static EmojiParser sInstance;

	public static EmojiParser getInstance() {
		return sInstance;
	}

	public static void init(Context context) {
		sInstance = new EmojiParser(context);
	}

	private final Context mContext;
	private final String[] mEmojiChars;

	private final HashMap<String, Integer> mEmojiCharToRes;

	private EmojiParser(Context context) {
		mContext = context;
		mEmojiChars = mContext.getResources().getStringArray(EMOJI_CHARS);
		mEmojiCharToRes = buildEmojiCharToRes();
		// mPattern = buildPattern();
	}

	static class Emojis {
		private static final int[] sIconIds = { R.drawable.stuck_out_tongue_winking_eye, R.drawable.stuck_out_tongue_closed_eyes,
				R.drawable.stuck_out_tongue, R.drawable.grinning, R.drawable.anguished };

		public static int STUCK_OUT_TONGUE_WINKING_EYE = 0;
		public static int STUCK_OUT_TONGUE_CLOSED_EYES = 1;
		public static int STUCK_OUT_TONGUE = 2;
		public static int GRINNING_FACE_WITH_SMILING_EYES = 3;
		public static int FACE_STREAMING_IN_FEAR = 4;

		public static int getEmojiResource(int which) {
			return sIconIds[which];
		}

		public static int getCount() {
			return sIconIds.length;
		}
	}

	// NOTE: if you change anything about this array, you must make the corresponding change in res/values/arrays.xml
	public static final int[] EMOJI_RES_IDS = { Emojis.getEmojiResource(Emojis.STUCK_OUT_TONGUE_WINKING_EYE), // 0
			Emojis.getEmojiResource(Emojis.STUCK_OUT_TONGUE_CLOSED_EYES), // 1
			Emojis.getEmojiResource(Emojis.STUCK_OUT_TONGUE), // 2
			Emojis.getEmojiResource(Emojis.GRINNING_FACE_WITH_SMILING_EYES), // 3
			Emojis.getEmojiResource(Emojis.FACE_STREAMING_IN_FEAR), // 4

	};

	public static final int EMOJI_CHARS = R.array.emoji_unicode_char;
	public static final int EMOJI_NAMES = R.array.emoji_names;
	private static final String TAG = null;

	private HashMap<String, Integer> buildEmojiCharToRes() {
		if (EMOJI_RES_IDS.length != mEmojiChars.length) {
			// Throw an exception if someone updated EMOJI_RES_IDS
			// and failed to update arrays.xml
			throw new IllegalStateException("Emoji resource ID/text mismatch");
		}

		HashMap<String, Integer> emojiCharToRes = new HashMap<String, Integer>(mEmojiChars.length);
		for (int i = 0; i < mEmojiChars.length; i++) {
			emojiCharToRes.put("\\u" + mEmojiChars[i], EMOJI_RES_IDS[i]);
		}

		return emojiCharToRes;
	}

	public CharSequence getEmojiChar(int position) {
		int codePoint = Integer.parseInt(mEmojiChars[position], 16);
		int end = Character.charCount(codePoint);

		SpannableStringBuilder builder = new SpannableStringBuilder(new String(Character.toChars(codePoint)));
		builder.setSpan(new ImageSpan(mContext, EMOJI_RES_IDS[position]), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		return builder;
	}

	/**
	 * Adds ImageSpans to a CharSequence that replace textual emoticons such as :-) with a graphical version.
	 * 
	 * @param text
	 *            A CharSequence possibly containing emoticons
	 * @return A CharSequence annotated with ImageSpans covering any recognized emoticons.
	 */
	public CharSequence addEmojiSpans(String text) {

		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		StringBuilder suppCps = new StringBuilder();
		// TODO use regex
		// would be nice to use a regex for these wacky characters:
		// http://stackoverflow.com/questions/5409636/java-support-for-non-bmp-unicode-characters-i-e-codepoints-0xffff-in-their
		Iterator<CodePoint> i = ChatUtils.codePoints(text).iterator();
		while (i.hasNext()) {

			CodePoint cp = i.next();
			if (Character.isSupplementaryCodePoint(cp.codePoint)) {
				String escapedUnicode = ChatUtils.unicodeEscaped(cp.codePoint);
				suppCps.append(escapedUnicode + (i.hasNext() ? ", " : ""));
				Integer resId = mEmojiCharToRes.get(escapedUnicode);

				if (resId != null) {
					builder.setSpan(new ImageSpan(mContext, resId), cp.start, cp.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				else {
					builder.replace(cp.start, cp.end - 1, escapedUnicode);
				}
			}

		}

		SurespotLog.v(TAG, "decrypted supp unicode chars: %s.", suppCps);
		return builder;
	}
}
