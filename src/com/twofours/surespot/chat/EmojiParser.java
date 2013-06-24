package com.twofours.surespot.chat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils.CodePoint;

/**
 * A class for annotating a CharSequence with spans to convert textual emoticons to graphical ones.
 */
public class EmojiParser {
	// Singleton stuff
	private static final String TAG = "EmojiParser";
	private static EmojiParser sInstance;
	private static Random mRandom = new Random();

	public static EmojiParser getInstance() {
		return sInstance;
	}

	public static void init(Context context) {
		sInstance = new EmojiParser(context);
	}

	private final Context mContext;
	private static final String[] mEmojiChars = { "1f604", "1F60A", "1F603", "263A", "1F609", "1F60D", "1F618", "1F61A", "1F601", "1F61C", "1F61D", "1F60F", "1F617",
			"1F619", "1F606", "1F605", "1F635", "1F61B", "1F600", "1F610", "1F633", "1F60C", "1F612", "1F613", "1F614", "1F62E", "1F611", "1F615", "1F634",
			"1F627", "1F62C", "1F626", "2764", "1F46B", "1F46A", "1F46C", "1F491", "1F46d", "1F311", "1F319", "1F317", "1F315", "1F312", "1F31C", "1F31E",
			"1F308", "1F4A9", "1F42B", "1F414", "1F417", "1F40D", "1F40C", "1F40E", "1F428", "1F418", "1F699", "1F697", "1F355", "1F354", "1F359", "1F370" };

	private static final int[] mEmojiRes = { R.drawable.smile, R.drawable.grin, R.drawable.smiley, R.drawable.relaxed, R.drawable.wink, R.drawable.heart_eyes,
			R.drawable.kissing_heart, R.drawable.kissing_closed_eyes, R.drawable.grinning, R.drawable.stuck_out_tongue_winking_eye,
			R.drawable.stuck_out_tongue_closed_eyes, R.drawable.smirk, R.drawable.kissing, R.drawable.kissing_smiling_eyes, R.drawable.laughing,
			R.drawable.sweat_smile, R.drawable.drunk, R.drawable.stuck_out_tongue, R.drawable.satisfied, R.drawable.hushed, R.drawable.flushed,
			R.drawable.relieved, R.drawable.unamused, R.drawable.sweat, R.drawable.worried, R.drawable.open_mouth, R.drawable.expressionless,
			R.drawable.confused, R.drawable.sleeping, R.drawable.anguished, R.drawable.grimacing, R.drawable.frowning, R.drawable.heart,
			R.drawable.couple_holding_hands, R.drawable.family, R.drawable.two_men_holding_hands, R.drawable.couple_with_heart,
			R.drawable.two_women_holding_hands, R.drawable.new_moon, R.drawable.crescent_moon, R.drawable.first_quarter_moon, R.drawable.full_moon,
			R.drawable.waxing_gibbous_moon, R.drawable.moon_with_face, R.drawable.sun_with_face, R.drawable.rainbow_sky, R.drawable.poop,
			R.drawable.bactrian_camel, R.drawable.chicken, R.drawable.boar, R.drawable.snake, R.drawable.snail, R.drawable.horse, R.drawable.koala,
			R.drawable.elephant, R.drawable.rv, R.drawable.car, R.drawable.pizza, R.drawable.hamburger, R.drawable.rice_ball, R.drawable.cake,

	};

	private HashMap<String, Integer> mCodepointToIndex;

	@SuppressLint("DefaultLocale")
	private EmojiParser(Context context) {
		if (mEmojiRes.length != mEmojiChars.length) {
			throw new IllegalStateException("Emoji resource ID/text mismatch");
		}
				
		mContext = context;
		mCodepointToIndex = new HashMap<String, Integer>(mEmojiChars.length);
		
		
		for (int i = 0; i < mEmojiChars.length; i++) {
			mCodepointToIndex.put(("\\u" + mEmojiChars[i]).toLowerCase(), i);
		}			
	}

	public CharSequence getEmojiChar(int position) {
		int codePoint = Integer.parseInt(mEmojiChars[position], 16);
		int end = Character.charCount(codePoint);

		SpannableStringBuilder builder = new SpannableStringBuilder(new String(Character.toChars(codePoint)));
		builder.setSpan(new ImageSpan(mContext, mEmojiRes[position]), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
		if (TextUtils.isEmpty(text)) {
			return null;
		}

		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		StringBuilder suppCps = new StringBuilder();
		// TODO use regex
		// would be nice to use a regex for these wacky characters:
		// http://stackoverflow.com/questions/5409636/java-support-for-non-bmp-unicode-characters-i-e-codepoints-0xffff-in-their
		Iterator<CodePoint> i = ChatUtils.codePoints(text).iterator();
		while (i.hasNext()) {

			CodePoint cp = i.next();

			if (cp.codePoint == 0x2764 || cp.codePoint == 0x263A || Character.isSupplementaryCodePoint(cp.codePoint)) {
				String escapedUnicode = ChatUtils.unicodeEscaped(cp.codePoint);
				suppCps.append(escapedUnicode + (i.hasNext() ? ", " : ""));
				Integer resId = mEmojiRes[getEmojiIndex(escapedUnicode)];

				if (resId != null) {
					builder.setSpan(new ImageSpan(mContext, resId), cp.start, cp.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				else {
					builder.replace(cp.start, cp.end, escapedUnicode);
				}
			}

		}

		// SurespotLog.v(TAG, "decrypted supp unicode chars: %s.", suppCps);
		return builder;
	}

	@SuppressLint("DefaultLocale")
	private int getEmojiIndex(String codepoint) {
		return mCodepointToIndex.get(codepoint.toLowerCase());		
	}

	public int getRandomEmojiResource() {
		return mEmojiRes[mRandom.nextInt(mEmojiRes.length)];
	}
	
	public static int getEmojiResource(int which) {
		return mEmojiRes[which];
	}
	
	public static int getCount() {
		return mEmojiChars.length;
	}
}
