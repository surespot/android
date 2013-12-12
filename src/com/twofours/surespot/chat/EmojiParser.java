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
import com.twofours.surespot.common.Utils;

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
	private final List<Integer> mEmojiRes;
	private final HashMap<Integer, Object> mUncategorized;
	private HashMap<String, Integer> mCodepointToIndex;
	private int mEmojiCount = 0;

	@SuppressLint("DefaultLocale")
	private EmojiParser(Context context) {

		mContext = context;
		mCodepointToIndex = new HashMap<String, Integer>();
		mEmojiChars = new ArrayList<String>();
		mEmojiRes = new ArrayList<Integer>();
		mUncategorized = new HashMap<Integer, Object>();

		addCharToResMapping("2764", R.drawable.heart);
		mUncategorized.put(0x2764, null);
		addCharToResMapping("1F600", R.drawable.grin);		
		addCharToResMapping("1F603", R.drawable.grinning);
		addCharToResMapping("1F602", R.drawable.face_with_tear_of_joy);
				
		//doesn't look right on ios, remove and remap incoming
		//addCharToResMapping("1F603", R.drawable.smiley);
		mCodepointToIndex.put(("\\u1F601").toLowerCase(), 2);
		addCharToResMapping("1F604", R.drawable.smile);
		addCharToResMapping("1F605", R.drawable.sweat_smile);
		addCharToResMapping("1F606", R.drawable.laughing);
		addCharToResMapping("1F609", R.drawable.wink);
		addCharToResMapping("1F60A", R.drawable.satisfied);
		addCharToResMapping("1F60B", R.drawable.face_savouring_delicious_food);
		//doesn't look right on ios, remove and remap incoming
		mCodepointToIndex.put(("\\u1F60C").toLowerCase(), 13);
		// addCharToResMapping("1F60C", R.drawable.relieved);
		addCharToResMapping("1F60D", R.drawable.heart_eyes);
		addCharToResMapping("1F60F", R.drawable.smirk);
		addCharToResMapping("F0002", R.drawable.blush);
		addCharToResMapping("263A", R.drawable.relaxed);
		mUncategorized.put(0x263A, null);
		addCharToResMapping("F0018", R.drawable.white_smiling_face);

		addCharToResMapping("1F611", R.drawable.expressionless);
		addCharToResMapping("1F612", R.drawable.unamused);
		addCharToResMapping("1F613", R.drawable.sweat);
		addCharToResMapping("1F614", R.drawable.pensive_face);
		addCharToResMapping("1F615", R.drawable.confused);
		addCharToResMapping("1F617", R.drawable.kissing);
		addCharToResMapping("1F618", R.drawable.kissing_heart);
		addCharToResMapping("1F619", R.drawable.kissing_smiling_eyes);
		addCharToResMapping("1F61A", R.drawable.kissing_closed_eyes);
		addCharToResMapping("1F61B", R.drawable.stuck_out_tongue);
		addCharToResMapping("1F61C", R.drawable.stuck_out_tongue_winking_eye);

		addCharToResMapping("1F61D", R.drawable.stuck_out_tongue_closed_eyes);
		addCharToResMapping("1F61E", R.drawable.disappointed_face);
		addCharToResMapping("1F61F", R.drawable.worried);
		addCharToResMapping("F0003", R.drawable.drunk);
		//doesn't look right on ios, remove and remap incoming
		// addCharToResMapping("F0004", R.drawable.wink2);
		mCodepointToIndex.put(("\\uF0004").toLowerCase(), 25);
		addCharToResMapping("F0005", R.drawable.smiling_face);

		addCharToResMapping("1F620", R.drawable.angry_face);
		addCharToResMapping("1F621", R.drawable.pouting_face);
		addCharToResMapping("1F622", R.drawable.crying_face);
		addCharToResMapping("1F623", R.drawable.persevering_face);
		addCharToResMapping("1F624", R.drawable.face_with_look_of_triumph);
		addCharToResMapping("1F625", R.drawable.disappointed_but_relieved_face);
		addCharToResMapping("1F626", R.drawable.frowning);
		addCharToResMapping("1F627", R.drawable.anguished);
		addCharToResMapping("1F628", R.drawable.fearful_face);
		addCharToResMapping("1F629", R.drawable.weary_face);
		addCharToResMapping("1F62C", R.drawable.grimacing);
		addCharToResMapping("1F62D", R.drawable.loudly_crying_face);
		addCharToResMapping("1F62E", R.drawable.open_mouth);
		addCharToResMapping("1F62F", R.drawable.hushed);

		addCharToResMapping("1F630", R.drawable.face_with_open_mouth_and_cold_sweat);
		addCharToResMapping("1F632", R.drawable.astonished_face);
		addCharToResMapping("1F633", R.drawable.flushed);
		addCharToResMapping("1F634", R.drawable.sleeping);
		addCharToResMapping("1F635", R.drawable.dizzy_face);
		addCharToResMapping("1F637", R.drawable.face_with_medical_mask);

		addCharToResMapping("1F638", R.drawable.grinning_cat_face_with_smiling_eyes);
		addCharToResMapping("1F639", R.drawable.cat_face_with_tears_of_joy);
		addCharToResMapping("1F63A", R.drawable.smiling_cat_face_with_open_mouth);
		addCharToResMapping("1F63B", R.drawable.smiling_cat_face_with_heart_shaped_eyes);
		addCharToResMapping("1F63C", R.drawable.cat_face_with_wry_smile);
		addCharToResMapping("1F63D", R.drawable.kissing_cat_face_with_closed_eyes);
		addCharToResMapping("1F63E", R.drawable.pouting_cat_face);
		addCharToResMapping("1F63F", R.drawable.crying_cat_face);
		addCharToResMapping("1F640", R.drawable.weary_cat_face);

		addCharToResMapping("1F648", R.drawable.see_no_evil_monkey);
		addCharToResMapping("1F649", R.drawable.hear_no_evil_monkey);
		addCharToResMapping("1F64A", R.drawable.speak_no_evil_monkey);

		addCharToResMapping("1F645", R.drawable.face_with_no_good_gesture);
		addCharToResMapping("1F646", R.drawable.face_with_ok_gesture);
		addCharToResMapping("1F647", R.drawable.person_bowing_deeply);
		addCharToResMapping("1F64B", R.drawable.happy_person_raising_one_hand);
		addCharToResMapping("1F64C", R.drawable.person_raising_both_hands_in_celebration);
		addCharToResMapping("1F64D", R.drawable.person_frowning);
		addCharToResMapping("1F64E", R.drawable.person_with_pouting_face);
		addCharToResMapping("1F64F", R.drawable.person_with_folded_hands);
		addCharToResMapping("1F483", R.drawable.dancer);
		mCodepointToIndex.put(("\\uF0000").toLowerCase(), 71);
		addCharToResMapping("F0007", R.drawable.ninja);

		addCharToResMapping("1F46A", R.drawable.family);
		addCharToResMapping("1F46B", R.drawable.couple_holding_hands);
		addCharToResMapping("1F491", R.drawable.couple_with_heart);
		addCharToResMapping("F0001", R.drawable.couple_in_love);
		addCharToResMapping("1F46C", R.drawable.two_men_holding_hands);
		addCharToResMapping("F0009", R.drawable.two_men_in_love);
		addCharToResMapping("F0019", R.drawable.two_men_with_heart);
		addCharToResMapping("1F46D", R.drawable.two_women_holding_hands);
		addCharToResMapping("F0016", R.drawable.two_women_in_love);
		addCharToResMapping("F0010", R.drawable.two_women_with_heart);
		addCharToResMapping("F0017", R.drawable.puke_finger);

		addCharToResMapping("1F34A", R.drawable.tangerine);
		addCharToResMapping("1F354", R.drawable.hamburger);
		addCharToResMapping("1F355", R.drawable.pizza);
		addCharToResMapping("1F359", R.drawable.rice_ball);

		addCharToResMapping("1F365", R.drawable.fish_cake_with_swirl_design);
		addCharToResMapping("1F370", R.drawable.cake);
		addCharToResMapping("1F371", R.drawable.bento_box);
		addCharToResMapping("1F379", R.drawable.tropical_drink);
		addCharToResMapping("1F37A", R.drawable.beer_mug);

		addCharToResMapping("2615", R.drawable.hot_beverage);
		mUncategorized.put(0x2615, null);

		addCharToResMapping("1F4A9", R.drawable.poop);
		addCharToResMapping("1F40C", R.drawable.snail);
		addCharToResMapping("1F40D", R.drawable.snake);
		addCharToResMapping("1F40E", R.drawable.horse);

		addCharToResMapping("1F413", R.drawable.bgok);
		addCharToResMapping("1F414", R.drawable.chicken);
		addCharToResMapping("1F417", R.drawable.boar);
		addCharToResMapping("1F418", R.drawable.elephant);
		addCharToResMapping("1F419", R.drawable.octopus);
		addCharToResMapping("F0008", R.drawable.jumping_spider);
		addCharToResMapping("F0015", R.drawable.gentleman_octopus);
		addCharToResMapping("F0013", R.drawable.quoll);
		addCharToResMapping("1F42B", R.drawable.bactrian_camel);
		addCharToResMapping("1F421", R.drawable.blowfish);
		addCharToResMapping("1F423", R.drawable.hatching_chick);
		addCharToResMapping("1F427", R.drawable.penguin);
		addCharToResMapping("1F428", R.drawable.koala);
		addCharToResMapping("1F431", R.drawable.cat_face);
		addCharToResMapping("1F433", R.drawable.spouting_whale);
		addCharToResMapping("1F436", R.drawable.dog_face);
		addCharToResMapping("1F438", R.drawable.frog_face);
		addCharToResMapping("1F43A", R.drawable.wolf_face);
		addCharToResMapping("1F43E", R.drawable.paw_prints);

		addCharToResMapping("1F44A", R.drawable.fisted_hand_sign);
		addCharToResMapping("1F44D", R.drawable.thumbs_up_sign);
		addCharToResMapping("270C", R.drawable.victory_hand);
		mUncategorized.put(0x270C, null);
		addCharToResMapping("1F4AC", R.drawable.speech_balloon);

		addCharToResMapping("1F383", R.drawable.jack_o_lantern);
		addCharToResMapping("1F47B", R.drawable.ghost);
		addCharToResMapping("F0014", R.drawable.monster);
		addCharToResMapping("1F47D", R.drawable.extraterrestrial_alien);
		addCharToResMapping("1F48A", R.drawable.pill);
		addCharToResMapping("1F480", R.drawable.skull);
		addCharToResMapping("1F48E", R.drawable.ruby);

		addCharToResMapping("1F68F", R.drawable.bus_stop);
		addCharToResMapping("F0006", R.drawable.happy_fmc);
		addCharToResMapping("1F697", R.drawable.car);
		addCharToResMapping("1F699", R.drawable.rv);
		addCharToResMapping("1F6A2", R.drawable.ship);
		addCharToResMapping("F0012", R.drawable.ocean_dive_view);
		addCharToResMapping("F0011", R.drawable.scuba_diver);
		addCharToResMapping("2693", R.drawable.anchor);
		mUncategorized.put(0x2693, null);
		addCharToResMapping("1F3AE", R.drawable.video_game);
		addCharToResMapping("1F3A4", R.drawable.microphone);
		addCharToResMapping("1F3B8", R.drawable.guitar);
		addCharToResMapping("1F3BE", R.drawable.tennis_racquet_and_ball);
		addCharToResMapping("1F3C2", R.drawable.snowboarder);
		addCharToResMapping("1F3E9", R.drawable.love_hotel);

		addCharToResMapping("1F300", R.drawable.cyclone);
		addCharToResMapping("1F304", R.drawable.sunrise_over_mountains);
		addCharToResMapping("1F308", R.drawable.rainbow_solid);
		addCharToResMapping("1F30C", R.drawable.milky_way);
		addCharToResMapping("1F31F", R.drawable.glowing_star);
		addCharToResMapping("1F320", R.drawable.shooting_star);

		addCharToResMapping("1F311", R.drawable.new_moon);
		addCharToResMapping("1F314", R.drawable.waxing_gibbous_moon);
		addCharToResMapping("1F313", R.drawable.first_quarter_moon);
		addCharToResMapping("1F315", R.drawable.full_moon);
		addCharToResMapping("1F317", R.drawable.last_quarter_moon);
		addCharToResMapping("1F319", R.drawable.crescent_moon);
		addCharToResMapping("1F31B", R.drawable.moon_with_face);
		addCharToResMapping("1F31E", R.drawable.sun_with_face);

		addCharToResMapping("2600", R.drawable.sun);
		mUncategorized.put(0x2600, null);
		addCharToResMapping("2601", R.drawable.cloud);
		mUncategorized.put(0x2601, null);
		addCharToResMapping("1F5FB", R.drawable.mount_fuji);
		addCharToResMapping("1F335", R.drawable.cactus);
		addCharToResMapping("26A1", R.drawable.high_voltage_sign);
		mUncategorized.put(0x26A1, null);
		addCharToResMapping("1F6B9", R.drawable.mens_symbol);
		addCharToResMapping("267F", R.drawable.wheelchair);
		mUncategorized.put(0x267F, null);
		addCharToResMapping("1F51E", R.drawable.no_one_under_eighteen_symbol);

		addCharToResMapping("2648", R.drawable.aries);
		mUncategorized.put(0x2648, null);
		addCharToResMapping("2649", R.drawable.taurus);
		mUncategorized.put(0x2649, null);
		addCharToResMapping("264A", R.drawable.gemini);
		mUncategorized.put(0x264A, null);
		addCharToResMapping("264B", R.drawable.cancer);
		mUncategorized.put(0x264B, null);
		addCharToResMapping("264C", R.drawable.leo);
		mUncategorized.put(0x264C, null);
		addCharToResMapping("264D", R.drawable.virgo);
		mUncategorized.put(0x264D, null);
		addCharToResMapping("264E", R.drawable.libra);
		mUncategorized.put(0x264E, null);
		addCharToResMapping("264F", R.drawable.scorpius);
		mUncategorized.put(0x264F, null);
		addCharToResMapping("2650", R.drawable.sagittarius);
		mUncategorized.put(0x2650, null);
		addCharToResMapping("2651", R.drawable.capricorn);
		mUncategorized.put(0x2651, null);
		addCharToResMapping("2652", R.drawable.aquarius);
		mUncategorized.put(0x2652, null);
		addCharToResMapping("2653", R.drawable.pisces);
		mUncategorized.put(0x2653, null);

		// // omitted
		// addCharToResMapping("", R.drawable.rainbow_sky);
		// addCharToResMapping("", R.drawable.Kagetsuki);
		// addCharToResMapping("", R.drawable.giorgio);
		// addCharToResMapping("", R.drawable.pistol);
		// addCharToResMapping("", R.drawable.assault_rifle);
		// addCharToResMapping("", R.drawable.circled_ideograph_secret);
		// addCharToResMapping("", R.drawable.palm_pre3);
		// addCharToResMapping("", R.drawable.bowtie);
		// addCharToResMapping("", R.drawable.apple_of_discord);
		// addCharToResMapping("", R.drawable.family_daughters);
		// addCharToResMapping("", R.drawable.happijar);
		// addCharToResMapping("", R.drawable.jumping_spider_red);
		// addCharToResMapping("", R.drawable.ksroom);
		// addCharToResMapping("", R.drawable.cutting_lines);
		// addCharToResMapping("", R.drawable.lambda_chi_alpha);
		// addCharToResMapping("", R.drawable.shit);
		// addCharToResMapping("", R.drawable.rainbow);
		// addCharToResMapping("", R.drawable.pegasus_black);
		// addCharToResMapping("", R.drawable.penguin_chick);

	}

	private void addCharToResMapping(String chars, int id) {
		if (mEmojiChars.contains(chars)) {
			Utils.makeLongToast(mContext, "Emoji list already contains " + chars);
			throw new IllegalArgumentException("list already contains " + chars);
		}
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
		// TODO use regex
		// would be nice to use a regex for these wacky characters:
		// http://stackoverflow.com/questions/5409636/java-support-for-non-bmp-unicode-characters-i-e-codepoints-0xffff-in-their
		Iterator<CodePoint> i = ChatUtils.codePoints(text).iterator();
		while (i.hasNext()) {

			CodePoint cp = i.next();

			if (mUncategorized.containsKey(cp.codePoint) || Character.isSupplementaryCodePoint(cp.codePoint)) {
				String escapedUnicode = ChatUtils.unicodeEscaped(cp.codePoint);
				int index = getEmojiIndex(escapedUnicode);

				if (index >= 0) {
					Integer resId = mEmojiRes.get(index);
					builder.setSpan(new ImageSpan(mContext, resId), cp.start, cp.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}

		// SurespotLog.v(TAG, "decrypted supp unicode chars: %s.", suppCps);
		return builder;
	}

	@SuppressLint("DefaultLocale")
	private int getEmojiIndex(String codepoint) {
		// SurespotLog.v(TAG,"getting index of codepoint: " + codepoint);
		Integer index = mCodepointToIndex.get(codepoint.toLowerCase());
		return (index == null ? -1 : index);
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
