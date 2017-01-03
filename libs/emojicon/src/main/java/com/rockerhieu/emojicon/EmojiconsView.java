/*
 * Copyright 2015 Hieu Rocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rockerhieu.emojicon;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.rockerhieu.emojicon.emoji.Cars;
import com.rockerhieu.emojicon.emoji.Electronics;
import com.rockerhieu.emojicon.emoji.Emojicon;
import com.rockerhieu.emojicon.emoji.Food;
import com.rockerhieu.emojicon.emoji.Nature;
import com.rockerhieu.emojicon.emoji.People;
import com.rockerhieu.emojicon.emoji.Sport;
import com.rockerhieu.emojicon.emoji.Symbols;

import java.util.Arrays;
import java.util.List;

/**
 * @author Daniele Ricci (daniele.athome@gmail.com).
 */
public class EmojiconsView extends RelativeLayout implements ViewPager.OnPageChangeListener, EmojiconRecents {
    private OnEmojiconBackspaceClickedListener mOnEmojiconBackspaceClickedListener;
    private int mEmojiTabLastSelectedIndex = -1;
    private ImageView[] mEmojiTabs;
    private EmojisPagerAdapter mEmojisAdapter;
    private EmojiconRecentsManager mRecentsManager;
    private boolean mUseSystemDefault = false;
    private int mSelectedMask;
    private int mUnselectedMask;

    public EmojiconsView(Context context) {
        super(context);
    }

    public EmojiconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public EmojiconsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EmojiconsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final ViewPager emojisPager = (ViewPager) findViewById(R.id.emojis_pager);
        emojisPager.setOnPageChangeListener(this);
        // we handle recents
        EmojiconRecents recents = this;

        SharedPreferences settings = this.getContext().getSharedPreferences("surespot_preferences", android.content.Context.MODE_PRIVATE);
        boolean black = settings.getBoolean("pref_black", false);
        mSelectedMask = ContextCompat.getColor(getContext(), R.color.selectedMask);
        mUnselectedMask = ContextCompat.getColor(getContext(), black ? R.color.unselectedMaskDark : R.color.unselectedMaskLight);
        setBackgroundColor(ContextCompat.getColor(getContext(), black ? R.color.emojiBackgroundDark : R.color.emojiBackgroundLight));

        mEmojisAdapter = new EmojisPagerAdapter(Arrays.asList(
                EmojiconRecentsGridView.newInstance(getContext(), this, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, People.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Nature.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Food.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Sport.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Cars.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Electronics.DATA, recents, mUseSystemDefault),
                EmojiconGridView.newInstance(getContext(), this, Symbols.DATA, recents, mUseSystemDefault)
        ));
        emojisPager.setAdapter(mEmojisAdapter);

        mEmojiTabs = new ImageView[8];
        mEmojiTabs[0] = (ImageView) findViewById(R.id.emojis_tab_0_recents);
        mEmojiTabs[1] = (ImageView) findViewById(R.id.emojis_tab_1_people);
        mEmojiTabs[2] = (ImageView) findViewById(R.id.emojis_tab_2_nature);
        mEmojiTabs[3] = (ImageView) findViewById(R.id.emojis_tab_3_food);
        mEmojiTabs[4] = (ImageView) findViewById(R.id.emojis_tab_4_sport);
        mEmojiTabs[5] = (ImageView) findViewById(R.id.emojis_tab_5_cars);
        mEmojiTabs[6] = (ImageView) findViewById(R.id.emojis_tab_6_electronics);
        mEmojiTabs[7] = (ImageView) findViewById(R.id.emojis_tab_7_symbols);
        for (int i = 0; i < mEmojiTabs.length; i++) {
            final int position = i;
            mEmojiTabs[i].setColorFilter(mUnselectedMask, PorterDuff.Mode.SRC_IN);
            mEmojiTabs[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    emojisPager.setCurrentItem(position);
                }
            });
        }

        ImageButton backspace = (ImageButton) findViewById(R.id.emojis_backspace);
        //set icon
        backspace.setImageResource(black ? R.drawable.emoji_backspace_dark : R.drawable.emoji_backspace_light);
        backspace.setOnTouchListener(new RepeatListener(1000, 50, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnEmojiconBackspaceClickedListener != null) {
                    mOnEmojiconBackspaceClickedListener.onEmojiconBackspaceClicked(v);
                }
            }
        }));

        // get last selected page
        mRecentsManager = EmojiconRecentsManager.getInstance(getContext());
        int page = mRecentsManager.getRecentPage();
        // last page was recents, check if there are recents to use
        // if none was found, go to page 1
        if (page == 0 && mRecentsManager.size() == 0) {
            page = 1;
        }

        if (page == 0) {
            onPageSelected(page);
        } else {
            emojisPager.setCurrentItem(page, false);
        }
    }

    public void setUseSystemDefault(boolean useSystemDefault) {
        mUseSystemDefault = useSystemDefault;
    }

    public void setOnEmojiconBackspaceClickedListener(OnEmojiconBackspaceClickedListener listener) {
        mOnEmojiconBackspaceClickedListener = listener;
    }

    public void setOnEmojiconClickedListener(OnEmojiconClickedListener listener) {
        mEmojisAdapter.setOnEmojiconClickedListener(listener);
    }

    public static void input(EditText editText, Emojicon emojicon) {
        if (editText == null || emojicon == null) {
            return;
        }

        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start < 0) {
            editText.append(emojicon.getEmoji());
        } else {
            editText.getText().replace(Math.min(start, end), Math.max(start, end), emojicon.getEmoji(), 0, emojicon.getEmoji().length());
        }
    }

    @Override
    public void addRecentEmoji(Context context, Emojicon emojicon) {
        final ViewPager emojisPager = (ViewPager) findViewById(R.id.emojis_pager);
        EmojiconRecentsGridView grid = (EmojiconRecentsGridView) mEmojisAdapter.instantiateItem(emojisPager, 0);
        grid.addRecentEmoji(context, emojicon);
    }

    public static void backspace(EditText editText) {
        KeyEvent event = new KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL);
        editText.dispatchKeyEvent(event);
    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {
    }

    @Override
    public void onPageSelected(int i) {
        if (mEmojiTabLastSelectedIndex == i) {
            return;
        }
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:

                if (mEmojiTabLastSelectedIndex >= 0 && mEmojiTabLastSelectedIndex < mEmojiTabs.length) {
                    mEmojiTabs[mEmojiTabLastSelectedIndex].setSelected(false);
                    //mEmojiTabs[mEmojiTabLastSelectedIndex].clearColorFilter();
                    mEmojiTabs[mEmojiTabLastSelectedIndex].setColorFilter(mUnselectedMask, PorterDuff.Mode.SRC_IN);
                }
                mEmojiTabs[i].setSelected(true);
                mEmojiTabs[i].setColorFilter(mSelectedMask, PorterDuff.Mode.SRC_IN);
                mEmojiTabLastSelectedIndex = i;
                mRecentsManager.setRecentPage(i);
                break;
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    private static class EmojisPagerAdapter extends PagerAdapter {
        private List<EmojiconGridView> views;

        public EmojisPagerAdapter(List<EmojiconGridView> views) {
            this.views = views;
        }

        public void setOnEmojiconClickedListener(OnEmojiconClickedListener listener) {
            for (EmojiconGridView view : views) {
                view.setOnEmojiconClickedListener(listener);
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View view = views.get(position);
            container.removeView(view);
        }

        @Override
        public int getCount() {
            return views.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = views.get(position);
            if (view.getParent() == null)
                container.addView(view);
            return view;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    /**
     * A class, that can be used as a TouchListener on any view (e.g. a Button).
     * It cyclically runs a clickListener, emulating keyboard-like behaviour. First
     * click is fired immediately, next before initialInterval, and subsequent before
     * normalInterval.
     * <p/>
     * <p>Interval is scheduled before the onClick completes, so it has to run fast.
     * If it runs slow, it does not generate skipped onClicks.
     */
    public static class RepeatListener implements View.OnTouchListener {

        private Handler handler = new Handler();

        private int initialInterval;
        private final int normalInterval;
        private final View.OnClickListener clickListener;

        private Runnable handlerRunnable = new Runnable() {
            @Override
            public void run() {
                if (downView == null) {
                    return;
                }
                handler.removeCallbacksAndMessages(downView);
                handler.postAtTime(this, downView, SystemClock.uptimeMillis() + normalInterval);
                clickListener.onClick(downView);
            }
        };

        private View downView;

        /**
         * @param initialInterval The interval before first click event
         * @param normalInterval  The interval before second and subsequent click
         *                        events
         * @param clickListener   The OnClickListener, that will be called
         *                        periodically
         */
        public RepeatListener(int initialInterval, int normalInterval, View.OnClickListener clickListener) {
            if (clickListener == null)
                throw new IllegalArgumentException("null runnable");
            if (initialInterval < 0 || normalInterval < 0)
                throw new IllegalArgumentException("negative interval");

            this.initialInterval = initialInterval;
            this.normalInterval = normalInterval;
            this.clickListener = clickListener;
        }

        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downView = view;
                    handler.removeCallbacks(handlerRunnable);
                    handler.postAtTime(handlerRunnable, downView, SystemClock.uptimeMillis() + initialInterval);
                    downView.setPressed(true);
                    clickListener.onClick(view);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_OUTSIDE:
                    handler.removeCallbacksAndMessages(downView);
                    downView.setPressed(false);
                    downView = null;
                    return true;
            }
            return false;
        }
    }

    public interface OnEmojiconBackspaceClickedListener {
        void onEmojiconBackspaceClicked(View v);
    }
}
