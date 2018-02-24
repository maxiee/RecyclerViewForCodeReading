package com.maxiee.recyclerview;

import android.view.View;

public class RecyclerViewUtils {
    static ViewHolder getChildViewHolderInt(View child) {
        if (child == null) {
            return null;
        }
        return ((com.maxiee.recyclerview.LayoutParams) child.getLayoutParams()).mViewHolder;
    }
}
