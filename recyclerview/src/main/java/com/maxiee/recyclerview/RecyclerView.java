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
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.OverScroller;

import static com.maxiee.recyclerview.RecyclerViewConstants.DEBUG;
import static com.maxiee.recyclerview.RecyclerViewConstants.NO_POSITION;
import static com.maxiee.recyclerview.RecyclerViewConstants.SCROLL_STATE_IDLE;
import static com.maxiee.recyclerview.RecyclerViewConstants.SCROLL_STATE_SETTLING;
import static com.maxiee.recyclerview.RecyclerViewConstants.TAG;
import static com.maxiee.recyclerview.RecyclerViewConstants.TRACE_ON_LAYOUT_TAG;
import static com.maxiee.recyclerview.RecyclerViewConstants.VERBOSE_TRACING;
import static com.maxiee.recyclerview.RecyclerViewUtils.getChildViewHolderInt;

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

    // binary OR of change events that were eaten during a layout or scroll.
    private int mEatenAccessibilityChangeFlags;

    private final AccessibilityManager mAccessibilityManager;

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

    private SavedState mPendingSavedState;

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
    private int mScrollState = SCROLL_STATE_IDLE;
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

        mAccessibilityManager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);

    }

    /**
     * Return the current scrolling state of the RecyclerView.
     *
     * @return {@link #SCROLL_STATE_IDLE}, {@link #SCROLL_STATE_DRAGGING} or
     * {@link #SCROLL_STATE_SETTLING}
     */
    public int getScrollState() {
        return mScrollState;
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
     * 封装 layoutChildren() 以处理由布局引起的动画改编
     *
     * Animations work on the assumption that there are five different kinds of items
     * in play:
     * 动画的工作原理是假设有五种不同的项目:
     *
     * PERSISTENT: items are visible before and after layout
     * PERSISTENT: 项目在布局前后都是可见的
     *
     * REMOVED: items were visible before layout and were removed by the app
     * REMOVED: 项目在布局前可见, 之后被 app 删除
     *
     * ADDED: items did not exist before layout and were added by the app
     * ADDED: 项目在布局前不存在, 之后被 app 添加
     *
     * DISAPPEARING: items exist in the data set before/after, but changed from
     * visible to non-visible in the process of layout (they were moved off
     * screen as a side-effect of other changes)
     * DISAPPEARING: 项目在数据集中存在 ($HTT$ before/after 的意思是?), 但是在布局过程中从可见变为不可见
     * (他们被移出屏幕, 作为其他变化的一种副作用 ($HTT$ 这句话如何理解?))
     *
     * APPEARING: items exist in the data set before/after, but changed from
     * non-visible to visible in the process of layout (they were moved on
     * screen as a side-effect of other changes)
     * APPEARING: 项目在数据集中存在 ($HTT$ before/after 的意思是?), 但是在布局过程中从不可见变为可见
     * (他们被移出屏幕, 作为其他变化的一种副作用 ($HTT$ 这句话如何理解?))
     *
     * The overall approach figures out what items exist before/after layout and
     * infers one of the five above states for each of the items. Then the animations
     * are set up accordingly:
     * 整体的方法指出哪些项目在布局前后存在, 并且为每个项目推断出上面五种状态之一.
     * 然后可以相应地设置动画:
     *
     * PERSISTENT views are animated via {@link ItemAnimator#animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     *
     * DISAPPEARING views are animated via {@link ItemAnimator#animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     *
     * APPEARING views are animated via {@link ItemAnimator#animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     *
     * and changed views are animated via {@link ItemAnimator#animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)}.
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

    /**
     * The second layout step where we do the actual layout of the views for the final state.
     * This step might be run multiple times if necessary (e.g. measure).
     *
     * 第二个步骤, 我们执行实际的视图终态的布局操作.
     * 这个步骤如果需要的话, 会被执行多次.
     */
    private void dispatchLayoutStep2() {
        eatRequestLayout();
        onEnterLayoutOrScroll();
        mState.assertLayoutStep(State.STEP_LAYOUT | State.STEP_ANIMATIONS);
        mAdapterHelper.consumeUpdatesInOnePass();
        mState.mItemCount = mAdapter.getItemCount();
        mState.mDeletedInvisibleItemCountSincePreviousLayout = 0;

        // Step 2: Run layout
        mState.mInPreLayout = false;
        mLayout.onLayoutChildren(mRecycler, mState);

        mState.mStructureChanged = false;
        mPendingSavedState = null;

        // onLayoutChildren may have caused client code to disable item animations; re-check
        mState.mRunSimpleAnimations = mState.mRunSimpleAnimations && mItemAnimator != null;
        mState.mLayoutStep = State.STEP_ANIMATIONS;
        onExitLayoutOrScroll();

        resumeRequestLayout(false);
    }

    /**
     * Records the animation information for a view holder that was bounced from hidden list. It
     * also clears the bounce back flag.
     * 记录被绑定到已隐藏列表的 view holder 的动画信息, 它同时也清空了 (bounce back) 标志
     */
    void recordAnimationInfoIfBouncedHiddenView(ViewHolder viewHolder,
                                                ItemHolderInfo animationInfo) {
        // looks like this view bounced back from hidden list!
        viewHolder.setFlags(0, ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
        if (mState.mTrackOldChangeHolders && viewHolder.isUpdated()
                && !viewHolder.isRemoved() && !viewHolder.shouldIgnore()) {
            long key = getChangedHolderKey(viewHolder);
            mViewInfoStore.addToOldChangeHolders(key, viewHolder);
        }
        mViewInfoStore.addToPreLayout(viewHolder, animationInfo);
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

    void onExitLayoutOrScroll() {
        onExitLayoutOrScroll(true);
    }

    void onExitLayoutOrScroll(boolean enableChangeEvents) {
        mLayoutOrScrollCounter--;
        if (mLayoutOrScrollCounter < 1) {
            if (DEBUG && mLayoutOrScrollCounter < 0) {
                throw new IllegalStateException("layout or scroll counter cannot go below zero."
                        + "Some calls are not matching" + exceptionLabel());
            }
            mLayoutOrScrollCounter = 0;
            if (enableChangeEvents) {
                dispatchContentChangedIfNecessary();
                dispatchPendingImportantForAccessibilityChanges();
            }
        }
    }

    void resumeRequestLayout(boolean performLayoutChildren) {
        if (mEatRequestLayout < 1) {
            //noinspection PointlessBooleanExpression
            if (DEBUG) {
                throw new IllegalStateException("invalid eat request layout count"
                        + exceptionLabel());
            }
            mEatRequestLayout = 1;
        }
        if (!performLayoutChildren) {
            // Reset the layout request eaten counter.
            // This is necessary since eatRequest calls can be nested in which case the other
            // call will override the inner one.
            // for instance:
            // eat layout for process adapter updates
            //   eat layout for dispatchLayout
            //     a bunch of req layout calls arrive

            mLayoutRequestEaten = false;
        }
        if (mEatRequestLayout == 1) {
            // when layout is frozen we should delay dispatchLayout()
            if (performLayoutChildren && mLayoutRequestEaten && !mLayoutFrozen
                    && mLayout != null && mAdapter != null) {
                dispatchLayout();
            }
            if (!mLayoutFrozen) {
                mLayoutRequestEaten = false;
            }
        }
        mEatRequestLayout--;
    }

    boolean isAccessibilityEnabled() {
        return mAccessibilityManager != null && mAccessibilityManager.isEnabled();
    }

    private void dispatchContentChangedIfNecessary() {
        final int flags = mEatenAccessibilityChangeFlags;
        mEatenAccessibilityChangeFlags = 0;
        if (flags != 0 && isAccessibilityEnabled()) {
            final AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
            AccessibilityEventCompat.setContentChangeTypes(event, flags);
            sendAccessibilityEventUnchecked(event);
        }
    }

    void dispatchPendingImportantForAccessibilityChanges() {
        for (int i = mPendingAccessibilityImportanceChange.size() - 1; i >= 0; i--) {
            ViewHolder viewHolder = mPendingAccessibilityImportanceChange.get(i);
            if (viewHolder.itemView.getParent() != this || viewHolder.shouldIgnore()) {
                continue;
            }
            int state = viewHolder.mPendingAccessibilityState;
            if (state != ViewHolder.PENDING_ACCESSIBILITY_STATE_NOT_SET) {
                //noinspection WrongConstant
                ViewCompat.setImportantForAccessibility(viewHolder.itemView, state);
                viewHolder.mPendingAccessibilityState =
                        ViewHolder.PENDING_ACCESSIBILITY_STATE_NOT_SET;
            }
        }
        mPendingAccessibilityImportanceChange.clear();
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
        // 简单动画是高级动画的子集 (这会导致预先布局步骤)
        //
        // If layout supports predictive animations, pre-process to decide if we want to run them
        // 如果布局支持预测动画, 则预过程决定我们是否要运行它们
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

    /**
     * 预测项目动画是否可用
     * @return
     */
    private boolean predictiveItemAnimationsEnabled() {
        return (mItemAnimator != null && mLayout.supportsPredictiveItemAnimations());
    }

    final void fillRemainingScrollValues(State state) {
        if (getScrollState() == SCROLL_STATE_SETTLING) {
            final OverScroller scroller = mViewFlinger.getScroller();
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
