package com.maxiee.recyclerview;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * A <code>LayoutManager</code> is responsible for measuring and positioning item views
 * within a <code>RecyclerView</code> as well as determining the policy for when to recycle
 * item views that are no longer visible to the user. By changing the <code>LayoutManager</code>
 * a <code>RecyclerView</code> can be used to implement a standard vertically scrolling list,
 * a uniform grid, staggered grids, horizontally scrolling collections and more. Several stock
 * layout managers are provided for general use.
 * <p/>
 * If the LayoutManager specifies a default constructor or one with the signature
 * ({@link Context}, {@link AttributeSet}, {@code int}, {@code int}), RecyclerView will
 * instantiate and set the LayoutManager when being inflated. Most used properties can
 * be then obtained from {@link #getProperties(Context, AttributeSet, int, int)}. In case
 * a LayoutManager specifies both constructors, the non-default constructor will take
 * precedence.
 *
 */
public class LayoutManager {
    ChildHelper mChildHelper;
    RecyclerView mRecyclerView;

    /**
     * The callback used for retrieving information about a RecyclerView and its children in the
     * horizontal direction.
     */
    private final ViewBoundsCheck.Callback mHorizontalBoundCheckCallback =
            new ViewBoundsCheck.Callback() {
                @Override
                public int getChildCount() {
                    return LayoutManager.this.getChildCount();
                }

                @Override
                public View getParent() {
                    return mRecyclerView;
                }

                @Override
                public View getChildAt(int index) {
                    return LayoutManager.this.getChildAt(index);
                }

                @Override
                public int getParentStart() {
                    return LayoutManager.this.getPaddingLeft();
                }

                @Override
                public int getParentEnd() {
                    return LayoutManager.this.getWidth() - LayoutManager.this.getPaddingRight();
                }

                @Override
                public int getChildStart(View view) {
                    final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            view.getLayoutParams();
                    return LayoutManager.this.getDecoratedLeft(view) - params.leftMargin;
                }

                @Override
                public int getChildEnd(View view) {
                    final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            view.getLayoutParams();
                    return LayoutManager.this.getDecoratedRight(view) + params.rightMargin;
                }
            };

    /**
     * The callback used for retrieving information about a RecyclerView and its children in the
     * vertical direction.
     */
    private final ViewBoundsCheck.Callback mVerticalBoundCheckCallback =
            new ViewBoundsCheck.Callback() {
                @Override
                public int getChildCount() {
                    return LayoutManager.this.getChildCount();
                }

                @Override
                public View getParent() {
                    return mRecyclerView;
                }

                @Override
                public View getChildAt(int index) {
                    return LayoutManager.this.getChildAt(index);
                }

                @Override
                public int getParentStart() {
                    return LayoutManager.this.getPaddingTop();
                }

                @Override
                public int getParentEnd() {
                    return LayoutManager.this.getHeight()
                            - LayoutManager.this.getPaddingBottom();
                }

                @Override
                public int getChildStart(View view) {
                    final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            view.getLayoutParams();
                    return LayoutManager.this.getDecoratedTop(view) - params.topMargin;
                }

                @Override
                public int getChildEnd(View view) {
                    final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            view.getLayoutParams();
                    return LayoutManager.this.getDecoratedBottom(view) + params.bottomMargin;
                }
            };

    /**
     * Utility objects used to check the boundaries of children against their parent
     * RecyclerView.
     * @see #isViewPartiallyVisible(View, boolean, boolean),
     * {@link LinearLayoutManager#findOneVisibleChild(int, int, boolean, boolean)},
     * and {@link LinearLayoutManager#findOnePartiallyOrCompletelyInvisibleChild(int, int)}.
     */
    ViewBoundsCheck mHorizontalBoundCheck = new ViewBoundsCheck(mHorizontalBoundCheckCallback);
    ViewBoundsCheck mVerticalBoundCheck = new ViewBoundsCheck(mVerticalBoundCheckCallback);

    @Nullable
    SmoothScroller mSmoothScroller;

    boolean mRequestedSimpleAnimations = false;

    boolean mIsAttachedToWindow = false;

    boolean mAutoMeasure = false;
}
