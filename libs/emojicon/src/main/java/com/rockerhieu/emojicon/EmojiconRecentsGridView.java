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

import com.rockerhieu.emojicon.emoji.Emojicon;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

/**
 * @author Daniele Ricci
 */
public class EmojiconRecentsGridView extends EmojiconGridView implements EmojiconRecents {

    public EmojiconRecentsGridView(Context context) {
        super(context);
    }

    public EmojiconRecentsGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public EmojiconRecentsGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EmojiconRecentsGridView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected static EmojiconRecentsGridView newInstance(Context context, ViewGroup parent, boolean useSystemDefault) {
        EmojiconRecentsGridView view = (EmojiconRecentsGridView) LayoutInflater.from(context)
            .inflate(R.layout.emojicon_grid_recents, parent, false);
        view.setUseSystemDefault(useSystemDefault);
        return view;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        EmojiconRecentsManager recents = EmojiconRecentsManager
            .getInstance(getContext());
        super.init(recents);
    }

    @Override
    public void addRecentEmoji(Context context, Emojicon emojicon) {
        EmojiconRecentsManager recents = EmojiconRecentsManager
            .getInstance(context);
        recents.push(emojicon);

        // notify dataset changed
        EmojiAdapter adapter = (EmojiAdapter) getAdapter();
        if (adapter != null)
            adapter.notifyDataSetChanged();
    }

}
