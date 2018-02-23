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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.TraceCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.OverScroller;

import static com.maxiee.recyclerview.RecyclerViewConstants.DEBUG;
import static com.maxiee.recyclerview.RecyclerViewConstants.NO_POSITION;
import static com.maxiee.recyclerview.RecyclerViewConstants.SCROLL_STATE_SETTLING;
import static com.maxiee.recyclerview.RecyclerViewConstants.TAG;
import static com.maxiee.recyclerview.RecyclerViewConstants.TRACE_ON_LAYOUT_TAG;
import static com.maxiee.recyclerview.RecyclerViewConstants.VERBOSE_TRACING;

public class RecyclerView extends ViewGroup {

    private static final int[] CLIP_TO_PADDING_ATTR = {android.R.attr.clipToPadding};

    /**
     * Prior to L, there is no way to query this variable which is why we override the setter and
     * track it here.
     */
    boolean mClipToPadding;

    Adapter mAdapter;

    @VisibleForTesting
    LayoutManager mLayout;

    @VisibleForTesting
    boolean mFirstLayoutComplete;

    // Counting lock to control whether we should ignore requestLayout calls from children or not.
    private int mEatRequestLayout = 0;

    boolean mLayoutRequestEaten;

    /**
     * Set to true when an adapter data set changed notification is received.
     * In that case, we cannot run any animations since we don't know what happened until layout.
     *
     * Attached items are invalid until next layout, at which point layout will animate/replace
     * items as necessary, building up content from the (effectively) new adapter from scratch.
     *
     * Cached items must be discarded when setting this to true, so that the cache may be freely
     * used by prefetching until the next layout occurs.
     *
     * @see #setDataSetChangedAfterLayout()
     */
    boolean mDataSetHasChangedAfterLayout = false;

    /**
     * This variable is incremented during a dispatchLayout and/or scroll.
     * Some methods should not be called during these periods (e.g. adapter data change).
     * Doing so will create hard to find bugs so we better check it and throw an exception.
     *
     * @see #assertInLayoutOrScroll(String)
     * @see #assertNotInLayoutOrScroll(String)
     */
    private int mLayoutOrScrollCounter = 0;

    ItemAnimator mItemAnimator = new DefaultItemAnimator();

    final Recycler mRecycler = new Recycler();

    /**
     * Handles adapter updates
     */
    AdapterHelper mAdapterHelper;

    /**
     * Handles abstraction between LayoutManager children and RecyclerView children
     */
    ChildHelper mChildHelper;

    /**
     * Keeps data about views to be used for animations
     * 保存视图相关数据, 用于动画
     */
    final ViewInfoStore mViewInfoStore = new ViewInfoStore();

    // Touch/scrolling handling
    private int mTouchSlop;
    private final int mMinFlingVelocity;
    private final int mMaxFlingVelocity;

    // This value is used when handling rotary encoder generic motion events.
    private float mScaledHorizontalScrollFactor = Float.MIN_VALUE;
    private float mScaledVerticalScrollFactor = Float.MIN_VALUE;

    private boolean mPreserveFocusAfterLayout = true;

    final ViewFlinger mViewFlinger = new ViewFlinger();

    final State mState = new State();

    // For use in item animations
    boolean mItemsAddedOrRemoved = false;
    boolean mItemsChanged = false;

    private ItemAnimator.ItemAnimatorListener mItemAnimatorListener = new ItemAnimatorRestoreListener();

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

        mItemAnimator.setListener(mItemAnimatorListener);

        initAdapterManager();
        initChildrenHelper();

    }

    private void initChildrenHelper() {
        mChildHelper = new ChildHelper(new ChildHelper.Callback() {
            @Override
            public int getChildCount() {
                return RecyclerView.this.getChildCount();
            }

            @Override
            public void addView(View child, int index) {
                if (VERBOSE_TRACING) {
                    TraceCompat.beginSection("RV addView");
                }
                RecyclerView.this.addView(child, index);
                if (VERBOSE_TRACING) {
                    TraceCompat.endSection();
                }
                dispatchChildAttached(child);
            }

            @Override
            public int indexOfChild(View view) {
                return RecyclerView.this.indexOfChild(view);
            }

            @Override
            public void removeViewAt(int index) {
                final View child = RecyclerView.this.getChildAt(index);
                if (child != null) {
                    dispatchChildDetached(child);

                    // Clear any android.view.animation.Animation that may prevent the item from
                    // detaching when being removed. If a child is re-added before the
                    // lazy detach occurs, it will receive invalid attach/detach sequencing.
                    child.clearAnimation();
                }
                if (VERBOSE_TRACING) {
                    TraceCompat.beginSection("RV removeViewAt");
                }
                RecyclerView.this.removeViewAt(index);
                if (VERBOSE_TRACING) {
                    TraceCompat.endSection();
                }
            }

            @Override
            public View getChildAt(int offset) {
                return RecyclerView.this.getChildAt(offset);
            }

            @Override
            public void removeAllViews() {
                final int count = getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = getChildAt(i);
                    dispatchChildDetached(child);

                    // Clear any android.view.animation.Animation that may prevent the item from
                    // detaching when being removed. If a child is re-added before the
                    // lazy detach occurs, it will receive invalid attach/detach sequencing.
                    child.clearAnimation();
                }
                RecyclerView.this.removeAllViews();
            }

            @Override
            public ViewHolder getChildViewHolder(View view) {
                return getChildViewHolderInt(view);
            }

            @Override
            public void attachViewToParent(View child, int index,
                                           ViewGroup.LayoutParams layoutParams) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    if (!vh.isTmpDetached() && !vh.shouldIgnore()) {
                        throw new IllegalArgumentException("Called attach on a child which is not"
                                + " detached: " + vh + exceptionLabel());
                    }
                    if (DEBUG) {
                        Log.d(TAG, "reAttach " + vh);
                    }
                    vh.clearTmpDetachFlag();
                }
                RecyclerView.this.attachViewToParent(child, index, layoutParams);
            }

            @Override
            public void detachViewFromParent(int offset) {
                final View view = getChildAt(offset);
                if (view != null) {
                    final ViewHolder vh = getChildViewHolderInt(view);
                    if (vh != null) {
                        if (vh.isTmpDetached() && !vh.shouldIgnore()) {
                            throw new IllegalArgumentException("called detach on an already"
                                    + " detached child " + vh + exceptionLabel());
                        }
                        if (DEBUG) {
                            Log.d(TAG, "tmpDetach " + vh);
                        }
                        vh.addFlags(ViewHolder.FLAG_TMP_DETACHED);
                    }
                }
                RecyclerView.this.detachViewFromParent(offset);
            }

            @Override
            public void onEnteredHiddenState(View child) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    vh.onEnteredHiddenState(RecyclerView.this);
                }
            }

            @Override
            public void onLeftHiddenState(View child) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    vh.onLeftHiddenState(RecyclerView.this);
                }
            }
        });
    }

    void initAdapterManager() {
        mAdapterHelper = new AdapterHelper(new AdapterHelper.Callback() {
            @Override
            public ViewHolder findViewHolder(int position) {
                final ViewHolder vh = findViewHolderForPosition(position, true);
                if (vh == null) {
                    return null;
                }
                // ensure it is not hidden because for adapter helper, the only thing matter is that
                // LM thinks view is a child.
                if (mChildHelper.isHidden(vh.itemView)) {
                    if (DEBUG) {
                        Log.d(TAG, "assuming view holder cannot be find because it is hidden");
                    }
                    return null;
                }
                return vh;
            }

            @Override
            public void offsetPositionsForRemovingInvisible(int start, int count) {
                offsetPositionRecordsForRemove(start, count, true);
                mItemsAddedOrRemoved = true;
                mState.mDeletedInvisibleItemCountSincePreviousLayout += count;
            }

            @Override
            public void offsetPositionsForRemovingLaidOutOrNewView(
                    int positionStart, int itemCount) {
                offsetPositionRecordsForRemove(positionStart, itemCount, false);
                mItemsAddedOrRemoved = true;
            }


            @Override
            public void markViewHoldersUpdated(int positionStart, int itemCount, Object payload) {
                viewRangeUpdate(positionStart, itemCount, payload);
                mItemsChanged = true;
            }

            @Override
            public void onDispatchFirstPass(UpdateOp op) {
                dispatchUpdate(op);
            }

            void dispatchUpdate(UpdateOp op) {
                switch (op.cmd) {
                    case UpdateOp.ADD:
                        mLayout.onItemsAdded(RecyclerView.this, op.positionStart, op.itemCount);
                        break;
                    case UpdateOp.REMOVE:
                        mLayout.onItemsRemoved(RecyclerView.this, op.positionStart, op.itemCount);
                        break;
                    case UpdateOp.UPDATE:
                        mLayout.onItemsUpdated(RecyclerView.this, op.positionStart, op.itemCount,
                                op.payload);
                        break;
                    case UpdateOp.MOVE:
                        mLayout.onItemsMoved(RecyclerView.this, op.positionStart, op.itemCount, 1);
                        break;
                }
            }

            @Override
            public void onDispatchSecondPass(UpdateOp op) {
                dispatchUpdate(op);
            }

            @Override
            public void offsetPositionsForAdd(int positionStart, int itemCount) {
                offsetPositionRecordsForInsert(positionStart, itemCount);
                mItemsAddedOrRemoved = true;
            }

            @Override
            public void offsetPositionsForMove(int from, int to) {
                offsetPositionRecordsForMove(from, to);
                // should we create mItemsMoved ?
                mItemsAddedOrRemoved = true;
            }
        });
    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        TraceCompat.beginSection(TRACE_ON_LAYOUT_TAG);
        dispatchLayout();
        TraceCompat.endSection();
        mFirstLayoutComplete = true;
    }

    /**
     * Wrapper around layoutChildren() that handles animating changes caused by layout.
     * Animations work on the assumption that there are five different kinds of items
     * in play:
     * PERSISTENT: items are visible before and after layout
     * REMOVED: items were visible before layout and were removed by the app
     * ADDED: items did not exist before layout and were added by the app
     * DISAPPEARING: items exist in the data set before/after, but changed from
     * visible to non-visible in the process of layout (they were moved off
     * screen as a side-effect of other changes)
     * APPEARING: items exist in the data set before/after, but changed from
     * non-visible to visible in the process of layout (they were moved on
     * screen as a side-effect of other changes)
     * The overall approach figures out what items exist before/after layout and
     * infers one of the five above states for each of the items. Then the animations
     * are set up accordingly:
     * PERSISTENT views are animated via
     * {@link ItemAnimator#animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * DISAPPEARING views are animated via
     * {@link ItemAnimator#animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * APPEARING views are animated via
     * {@link ItemAnimator#animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * and changed views are animated via
     * {@link ItemAnimator#animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)}.
     */
    void dispatchLayout() {
        if (mAdapter == null) {
            Log.e(TAG, "No adapter attached; skipping layout");
            // leave the state in START
            return;
        }
        if (mLayout == null) {
            Log.e(TAG, "No layout manager attached; skipping layout");
            // leave the state in START
            return;
        }
        mState.mIsMeasuring = false;
        if (mState.mLayoutStep == State.STEP_START) {
            dispatchLayoutStep1();
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()
                || mLayout.getHeight() != getHeight()) {
            // First 2 steps are done in onMeasure but looks like we have to run again due to
            // changed size.
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else {
            // always make sure we sync them (to ensure mode is exact)
            mLayout.setExactMeasureSpecsFrom(this);
        }
        dispatchLayoutStep3();
    }

    /**
     * The first step of a layout where we;
     * - process adapter updates
     * - decide which animation should run
     * - save information about current views
     * - If necessary, run predictive layout and save its information
     */
    private void dispatchLayoutStep1() {
        mState.assertLayoutStep(State.STEP_START);
        fillRemainingScrollValues(mState);
        mState.mIsMeasuring = false;
        eatRequestLayout();
        mViewInfoStore.clear();
        onEnterLayoutOrScroll();
        processAdapterUpdatesAndSetAnimationFlags();
        saveFocusInfo();
        mState.mTrackOldChangeHolders = mState.mRunSimpleAnimations && mItemsChanged;
        mItemsAddedOrRemoved = mItemsChanged = false;
        mState.mInPreLayout = mState.mRunPredictiveAnimations;
        mState.mItemCount = mAdapter.getItemCount();
        findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);

        if (mState.mRunSimpleAnimations) {
            // Step 0: Find out where all non-removed items are, pre-layout
            int count = mChildHelper.getChildCount();
            for (int i = 0; i < count; ++i) {
                final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                if (holder.shouldIgnore() || (holder.isInvalid() && !mAdapter.hasStableIds())) {
                    continue;
                }
                final ItemHolderInfo animationInfo = mItemAnimator
                        .recordPreLayoutInformation(mState, holder,
                                ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),
                                holder.getUnmodifiedPayloads());
                mViewInfoStore.addToPreLayout(holder, animationInfo);
                if (mState.mTrackOldChangeHolders && holder.isUpdated() && !holder.isRemoved()
                        && !holder.shouldIgnore() && !holder.isInvalid()) {
                    long key = getChangedHolderKey(holder);
                    // This is NOT the only place where a ViewHolder is added to old change holders
                    // list. There is another case where:
                    //    * A VH is currently hidden but not deleted
                    //    * The hidden item is changed in the adapter
                    //    * Layout manager decides to layout the item in the pre-Layout pass (step1)
                    // When this case is detected, RV will un-hide that view and add to the old
                    // change holders list.
                    mViewInfoStore.addToOldChangeHolders(key, holder);
                }
            }
        }
        if (mState.mRunPredictiveAnimations) {
            // Step 1: run prelayout: This will use the old positions of items. The layout manager
            // is expected to layout everything, even removed items (though not to add removed
            // items back to the container). This gives the pre-layout position of APPEARING views
            // which come into existence as part of the real layout.

            // Save old positions so that LayoutManager can run its mapping logic.
            saveOldPositions();
            final boolean didStructureChange = mState.mStructureChanged;
            mState.mStructureChanged = false;
            // temporarily disable flag because we are asking for previous layout
            mLayout.onLayoutChildren(mRecycler, mState);
            mState.mStructureChanged = didStructureChange;

            for (int i = 0; i < mChildHelper.getChildCount(); ++i) {
                final View child = mChildHelper.getChildAt(i);
                final ViewHolder viewHolder = getChildViewHolderInt(child);
                if (viewHolder.shouldIgnore()) {
                    continue;
                }
                if (!mViewInfoStore.isInPreLayout(viewHolder)) {
                    int flags = ItemAnimator.buildAdapterChangeFlagsForAnimations(viewHolder);
                    boolean wasHidden = viewHolder
                            .hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                    if (!wasHidden) {
                        flags |= ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
                    }
                    final ItemHolderInfo animationInfo = mItemAnimator.recordPreLayoutInformation(
                            mState, viewHolder, flags, viewHolder.getUnmodifiedPayloads());
                    if (wasHidden) {
                        recordAnimationInfoIfBouncedHiddenView(viewHolder, animationInfo);
                    } else {
                        mViewInfoStore.addToAppearedInPreLayoutHolders(viewHolder, animationInfo);
                    }
                }
            }
            // we don't process disappearing list because they may re-appear in post layout pass.
            clearOldPositions();
        } else {
            clearOldPositions();
        }
        onExitLayoutOrScroll();
        resumeRequestLayout(false);
        mState.mLayoutStep = State.STEP_LAYOUT;
    }

    void eatRequestLayout() {
        mEatRequestLayout++;
        if (mEatRequestLayout == 1 && !mLayoutFrozen) {
            mLayoutRequestEaten = false;
        }
    }

    void onEnterLayoutOrScroll() {
        mLayoutOrScrollCounter++;
    }

    /**
     * Returns a unique key to be used while handling change animations.
     * It might be child's position or stable id depending on the adapter type.
     */
    long getChangedHolderKey(ViewHolder holder) {
        return mAdapter.hasStableIds() ? holder.getItemId() : holder.mPosition;
    }

    /**
     * Consumes adapter updates and calculates which type of animations we want to run.
     * Called in onMeasure and dispatchLayout.
     * 使用 Adapter 更新并计算我们想要运行的动画类型. 在 onMeasure 和 dispatchLayout 中被调用.
     *
     * $HTT$ 这里的 Consumes 该如何翻译?
     *
     * <p>
     * This method may process only the pre-layout state of updates or all of them.
     * 这个方法只处理所有的 pre-layout 的更新
     */
    private void processAdapterUpdatesAndSetAnimationFlags() {
        if (mDataSetHasChangedAfterLayout) {
            // Processing these items have no value since data set changed unexpectedly.
            // Instead, we just reset it.
            mAdapterHelper.reset();
            mLayout.onItemsChanged(this);
        }
        // simple animations are a subset of advanced animations (which will cause a
        // pre-layout step)
        // If layout supports predictive animations, pre-process to decide if we want to run them
        if (predictiveItemAnimationsEnabled()) {
            mAdapterHelper.preProcess();
        } else {
            mAdapterHelper.consumeUpdatesInOnePass();
        }
        boolean animationTypeSupported = mItemsAddedOrRemoved || mItemsChanged;
        mState.mRunSimpleAnimations = mFirstLayoutComplete
                && mItemAnimator != null
                && (mDataSetHasChangedAfterLayout
                || animationTypeSupported
                || mLayout.mRequestedSimpleAnimations)
                && (!mDataSetHasChangedAfterLayout
                || mAdapter.hasStableIds());
        mState.mRunPredictiveAnimations = mState.mRunSimpleAnimations
                && animationTypeSupported
                && !mDataSetHasChangedAfterLayout
                && predictiveItemAnimationsEnabled();
    }

    private void findMinMaxChildLayoutPositions(int[] into) {
        final int count = mChildHelper.getChildCount();
        if (count == 0) {
            into[0] = NO_POSITION;
            into[1] = NO_POSITION;
            return;
        }
        int minPositionPreLayout = Integer.MAX_VALUE;
        int maxPositionPreLayout = Integer.MIN_VALUE;
        for (int i = 0; i < count; ++i) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            if (holder.shouldIgnore()) {
                continue;
            }
            final int pos = holder.getLayoutPosition();
            if (pos < minPositionPreLayout) {
                minPositionPreLayout = pos;
            }
            if (pos > maxPositionPreLayout) {
                maxPositionPreLayout = pos;
            }
        }
        into[0] = minPositionPreLayout;
        into[1] = maxPositionPreLayout;
    }

    private boolean predictiveItemAnimationsEnabled() {
        return (mItemAnimator != null && mLayout.supportsPredictiveItemAnimations());
    }

    final void fillRemainingScrollValues(State state) {
        if (getScrollState() == SCROLL_STATE_SETTLING) {
            final OverScroller scroller = mViewFlinger.mScroller;
            state.mRemainingScrollHorizontal = scroller.getFinalX() - scroller.getCurrX();
            state.mRemainingScrollVertical = scroller.getFinalY() - scroller.getCurrY();
        } else {
            state.mRemainingScrollHorizontal = 0;
            state.mRemainingScrollVertical = 0;
        }
    }

    private void saveFocusInfo() {
        View child = null;
        if (mPreserveFocusAfterLayout && hasFocus() && mAdapter != null) {
            child = getFocusedChild();
        }

        final ViewHolder focusedVh = child == null ? null : findContainingViewHolder(child);
        if (focusedVh == null) {
            resetFocusInfo();
        } else {
            mState.mFocusedItemId = mAdapter.hasStableIds() ? focusedVh.getItemId() : NO_ID;
            // mFocusedItemPosition should hold the current adapter position of the previously
            // focused item. If the item is removed, we store the previous adapter position of the
            // removed item.
            mState.mFocusedItemPosition = mDataSetHasChangedAfterLayout ? NO_POSITION
                    : (focusedVh.isRemoved() ? focusedVh.mOldPosition
                    : focusedVh.getAdapterPosition());
            mState.mFocusedSubChildId = getDeepestFocusedViewWithId(focusedVh.itemView);
        }
    }

    private int getDeepestFocusedViewWithId(View view) {
        int lastKnownId = view.getId();
        while (!view.isFocused() && view instanceof ViewGroup && view.hasFocus()) {
            view = ((ViewGroup) view).getFocusedChild();
            final int id = view.getId();
            if (id != View.NO_ID) {
                lastKnownId = view.getId();
            }
        }
        return lastKnownId;
    }

    private void resetFocusInfo() {
        mState.mFocusedItemId = NO_ID;
        mState.mFocusedItemPosition = NO_POSITION;
        mState.mFocusedSubChildId = View.NO_ID;
    }

    static ViewHolder getChildViewHolderInt(View child) {
        if (child == null) {
            return null;
        }
        return ((com.maxiee.recyclerview.LayoutParams) child.getLayoutParams()).mViewHolder;
    }

    /**
     * Retrieve the {@link ViewHolder} for the given child view.
     *
     * @param child Child of this RecyclerView to query for its ViewHolder
     * @return The child view's ViewHolder
     */
    public ViewHolder getChildViewHolder(View child) {
        final ViewParent parent = child.getParent();
        if (parent != null && parent != this) {
            throw new IllegalArgumentException("View " + child + " is not a direct child of "
                    + this);
        }
        return getChildViewHolderInt(child);
    }

    /**
     * Traverses the ancestors of the given view and returns the item view that contains it and
     * also a direct child of the RecyclerView. This returned view can be used to get the
     * ViewHolder by calling {@link #getChildViewHolder(View)}.
     *
     * @param view The view that is a descendant of the RecyclerView.
     *
     * @return The direct child of the RecyclerView which contains the given view or null if the
     * provided view is not a descendant of this RecyclerView.
     *
     * @see #getChildViewHolder(View)
     * @see #findContainingViewHolder(View)
     */
    @Nullable
    public View findContainingItemView(View view) {
        ViewParent parent = view.getParent();
        while (parent != null && parent != this && parent instanceof View) {
            view = (View) parent;
            parent = view.getParent();
        }
        return parent == this ? view : null;
    }

    /**
     * Returns the ViewHolder that contains the given view.
     *
     * @param view The view that is a descendant of the RecyclerView.
     *
     * @return The ViewHolder that contains the given view or null if the provided view is not a
     * descendant of this RecyclerView.
     */
    @Nullable
    public ViewHolder findContainingViewHolder(View view) {
        View itemView = findContainingItemView(view);
        return itemView == null ? null : getChildViewHolder(itemView);
    }

    void saveOldPositions() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (DEBUG && holder.mPosition == -1 && !holder.isRemoved()) {
                throw new IllegalStateException("view holder cannot have position -1 unless it"
                        + " is removed" + exceptionLabel());
            }
            if (!holder.shouldIgnore()) {
                holder.saveOldPosition();
            }
        }
    }

    void clearOldPositions() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (!holder.shouldIgnore()) {
                holder.clearOldPosition();
            }
        }
        mRecycler.clearOldPositions();
    }

    /**
     * Label appended to all public exception strings, used to help find which RV in an app is
     * hitting an exception.
     */
    String exceptionLabel() {
        return " " + super.toString()
                + ", adapter:" + mAdapter
                + ", layout:" + mLayout
                + ", context:" + getContext();
    }
}
