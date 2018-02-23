/*
 * Copyright (C) 2013 The Android Open Source Project
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


package com.maxiee.recyclerview;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

public class RecyclerView extends ViewGroup {

    private static final int[] CLIP_TO_PADDING_ATTR = {android.R.attr.clipToPadding};

    /**
     * Prior to L, there is no way to query this variable which is why we override the setter and
     * track it here.
     */
    boolean mClipToPadding;

    ItemAnimator mItemAnimator = new DefaultItemAnimator();

    // Touch/scrolling handling
    private int mTouchSlop;
    private final int mMinFlingVelocity;
    private final int mMaxFlingVelocity;

    // This value is used when handling rotary encoder generic motion events.
    private float mScaledHorizontalScrollFactor = Float.MIN_VALUE;
    private float mScaledVerticalScrollFactor = Float.MIN_VALUE;

    public RecyclerView(Context context) {
        this(context, null);
    }

    public RecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, CLIP_TO_PADDING_ATTR, defStyle, 0);
            mClipToPadding = a.getBoolean(0, true);
            a.recycle();
        } else {
            mClipToPadding = true;
        }

        setScrollContainer(true);

        setFocusableInTouchMode(true);

        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mScaledHorizontalScrollFactor =
                ViewConfigurationCompat.getScaledHorizontalScrollFactor(vc, context);
        mScaledVerticalScrollFactor =
                ViewConfigurationCompat.getScaledVerticalScrollFactor(vc, context);
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();

        setWillNotDraw(getOverScrollMode() == View.OVER_SCROLL_NEVER);


    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {

    }
}
