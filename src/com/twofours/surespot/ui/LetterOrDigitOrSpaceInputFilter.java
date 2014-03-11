package com.twofours.surespot.ui;

import android.text.InputFilter;
import android.text.Spanned;

public class LetterOrDigitOrSpaceInputFilter implements InputFilter {
	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		for (int i = start; i < end; i++) {
			char c = source.charAt(i);
			if (!Character.isLetterOrDigit(c) && !Character.isSpaceChar(c) ) { return ""; }
		}
		return null;
	}
}
