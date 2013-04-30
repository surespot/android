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
		private static final int[] sIconIds = { 
		R.drawable.smile,
		R.drawable.grin,
		R.drawable.smiley,
		R.drawable.relaxed,
		R.drawable.wink,
		R.drawable.heart_eyes,
		R.drawable.kissing_heart,
		R.drawable.kissing_closed_eyes,
		R.drawable.flushed,
		R.drawable.relieved,
		R.drawable.grinning,
		R.drawable.stuck_out_tongue_winking_eye,
		R.drawable.stuck_out_tongue_closed_eyes,
		R.drawable.unamused,
		R.drawable.smirk,
		R.drawable.sweat,
		R.drawable.worried,
		R.drawable.kissing,
		R.drawable.kissing_smiling_eyes,
		R.drawable.laughing,
		R.drawable.hushed,
		R.drawable.open_mouth,
		R.drawable.expressionless,
		R.drawable.confused,
		R.drawable.sleeping,
		R.drawable.anguished,
		R.drawable.sweat_smile,
		R.drawable.grimacing,
		R.drawable.frowning,
		R.drawable.drunk,
		R.drawable.stuck_out_tongue,
		R.drawable.satisfied,
		R.drawable.poop,
		R.drawable.couple_holding_hands,
		R.drawable.family,
		R.drawable.two_men_holding_hands,
		R.drawable.heart,
		R.drawable.couple_with_heart,
		R.drawable.crescent_moon,
		R.drawable.first_quarter_moon,
		R.drawable.full_moon,
		R.drawable.waxing_gibbous_moon,
		R.drawable.moon_with_face,
		R.drawable.sun_with_face,
		R.drawable.bactrian_camel,
		R.drawable.chicken,
		R.drawable.boar,
		R.drawable.snake,
		R.drawable.snail,
		R.drawable.horse,
		R.drawable.new_moon,
		R.drawable.rainbow_sky,
		R.drawable.koala,
		R.drawable.elephant,
		R.drawable.rv,
		R.drawable.car,	
		R.drawable.pizza,
		R.drawable.hamburger,
		R.drawable.rice_ball,
		R.drawable.cake,
		R.drawable.two_women_holding_hands,
		R.drawable.pistol,
		
		
				 };

		public static int SMILING_FACE_WITH_OPEN_MOUTH_AND_SMILING_EYES = 0;
		public static int GRINNING_FACE_WITH_SMILING_EYES = 1;
		public static int SMILING_FACE_WITH_OPEN_MOUTH = 2;
		public static int SMILING_FACE = 3;
		public static int WINKING_FACE = 4;
		public static int SMILING_FACE_WITH_HEART_SHAPED_EYES = 5;
		public static int FACE_THROWING_A_KISS = 6;
		public static int KISSING_FACE_WITH_CLOSE_EYES = 7;
		public static int FLUSHED_FACE = 8;	
		public static int RELIEVED_FACE = 9;
		public static int TOOTHY_FACE_WITH_SMILING_EYES = 10;
		public static int FACE_WITH_STUCK_OUT_TONGUE_AND_WINKING_EYE = 11;
		public static int FACE_WITH_STUCK_OUT_TONGUE_AND_TIGHTLY_CLOSED_EYES = 12;
		public static int UNAMUSED_FACE = 13;
		public static int SMIRKING_FACE = 14;
		public static int FACE_WITH_COLD_SWEAT = 15;
		public static int PENSIVE_FACE = 16;
		public static int KISSING_FACE = 17;
		public static int KISSING_FACE_WITH_SMILING_EYES = 18;
		public static int SMILING_FACE_WITH_OPEN_MOUTH_AND_TIGHTLY_CLOSED_EYES = 19;
		public static int NEUTRAL_FACE = 20;
		public static int FACE_WITH_OPEN_MOUTH = 21;
		public static int EXPRESSIONLESS_FACE = 22;
		public static int CONFUSED_FACE = 23;
		public static int SLEEPING_FACE = 24;
		public static int ANGUISHED_FACE = 25;
		public static int SMILING_FACE_WITH_OPEN_MOUTH_AND_COLD_SWEAT = 26;
		public static int GRIMICING_FACE = 27;
		public static int FROWNING_FACE_WITH_OPEN_MOUTH = 28;
		public static int DIZZY_FACE = 29;
		public static int FACE_WITH_STUCK_OUT_TONGUE = 30;
		public static int GRINNING_FACE = 31;
		public static int DOG_DIRT = 32;
		public static int MAN_AND_WOMAN_HOLDING_HANDS = 33;
		public static int FAMILY = 34;
		public static int TWO_MEN_HOLDING_HANDS = 35;
		public static int RED_HEART = 36;
		public static int COUPLE_WITH_HEART = 37;
		public static int CRESCENT_MOON = 38;
		public static int LAST_QUARTER_MOON_SYMBOL = 39;
		public static int FULL_MOON_SYMBOL = 40;
		public static int WAXING_CRESCENT_MOON_SYMBOL = 41;
		public static int LAST_QUARTER_MOON_WITH_FACE = 42;
		public static int SUN_WITH_FACE = 43;
		public static int BACTRIAN_CAMEL = 44;
		public static int CHICKEN = 45;
		public static int BOAR = 46;
		public static int SNAKE = 47;
		public static int SNAIL = 48;
		public static int HORSE = 49;
		public static int NEW_MOON = 50;
		public static int RAINBOW = 51;
		public static int KOALA = 52;
		public static int ELEPHANT = 53;
		public static int RECREATIONAL_VEHICLE = 54;
		public static int AUTOMOBILE = 55;
		public static int SLICE_OF_PIZZA = 56;
		public static int HAMBURGER = 57;
		public static int RICE_BALL = 58;
		public static int SHORTCAKE = 59;
		public static int TWO_WOMEN_HOLDING_HANDS = 60;
		public static int PISTOL = 61;
		
	

		public static int getEmojiResource(int which) {
			return sIconIds[which];
		}

		public static int getCount() {
			return sIconIds.length;
		}
	}

	// NOTE: if you change anything about this array, you must make the corresponding change in res/values/arrays.xml
	public static final int[] EMOJI_RES_IDS = { 
		Emojis.getEmojiResource(Emojis.SMILING_FACE_WITH_OPEN_MOUTH_AND_SMILING_EYES), // 0
		Emojis.getEmojiResource(Emojis.GRINNING_FACE_WITH_SMILING_EYES), // 1
		Emojis.getEmojiResource(Emojis.SMILING_FACE_WITH_OPEN_MOUTH), // 2
		Emojis.getEmojiResource(Emojis.SMILING_FACE), //3
		Emojis.getEmojiResource(Emojis.WINKING_FACE), //4
		Emojis.getEmojiResource(Emojis.SMILING_FACE_WITH_HEART_SHAPED_EYES), //5
		Emojis.getEmojiResource(Emojis.FACE_THROWING_A_KISS), //6
		Emojis.getEmojiResource(Emojis.KISSING_FACE_WITH_CLOSE_EYES), //7
		Emojis.getEmojiResource(Emojis.FLUSHED_FACE), //8
		Emojis.getEmojiResource(Emojis.RELIEVED_FACE), //9
		Emojis.getEmojiResource(Emojis.TOOTHY_FACE_WITH_SMILING_EYES), //10
		Emojis.getEmojiResource(Emojis.FACE_WITH_STUCK_OUT_TONGUE_AND_WINKING_EYE), //11
		Emojis.getEmojiResource(Emojis.FACE_WITH_STUCK_OUT_TONGUE_AND_TIGHTLY_CLOSED_EYES), //12
		Emojis.getEmojiResource(Emojis.UNAMUSED_FACE), //13
		Emojis.getEmojiResource(Emojis.SMIRKING_FACE), //14
		Emojis.getEmojiResource(Emojis.FACE_WITH_COLD_SWEAT), //15
		Emojis.getEmojiResource(Emojis.PENSIVE_FACE), //16
		Emojis.getEmojiResource(Emojis.KISSING_FACE), //17
		Emojis.getEmojiResource(Emojis.KISSING_FACE_WITH_SMILING_EYES), //18
		Emojis.getEmojiResource(Emojis.SMILING_FACE_WITH_OPEN_MOUTH_AND_TIGHTLY_CLOSED_EYES), //19
		Emojis.getEmojiResource(Emojis.NEUTRAL_FACE), //20
		Emojis.getEmojiResource(Emojis.FACE_WITH_OPEN_MOUTH), //21
		Emojis.getEmojiResource(Emojis.EXPRESSIONLESS_FACE), //22
		Emojis.getEmojiResource(Emojis.CONFUSED_FACE), //23
		Emojis.getEmojiResource(Emojis.SLEEPING_FACE), //24
		Emojis.getEmojiResource(Emojis.ANGUISHED_FACE), //25
		Emojis.getEmojiResource(Emojis.SMILING_FACE_WITH_OPEN_MOUTH_AND_COLD_SWEAT), //26
		Emojis.getEmojiResource(Emojis.GRIMICING_FACE), //27 	
		Emojis.getEmojiResource(Emojis.FROWNING_FACE_WITH_OPEN_MOUTH), //28
		Emojis.getEmojiResource(Emojis.DIZZY_FACE), //29
		Emojis.getEmojiResource(Emojis.FACE_WITH_STUCK_OUT_TONGUE), //30
		Emojis.getEmojiResource(Emojis.GRINNING_FACE), //31
		Emojis.getEmojiResource(Emojis.DOG_DIRT), //32
		Emojis.getEmojiResource(Emojis.MAN_AND_WOMAN_HOLDING_HANDS), //33
		Emojis.getEmojiResource(Emojis.FAMILY), //34
		Emojis.getEmojiResource(Emojis.TWO_MEN_HOLDING_HANDS), //35
		Emojis.getEmojiResource(Emojis.RED_HEART), //36
		Emojis.getEmojiResource(Emojis.COUPLE_WITH_HEART), //37
		Emojis.getEmojiResource(Emojis.CRESCENT_MOON), //38
		Emojis.getEmojiResource(Emojis.LAST_QUARTER_MOON_SYMBOL), //39
		Emojis.getEmojiResource(Emojis.FULL_MOON_SYMBOL), //40
		Emojis.getEmojiResource(Emojis.WAXING_CRESCENT_MOON_SYMBOL), //41
		Emojis.getEmojiResource(Emojis.LAST_QUARTER_MOON_WITH_FACE), //42
		Emojis.getEmojiResource(Emojis.SUN_WITH_FACE), //43
		Emojis.getEmojiResource(Emojis.BACTRIAN_CAMEL), //44
		Emojis.getEmojiResource(Emojis.CHICKEN), //45
		Emojis.getEmojiResource(Emojis.BOAR), //46
		Emojis.getEmojiResource(Emojis.SNAKE), //47
		Emojis.getEmojiResource(Emojis.SNAIL), //48
		Emojis.getEmojiResource(Emojis.HORSE), //49
		Emojis.getEmojiResource(Emojis.NEW_MOON), //50
		Emojis.getEmojiResource(Emojis.RAINBOW), //51
		Emojis.getEmojiResource(Emojis.KOALA), //52
		Emojis.getEmojiResource(Emojis.ELEPHANT), //53
		Emojis.getEmojiResource(Emojis.RECREATIONAL_VEHICLE), //54
		Emojis.getEmojiResource(Emojis.AUTOMOBILE), //55
		Emojis.getEmojiResource(Emojis.SLICE_OF_PIZZA), //56
		Emojis.getEmojiResource(Emojis.HAMBURGER), //57
		Emojis.getEmojiResource(Emojis.RICE_BALL), //58
		Emojis.getEmojiResource(Emojis.SHORTCAKE), //59
		Emojis.getEmojiResource(Emojis.TWO_WOMEN_HOLDING_HANDS), //60
		Emojis.getEmojiResource(Emojis.PISTOL)//61
		
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
					builder.setSpan(new ImageSpan(mContext, resId), cp.start, cp.end-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				else {
					builder.replace(cp.start, cp.end-1, escapedUnicode);
				}
			}

		}

		//SurespotLog.v(TAG, "decrypted supp unicode chars: %s.", suppCps);
		return builder;
	}
}
