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

import android.view.View;

import java.util.List;

/**
 * A simple data structure that holds information about an item's bounds.
 * This information is used in calculating item animations. Default implementation of
 * {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int, List)} and
 * {@link #recordPostLayoutInformation(RecyclerView.State, ViewHolder)} returns this data
 * structure. You can extend this class if you would like to keep more information about
 * the Views.
 * <p>
 * If you want to provide your own implementation but still use `super` methods to record
 * basic information, you can override {@link #obtainHolderInfo()} to provide your own
 * instances.
 */
public class ItemHolderInfo {
    /**
     * The left edge of the View (excluding decorations)
     */
    public int left;

    /**
     * The top edge of the View (excluding decorations)
     */
    public int top;

    /**
     * The right edge of the View (excluding decorations)
     */
    public int right;

    /**
     * The bottom edge of the View (excluding decorations)
     */
    public int bottom;

    /**
     * The change flags that were passed to
     * {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int, List)}.
     */
    @AdapterChanges
    public int changeFlags;

    public ItemHolderInfo() {
    }

    /**
     * Sets the {@link #left}, {@link #top}, {@link #right} and {@link #bottom} values from
     * the given ViewHolder. Clears all {@link #changeFlags}.
     *
     * @param holder The ViewHolder whose bounds should be copied.
     * @return This {@link ItemHolderInfo}
     */
    public ItemHolderInfo setFrom(RecyclerView.ViewHolder holder) {
        return setFrom(holder, 0);
    }

    /**
     * Sets the {@link #left}, {@link #top}, {@link #right} and {@link #bottom} values from
     * the given ViewHolder and sets the {@link #changeFlags} to the given flags parameter.
     *
     * @param holder The ViewHolder whose bounds should be copied.
     * @param flags  The adapter change flags that were passed into
     *               {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int,
     *               List)}.
     * @return This {@link ItemHolderInfo}
     */
    public ItemHolderInfo setFrom(RecyclerView.ViewHolder holder,
                                  @AdapterChanges int flags) {
        final View view = holder.itemView;
        this.left = view.getLeft();
        this.top = view.getTop();
        this.right = view.getRight();
        this.bottom = view.getBottom();
        return this;
    }
}
