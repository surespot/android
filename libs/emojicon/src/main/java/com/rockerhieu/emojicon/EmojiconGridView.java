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

import java.util.List;

import com.rockerhieu.emojicon.emoji.Emojicon;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

/**
 * @author Daniele Ricci(daniele.athome@gmail.com)
 */
public class EmojiconGridView extends GridView implements AdapterView.OnItemClickListener {
    private OnEmojiconClickedListener mOnEmojiconClickedListener;
    private EmojiconRecents mRecents;
    private boolean mUseSystemDefault = false;

    public static EmojiconGridView newInstance(Context context, ViewGroup parent,
            Emojicon[] data, EmojiconRecents recents, boolean useSystemDefault) {
        EmojiconGridView view = (EmojiconGridView) LayoutInflater.from(context)
            .inflate(R.layout.emojicon_grid, parent, false);
        view.init(data);
        view.setRecents(recents);
        view.setUseSystemDefault(useSystemDefault);
        return view;
    }

    public EmojiconGridView(Context context) {
        super(context);
    }

    public EmojiconGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public EmojiconGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EmojiconGridView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void init(Emojicon[] data) {
        init(new EmojiAdapter(getContext(), data, mUseSystemDefault));
    }

    protected void init(List<Emojicon> data) {
        init(new EmojiAdapter(getContext(), data, mUseSystemDefault));
    }

    private void init(EmojiAdapter adapter) {
        // this doesn't work -- setOnItemClickListener(this);
        adapter.setOnItemClickListener(this);
        setAdapter(adapter);
    }

    public void setUseSystemDefault(boolean useSystemDefault) {
        mUseSystemDefault = useSystemDefault;
    }

    public void setOnEmojiconClickedListener(OnEmojiconClickedListener listener) {
        mOnEmojiconClickedListener = listener;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mOnEmojiconClickedListener != null) {
            mOnEmojiconClickedListener.onEmojiconClicked((Emojicon) getItemAtPosition(position));
        }
        if (mRecents != null) {
            mRecents.addRecentEmoji(getContext(),
                (Emojicon) getItemAtPosition(position));
        }
    }

    private void setRecents(EmojiconRecents recents) {
        mRecents = recents;
    }

}
