package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
	private final List<String> mEmojiChars;

	// private static final String[] mEmojiChars = { "1f604", "1F60A", "1F603", "263A", "1F609", "1F60D", "1F618", "1F61A", "1F601", "1F61C", "1F61D", "1F60F",
	// "1F617",
	// "1F619", "1F606", "1F605", "1F635", "1F61B", "1F600", "1F610", "1F633", "1F60C", "1F612", "1F613", "1F614", "1F62E", "1F611", "1F615", "1F634",
	// "1F627", "1F62C", "1F626", "2764", "1F46B", "1F46A", "1F46C", "1F491", "1F46d", "1F311", "1F319", "1F317", "1F315", "1F312", "1F31C", "1F31E",
	// "1F308", "1F4A9", "1F42B", "1F414", "1F417", "1F40D", "1F40C", "1F40E", "1F428", "1F418", "1F699", "1F697", "1F355", "1F354", "1F359", "1F370" };

	private final List<Integer> mEmojiRes;
	private int mEmojiCount = 0;

	// private static final int[] mEmojiRes = { R.drawable.smile, R.drawable.grin, R.drawable.smiley, R.drawable.relaxed, R.drawable.wink,
	// R.drawable.heart_eyes,
	// R.drawable.kissing_heart, R.drawable.kissing_closed_eyes, R.drawable.grinning, R.drawable.stuck_out_tongue_winking_eye,
	// R.drawable.stuck_out_tongue_closed_eyes, R.drawable.smirk, R.drawable.kissing, R.drawable.kissing_smiling_eyes, R.drawable.laughing,
	// R.drawable.sweat_smile, R.drawable.drunk, R.drawable.stuck_out_tongue, R.drawable.satisfied, R.drawable.hushed, R.drawable.flushed,
	// R.drawable.relieved, R.drawable.unamused, R.drawable.sweat, R.drawable.worried, R.drawable.open_mouth, R.drawable.expressionless,
	// R.drawable.confused, R.drawable.sleeping, R.drawable.anguished, R.drawable.grimacing, R.drawable.frowning, R.drawable.heart,
	// R.drawable.couple_holding_hands, R.drawable.family, R.drawable.two_men_holding_hands, R.drawable.couple_with_heart,
	// R.drawable.two_women_holding_hands, R.drawable.new_moon, R.drawable.crescent_moon, R.drawable.first_quarter_moon, R.drawable.full_moon,
	// R.drawable.waxing_gibbous_moon, R.drawable.moon_with_face, R.drawable.sun_with_face, R.drawable.rainbow_sky, R.drawable.poop,
	// R.drawable.bactrian_camel, R.drawable.chicken, R.drawable.boar, R.drawable.snake, R.drawable.snail, R.drawable.horse, R.drawable.koala,
	// R.drawable.elephant, R.drawable.rv, R.drawable.car, R.drawable.pizza, R.drawable.hamburger, R.drawable.rice_ball, R.drawable.cake,
	//
	// };

	private HashMap<String, Integer> mCodepointToIndex;

	@SuppressLint("DefaultLocale")
	private EmojiParser(Context context) {

		mContext = context;
		mCodepointToIndex = new HashMap<String, Integer>();
		mEmojiChars = new ArrayList<String>();
		mEmojiRes = new ArrayList<Integer>();

		addCharToResMapping("1F604", R.drawable.smile);
		addCharToResMapping("1F60A", R.drawable.grin);
		addCharToResMapping("1F603", R.drawable.smiley);
		addCharToResMapping("263A", R.drawable.relaxed);
		addCharToResMapping("1F609", R.drawable.wink);
		addCharToResMapping("1F60D", R.drawable.heart_eyes);
		addCharToResMapping("1F618", R.drawable.kissing_heart);
		addCharToResMapping("1F61A", R.drawable.kissing_closed_eyes);
		addCharToResMapping("1F601", R.drawable.grinning);
		addCharToResMapping("1F61C", R.drawable.stuck_out_tongue_winking_eye);
		addCharToResMapping("1F61D", R.drawable.stuck_out_tongue_closed_eyes);
		addCharToResMapping("1F60F", R.drawable.smirk);
		addCharToResMapping("1F617", R.drawable.kissing);
		addCharToResMapping("1F619", R.drawable.kissing_smiling_eyes);
		addCharToResMapping("1F606", R.drawable.laughing);
		addCharToResMapping("1F605", R.drawable.sweat_smile);
		addCharToResMapping("1F635", R.drawable.drunk);
		addCharToResMapping("1F61B", R.drawable.stuck_out_tongue);
		addCharToResMapping("1F600", R.drawable.satisfied);
		addCharToResMapping("1F610", R.drawable.hushed);
		addCharToResMapping("1F633", R.drawable.flushed);
		addCharToResMapping("1F60C", R.drawable.relieved);
		addCharToResMapping("1F612", R.drawable.unamused);
		addCharToResMapping("1F613", R.drawable.sweat);
		addCharToResMapping("1F614", R.drawable.worried);
		addCharToResMapping("1F62E", R.drawable.open_mouth);
		addCharToResMapping("1F611", R.drawable.expressionless);
		addCharToResMapping("1F615", R.drawable.confused);
		addCharToResMapping("1F634", R.drawable.sleeping);
		addCharToResMapping("1F627", R.drawable.anguished);
		addCharToResMapping("1F62C", R.drawable.grimacing);
		addCharToResMapping("1F626", R.drawable.frowning);
		addCharToResMapping("2764", R.drawable.heart);
		addCharToResMapping("1F46B", R.drawable.couple_holding_hands);
		addCharToResMapping("1F46A", R.drawable.family);
		addCharToResMapping("1F46C", R.drawable.two_men_holding_hands);
		addCharToResMapping("1F491", R.drawable.couple_with_heart);
		addCharToResMapping("1F46d", R.drawable.two_women_holding_hands);
		addCharToResMapping("1F311", R.drawable.new_moon);
		addCharToResMapping("1F319", R.drawable.crescent_moon);
		addCharToResMapping("1F317", R.drawable.first_quarter_moon);
		addCharToResMapping("1F315", R.drawable.full_moon);
		addCharToResMapping("1F312", R.drawable.waxing_gibbous_moon);
		addCharToResMapping("1F31C", R.drawable.moon_with_face);
		addCharToResMapping("1F31E", R.drawable.sun_with_face);
		addCharToResMapping("1F308", R.drawable.rainbow_sky);
		addCharToResMapping("1F4A9", R.drawable.poop);
		addCharToResMapping("1F42B", R.drawable.bactrian_camel);
		addCharToResMapping("1F414", R.drawable.chicken);
		addCharToResMapping("1F417", R.drawable.boar);
		addCharToResMapping("1F40D", R.drawable.snake);
		addCharToResMapping("1F40C", R.drawable.snail);
		addCharToResMapping("1F40E", R.drawable.horse);
		addCharToResMapping("1F428", R.drawable.koala);
		addCharToResMapping("1F418", R.drawable.elephant);
		addCharToResMapping("1F699", R.drawable.rv);
		addCharToResMapping("1F697", R.drawable.car);
		addCharToResMapping("1F355", R.drawable.pizza);
		addCharToResMapping("1F354", R.drawable.hamburger);
		addCharToResMapping("1F359", R.drawable.rice_ball);
		addCharToResMapping("1F370", R.drawable.cake);

	}

	private void addCharToResMapping(String chars, int id) {
		mEmojiChars.add(chars);
		mEmojiRes.add(id);
		mCodepointToIndex.put(("\\u" + chars).toLowerCase(), mEmojiCount++);

	}

	public CharSequence getEmojiChar(int position) {
		int codePoint = Integer.parseInt(mEmojiChars.get(position), 16);
		int end = Character.charCount(codePoint);

		SpannableStringBuilder builder = new SpannableStringBuilder(new String(Character.toChars(codePoint)));
		builder.setSpan(new ImageSpan(mContext, mEmojiRes.get(position)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
				Integer resId = mEmojiRes.get(getEmojiIndex(escapedUnicode));

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
		return mEmojiRes.get(mRandom.nextInt(mEmojiCount));
	}

	public int getEmojiResource(int which) {
		return mEmojiRes.get(which);
	}

	public int getCount() {
		return mEmojiCount;
	}
}
