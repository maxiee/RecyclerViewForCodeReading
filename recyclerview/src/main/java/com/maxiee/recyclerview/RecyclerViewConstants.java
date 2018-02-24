package com.maxiee.recyclerview;

import android.os.Build;

public class RecyclerViewConstants {
    public static final String TAG = "MaxieeRV";
    public static final boolean DEBUG = true;

    public static final int NO_POSITION = -1;
    public static final long NO_ID = -1;
    public static final int INVALID_TYPE = -1;

    static final boolean VERBOSE_TRACING = false;

    static final boolean ALLOW_SIZE_IN_UNSPECIFIED_SPEC = Build.VERSION.SDK_INT >= 23;

    /**
     * OnLayout has been called by the View system.
     * If this shows up too many times in Systrace, make sure the children of RecyclerView do not
     * update themselves directly. This will cause a full re-layout but when it happens via the
     * Adapter notifyItemChanged, RecyclerView can avoid full layout calculation.
     */
    static final String TRACE_ON_LAYOUT_TAG = "RV OnLayout";

    /**
     * The RecyclerView is not currently scrolling.
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * The RecyclerView is currently being dragged by outside input such as user touch input.
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * The RecyclerView is currently animating to a final position while not under
     * outside control.
     * RecyclerView 现在是在动画的最终位置, 且不受外部的控制
     *
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_SETTLING = 2;
}
