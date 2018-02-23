package com.maxiee.recyclerview;

import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.maxiee.recyclerview.RecyclerViewConstants.DEBUG;
import static com.maxiee.recyclerview.RecyclerViewConstants.TAG;

/**
 * A Recycler is responsible for managing scrapped or detached item views for reuse.
 * Recycler 的职责是管理已销毁的或者已分离的项目视图, 用于重用.
 *
 * <p>A "scrapped" view is a view that is still attached to its parent RecyclerView but
 * that has been marked for removal or reuse.</p>
 *
 * <p>Typical use of a Recycler by a {@link LayoutManager} will be to obtain views for
 * an adapter's data set representing the data at a given position or item ID.
 * If the view to be reused is considered "dirty" the adapter will be asked to rebind it.
 * If not, the view can be quickly reused by the LayoutManager with no further work.
 * Clean views that have not {@link android.view.View#isLayoutRequested() requested layout}
 * may be repositioned by a LayoutManager without remeasurement.</p>
 */
public class Recycler {
    final ArrayList<ViewHolder> mAttachedScrap = new ArrayList<>();
    ArrayList<ViewHolder> mChangedScrap = null;

    final ArrayList<ViewHolder> mCachedViews = new ArrayList<ViewHolder>();

    private final List<ViewHolder>
            mUnmodifiableAttachedScrap = Collections.unmodifiableList(mAttachedScrap);

    private int mRequestedCacheMax = DEFAULT_CACHE_SIZE;
    int mViewCacheMax = DEFAULT_CACHE_SIZE;

    RecycledViewPool mRecyclerPool;

    private ViewCacheExtension mViewCacheExtension;

    static final int DEFAULT_CACHE_SIZE = 2;

    /**
     * Clear scrap views out of this recycler. Detached views contained within a
     * recycled view pool will remain.
     */
    public void clear() {
        mAttachedScrap.clear();
        recycleAndClearCachedViews();
    }

    /**
     * Set the maximum number of detached, valid views we should retain for later use.
     *
     * @param viewCount Number of views to keep before sending views to the shared pool
     */
    public void setViewCacheSize(int viewCount) {
        mRequestedCacheMax = viewCount;
        updateViewCacheSize();
    }

    void updateViewCacheSize() {
        int extraCache = mLayout != null ? mLayout.mPrefetchMaxCountObserved : 0;
        mViewCacheMax = mRequestedCacheMax + extraCache;

        // first, try the views that can be recycled
        for (int i = mCachedViews.size() - 1;
             i >= 0 && mCachedViews.size() > mViewCacheMax; i--) {
            recycleCachedViewAt(i);
        }
    }

    /**
     * Returns an unmodifiable list of ViewHolders that are currently in the scrap list.
     *
     * @return List of ViewHolders in the scrap list.
     */
    public List<ViewHolder> getScrapList() {
        return mUnmodifiableAttachedScrap;
    }

    /**
     * Helper method for getViewForPosition.
     * <p>
     * Checks whether a given view holder can be used for the provided position.
     *
     * @param holder ViewHolder
     * @return true if ViewHolder matches the provided position, false otherwise
     */
    boolean validateViewHolderForOffsetPosition(ViewHolder holder) {
        // if it is a removed holder, nothing to verify since we cannot ask adapter anymore
        // if it is not removed, verify the type and id.
        if (holder.isRemoved()) {
            if (DEBUG && !mState.isPreLayout()) {
                throw new IllegalStateException("should not receive a removed view unless it"
                        + " is pre layout" + exceptionLabel());
            }
            return mState.isPreLayout();
        }
        if (holder.mPosition < 0 || holder.mPosition >= mAdapter.getItemCount()) {
            throw new IndexOutOfBoundsException("Inconsistency detected. Invalid view holder "
                    + "adapter position" + holder + exceptionLabel());
        }
        if (!mState.isPreLayout()) {
            // don't check type if it is pre-layout.
            final int type = mAdapter.getItemViewType(holder.mPosition);
            if (type != holder.getItemViewType()) {
                return false;
            }
        }
        if (mAdapter.hasStableIds()) {
            return holder.getItemId() == mAdapter.getItemId(holder.mPosition);
        }
        return true;
    }

    /**
     * Attempts to bind view, and account for relevant timing information. If
     * deadlineNs != FOREVER_NS, this method may fail to bind, and return false.
     *
     * @param holder Holder to be bound.
     * @param offsetPosition Position of item to be bound.
     * @param position Pre-layout position of item to be bound.
     * @param deadlineNs Time, relative to getNanoTime(), by which bind/create work should
     *                   complete. If FOREVER_NS is passed, this method will not fail to
     *                   bind the holder.
     * @return
     */
    private boolean tryBindViewHolderByDeadline(ViewHolder holder, int offsetPosition,
                                                int position, long deadlineNs) {
        holder.mOwnerRecyclerView = RecyclerView.this;
        final int viewType = holder.getItemViewType();
        long startBindNs = getNanoTime();
        if (deadlineNs != FOREVER_NS
                && !mRecyclerPool.willBindInTime(viewType, startBindNs, deadlineNs)) {
            // abort - we have a deadline we can't meet
            return false;
        }
        mAdapter.bindViewHolder(holder, offsetPosition);
        long endBindNs = getNanoTime();
        mRecyclerPool.factorInBindTime(holder.getItemViewType(), endBindNs - startBindNs);
        attachAccessibilityDelegateOnBind(holder);
        if (mState.isPreLayout()) {
            holder.mPreLayoutPosition = position;
        }
        return true;
    }

    /**
     * Binds the given View to the position. The View can be a View previously retrieved via
     * {@link #getViewForPosition(int)} or created by
     * {@link Adapter#onCreateViewHolder(ViewGroup, int)}.
     * <p>
     * Generally, a LayoutManager should acquire its views via {@link #getViewForPosition(int)}
     * and let the RecyclerView handle caching. This is a helper method for LayoutManager who
     * wants to handle its own recycling logic.
     * <p>
     * Note that, {@link #getViewForPosition(int)} already binds the View to the position so
     * you don't need to call this method unless you want to bind this View to another position.
     *
     * @param view The view to update.
     * @param position The position of the item to bind to this View.
     */
    public void bindViewToPosition(View view, int position) {
        ViewHolder holder = getChildViewHolderInt(view);
        if (holder == null) {
            throw new IllegalArgumentException("The view does not have a ViewHolder. You cannot"
                    + " pass arbitrary views to this method, they should be created by the "
                    + "Adapter" + exceptionLabel());
        }
        final int offsetPosition = mAdapterHelper.findPositionOffset(position);
        if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
            throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item "
                    + "position " + position + "(offset:" + offsetPosition + ")."
                    + "state:" + mState.getItemCount() + exceptionLabel());
        }
        tryBindViewHolderByDeadline(holder, offsetPosition, position, FOREVER_NS);

        final ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        final LayoutParams rvLayoutParams;
        if (lp == null) {
            rvLayoutParams = (LayoutParams) generateDefaultLayoutParams();
            holder.itemView.setLayoutParams(rvLayoutParams);
        } else if (!checkLayoutParams(lp)) {
            rvLayoutParams = (LayoutParams) generateLayoutParams(lp);
            holder.itemView.setLayoutParams(rvLayoutParams);
        } else {
            rvLayoutParams = (LayoutParams) lp;
        }

        rvLayoutParams.mInsetsDirty = true;
        rvLayoutParams.mViewHolder = holder;
        rvLayoutParams.mPendingInvalidate = holder.itemView.getParent() == null;
    }

    /**
     * RecyclerView provides artificial position range (item count) in pre-layout state and
     * automatically maps these positions to {@link Adapter} positions when
     * {@link #getViewForPosition(int)} or {@link #bindViewToPosition(View, int)} is called.
     * <p>
     * Usually, LayoutManager does not need to worry about this. However, in some cases, your
     * LayoutManager may need to call some custom component with item positions in which
     * case you need the actual adapter position instead of the pre layout position. You
     * can use this method to convert a pre-layout position to adapter (post layout) position.
     * <p>
     * Note that if the provided position belongs to a deleted ViewHolder, this method will
     * return -1.
     * <p>
     * Calling this method in post-layout state returns the same value back.
     *
     * @param position The pre-layout position to convert. Must be greater or equal to 0 and
     *                 less than {@link State#getItemCount()}.
     */
    public int convertPreLayoutPositionToPostLayout(int position) {
        if (position < 0 || position >= mState.getItemCount()) {
            throw new IndexOutOfBoundsException("invalid position " + position + ". State "
                    + "item count is " + mState.getItemCount() + exceptionLabel());
        }
        if (!mState.isPreLayout()) {
            return position;
        }
        return mAdapterHelper.findPositionOffset(position);
    }

    /**
     * Obtain a view initialized for the given position.
     *
     * This method should be used by {@link LayoutManager} implementations to obtain
     * views to represent data from an {@link Adapter}.
     * <p>
     * The Recycler may reuse a scrap or detached view from a shared pool if one is
     * available for the correct view type. If the adapter has not indicated that the
     * data at the given position has changed, the Recycler will attempt to hand back
     * a scrap view that was previously initialized for that data without rebinding.
     *
     * @param position Position to obtain a view for
     * @return A view representing the data at <code>position</code> from <code>adapter</code>
     */
    public View getViewForPosition(int position) {
        return getViewForPosition(position, false);
    }

    View getViewForPosition(int position, boolean dryRun) {
        return tryGetViewHolderForPositionByDeadline(position, dryRun, FOREVER_NS).itemView;
    }

    /**
     * Attempts to get the ViewHolder for the given position, either from the Recycler scrap,
     * cache, the RecycledViewPool, or creating it directly.
     * <p>
     * If a deadlineNs other than {@link #FOREVER_NS} is passed, this method early return
     * rather than constructing or binding a ViewHolder if it doesn't think it has time.
     * If a ViewHolder must be constructed and not enough time remains, null is returned. If a
     * ViewHolder is aquired and must be bound but not enough time remains, an unbound holder is
     * returned. Use {@link ViewHolder#isBound()} on the returned object to check for this.
     *
     * @param position Position of ViewHolder to be returned.
     * @param dryRun True if the ViewHolder should not be removed from scrap/cache/
     * @param deadlineNs Time, relative to getNanoTime(), by which bind/create work should
     *                   complete. If FOREVER_NS is passed, this method will not fail to
     *                   create/bind the holder if needed.
     *
     * @return ViewHolder for requested position
     */
    @Nullable
    ViewHolder tryGetViewHolderForPositionByDeadline(int position,
                                                     boolean dryRun, long deadlineNs) {
        if (position < 0 || position >= mState.getItemCount()) {
            throw new IndexOutOfBoundsException("Invalid item position " + position
                    + "(" + position + "). Item count:" + mState.getItemCount()
                    + exceptionLabel());
        }
        boolean fromScrapOrHiddenOrCache = false;
        ViewHolder holder = null;
        // 0) If there is a changed scrap, try to find from there
        if (mState.isPreLayout()) {
            holder = getChangedScrapViewForPosition(position);
            fromScrapOrHiddenOrCache = holder != null;
        }
        // 1) Find by position from scrap/hidden list/cache
        if (holder == null) {
            holder = getScrapOrHiddenOrCachedHolderForPosition(position, dryRun);
            if (holder != null) {
                if (!validateViewHolderForOffsetPosition(holder)) {
                    // recycle holder (and unscrap if relevant) since it can't be used
                    if (!dryRun) {
                        // we would like to recycle this but need to make sure it is not used by
                        // animation logic etc.
                        holder.addFlags(ViewHolder.FLAG_INVALID);
                        if (holder.isScrap()) {
                            removeDetachedView(holder.itemView, false);
                            holder.unScrap();
                        } else if (holder.wasReturnedFromScrap()) {
                            holder.clearReturnedFromScrapFlag();
                        }
                        recycleViewHolderInternal(holder);
                    }
                    holder = null;
                } else {
                    fromScrapOrHiddenOrCache = true;
                }
            }
        }
        if (holder == null) {
            final int offsetPosition = mAdapterHelper.findPositionOffset(position);
            if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
                throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item "
                        + "position " + position + "(offset:" + offsetPosition + ")."
                        + "state:" + mState.getItemCount() + exceptionLabel());
            }

            final int type = mAdapter.getItemViewType(offsetPosition);
            // 2) Find from scrap/cache via stable ids, if exists
            if (mAdapter.hasStableIds()) {
                holder = getScrapOrCachedViewForId(mAdapter.getItemId(offsetPosition),
                        type, dryRun);
                if (holder != null) {
                    // update position
                    holder.mPosition = offsetPosition;
                    fromScrapOrHiddenOrCache = true;
                }
            }
            if (holder == null && mViewCacheExtension != null) {
                // We are NOT sending the offsetPosition because LayoutManager does not
                // know it.
                final View view = mViewCacheExtension
                        .getViewForPositionAndType(this, position, type);
                if (view != null) {
                    holder = getChildViewHolder(view);
                    if (holder == null) {
                        throw new IllegalArgumentException("getViewForPositionAndType returned"
                                + " a view which does not have a ViewHolder"
                                + exceptionLabel());
                    } else if (holder.shouldIgnore()) {
                        throw new IllegalArgumentException("getViewForPositionAndType returned"
                                + " a view that is ignored. You must call stopIgnoring before"
                                + " returning this view." + exceptionLabel());
                    }
                }
            }
            if (holder == null) { // fallback to pool
                if (DEBUG) {
                    Log.d(TAG, "tryGetViewHolderForPositionByDeadline("
                            + position + ") fetching from shared pool");
                }
                holder = getRecycledViewPool().getRecycledView(type);
                if (holder != null) {
                    holder.resetInternal();
                    if (FORCE_INVALIDATE_DISPLAY_LIST) {
                        invalidateDisplayListInt(holder);
                    }
                }
            }
            if (holder == null) {
                long start = getNanoTime();
                if (deadlineNs != FOREVER_NS
                        && !mRecyclerPool.willCreateInTime(type, start, deadlineNs)) {
                    // abort - we have a deadline we can't meet
                    return null;
                }
                holder = mAdapter.createViewHolder(RecyclerView.this, type);
                if (ALLOW_THREAD_GAP_WORK) {
                    // only bother finding nested RV if prefetching
                    RecyclerView innerView = findNestedRecyclerView(holder.itemView);
                    if (innerView != null) {
                        holder.mNestedRecyclerView = new WeakReference<>(innerView);
                    }
                }

                long end = getNanoTime();
                mRecyclerPool.factorInCreateTime(type, end - start);
                if (DEBUG) {
                    Log.d(TAG, "tryGetViewHolderForPositionByDeadline created new ViewHolder");
                }
            }
        }

        // This is very ugly but the only place we can grab this information
        // before the View is rebound and returned to the LayoutManager for post layout ops.
        // We don't need this in pre-layout since the VH is not updated by the LM.
        if (fromScrapOrHiddenOrCache && !mState.isPreLayout() && holder
                .hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST)) {
            holder.setFlags(0, ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
            if (mState.mRunSimpleAnimations) {
                int changeFlags = ItemAnimator
                        .buildAdapterChangeFlagsForAnimations(holder);
                changeFlags |= ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
                final ItemHolderInfo info = mItemAnimator.recordPreLayoutInformation(mState,
                        holder, changeFlags, holder.getUnmodifiedPayloads());
                recordAnimationInfoIfBouncedHiddenView(holder, info);
            }
        }

        boolean bound = false;
        if (mState.isPreLayout() && holder.isBound()) {
            // do not update unless we absolutely have to.
            holder.mPreLayoutPosition = position;
        } else if (!holder.isBound() || holder.needsUpdate() || holder.isInvalid()) {
            if (DEBUG && holder.isRemoved()) {
                throw new IllegalStateException("Removed holder should be bound and it should"
                        + " come here only in pre-layout. Holder: " + holder
                        + exceptionLabel());
            }
            final int offsetPosition = mAdapterHelper.findPositionOffset(position);
            bound = tryBindViewHolderByDeadline(holder, offsetPosition, position, deadlineNs);
        }

        final ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        final LayoutParams rvLayoutParams;
        if (lp == null) {
            rvLayoutParams = (LayoutParams) generateDefaultLayoutParams();
            holder.itemView.setLayoutParams(rvLayoutParams);
        } else if (!checkLayoutParams(lp)) {
            rvLayoutParams = (LayoutParams) generateLayoutParams(lp);
            holder.itemView.setLayoutParams(rvLayoutParams);
        } else {
            rvLayoutParams = (LayoutParams) lp;
        }
        rvLayoutParams.mViewHolder = holder;
        rvLayoutParams.mPendingInvalidate = fromScrapOrHiddenOrCache && bound;
        return holder;
    }

    private void attachAccessibilityDelegateOnBind(ViewHolder holder) {
        if (isAccessibilityEnabled()) {
            final View itemView = holder.itemView;
            if (ViewCompat.getImportantForAccessibility(itemView)
                    == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                ViewCompat.setImportantForAccessibility(itemView,
                        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
            if (!ViewCompat.hasAccessibilityDelegate(itemView)) {
                holder.addFlags(ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE);
                ViewCompat.setAccessibilityDelegate(itemView,
                        mAccessibilityDelegate.getItemDelegate());
            }
        }
    }

    private void invalidateDisplayListInt(ViewHolder holder) {
        if (holder.itemView instanceof ViewGroup) {
            invalidateDisplayListInt((ViewGroup) holder.itemView, false);
        }
    }

    private void invalidateDisplayListInt(ViewGroup viewGroup, boolean invalidateThis) {
        for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
            final View view = viewGroup.getChildAt(i);
            if (view instanceof ViewGroup) {
                invalidateDisplayListInt((ViewGroup) view, true);
            }
        }
        if (!invalidateThis) {
            return;
        }
        // we need to force it to become invisible
        if (viewGroup.getVisibility() == View.INVISIBLE) {
            viewGroup.setVisibility(View.VISIBLE);
            viewGroup.setVisibility(View.INVISIBLE);
        } else {
            final int visibility = viewGroup.getVisibility();
            viewGroup.setVisibility(View.INVISIBLE);
            viewGroup.setVisibility(visibility);
        }
    }

    /**
     * Recycle a detached view. The specified view will be added to a pool of views
     * for later rebinding and reuse.
     *
     * <p>A view must be fully detached (removed from parent) before it may be recycled. If the
     * View is scrapped, it will be removed from scrap list.</p>
     *
     * @param view Removed view for recycling
     * @see LayoutManager#removeAndRecycleView(View, Recycler)
     */
    public void recycleView(View view) {
        // This public recycle method tries to make view recycle-able since layout manager
        // intended to recycle this view (e.g. even if it is in scrap or change cache)
        ViewHolder holder = getChildViewHolderInt(view);
        if (holder.isTmpDetached()) {
            removeDetachedView(view, false);
        }
        if (holder.isScrap()) {
            holder.unScrap();
        } else if (holder.wasReturnedFromScrap()) {
            holder.clearReturnedFromScrapFlag();
        }
        recycleViewHolderInternal(holder);
    }

    /**
     * Internally, use this method instead of {@link #recycleView(android.view.View)} to
     * catch potential bugs.
     * @param view
     */
    void recycleViewInternal(View view) {
        recycleViewHolderInternal(getChildViewHolderInt(view));
    }

    void recycleAndClearCachedViews() {
        final int count = mCachedViews.size();
        for (int i = count - 1; i >= 0; i--) {
            recycleCachedViewAt(i);
        }
        mCachedViews.clear();
        if (ALLOW_THREAD_GAP_WORK) {
            mPrefetchRegistry.clearPrefetchPositions();
        }
    }

    /**
     * Recycles a cached view and removes the view from the list. Views are added to cache
     * if and only if they are recyclable, so this method does not check it again.
     * <p>
     * A small exception to this rule is when the view does not have an animator reference
     * but transient state is true (due to animations created outside ItemAnimator). In that
     * case, adapter may choose to recycle it. From RecyclerView's perspective, the view is
     * still recyclable since Adapter wants to do so.
     *
     * @param cachedViewIndex The index of the view in cached views list
     */
    void recycleCachedViewAt(int cachedViewIndex) {
        if (DEBUG) {
            Log.d(TAG, "Recycling cached view at index " + cachedViewIndex);
        }
        ViewHolder viewHolder = mCachedViews.get(cachedViewIndex);
        if (DEBUG) {
            Log.d(TAG, "CachedViewHolder to be recycled: " + viewHolder);
        }
        addViewHolderToRecycledViewPool(viewHolder, true);
        mCachedViews.remove(cachedViewIndex);
    }

    /**
     * internal implementation checks if view is scrapped or attached and throws an exception
     * if so.
     * Public version un-scraps before calling recycle.
     */
    void recycleViewHolderInternal(ViewHolder holder) {
        if (holder.isScrap() || holder.itemView.getParent() != null) {
            throw new IllegalArgumentException(
                    "Scrapped or attached views may not be recycled. isScrap:"
                            + holder.isScrap() + " isAttached:"
                            + (holder.itemView.getParent() != null) + exceptionLabel());
        }

        if (holder.isTmpDetached()) {
            throw new IllegalArgumentException("Tmp detached view should be removed "
                    + "from RecyclerView before it can be recycled: " + holder
                    + exceptionLabel());
        }

        if (holder.shouldIgnore()) {
            throw new IllegalArgumentException("Trying to recycle an ignored view holder. You"
                    + " should first call stopIgnoringView(view) before calling recycle."
                    + exceptionLabel());
        }
        //noinspection unchecked
        final boolean transientStatePreventsRecycling = holder
                .doesTransientStatePreventRecycling();
        final boolean forceRecycle = mAdapter != null
                && transientStatePreventsRecycling
                && mAdapter.onFailedToRecycleView(holder);
        boolean cached = false;
        boolean recycled = false;
        if (DEBUG && mCachedViews.contains(holder)) {
            throw new IllegalArgumentException("cached view received recycle internal? "
                    + holder + exceptionLabel());
        }
        if (forceRecycle || holder.isRecyclable()) {
            if (mViewCacheMax > 0
                    && !holder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID
                    | ViewHolder.FLAG_REMOVED
                    | ViewHolder.FLAG_UPDATE
                    | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN)) {
                // Retire oldest cached view
                int cachedViewSize = mCachedViews.size();
                if (cachedViewSize >= mViewCacheMax && cachedViewSize > 0) {
                    recycleCachedViewAt(0);
                    cachedViewSize--;
                }

                int targetCacheIndex = cachedViewSize;
                if (ALLOW_THREAD_GAP_WORK
                        && cachedViewSize > 0
                        && !mPrefetchRegistry.lastPrefetchIncludedPosition(holder.mPosition)) {
                    // when adding the view, skip past most recently prefetched views
                    int cacheIndex = cachedViewSize - 1;
                    while (cacheIndex >= 0) {
                        int cachedPos = mCachedViews.get(cacheIndex).mPosition;
                        if (!mPrefetchRegistry.lastPrefetchIncludedPosition(cachedPos)) {
                            break;
                        }
                        cacheIndex--;
                    }
                    targetCacheIndex = cacheIndex + 1;
                }
                mCachedViews.add(targetCacheIndex, holder);
                cached = true;
            }
            if (!cached) {
                addViewHolderToRecycledViewPool(holder, true);
                recycled = true;
            }
        } else {
            // NOTE: A view can fail to be recycled when it is scrolled off while an animation
            // runs. In this case, the item is eventually recycled by
            // ItemAnimatorRestoreListener#onAnimationFinished.

            // TODO: consider cancelling an animation when an item is removed scrollBy,
            // to return it to the pool faster
            if (DEBUG) {
                Log.d(TAG, "trying to recycle a non-recycleable holder. Hopefully, it will "
                        + "re-visit here. We are still removing it from animation lists"
                        + exceptionLabel());
            }
        }
        // even if the holder is not removed, we still call this method so that it is removed
        // from view holder lists.
        mViewInfoStore.removeViewHolder(holder);
        if (!cached && !recycled && transientStatePreventsRecycling) {
            holder.mOwnerRecyclerView = null;
        }
    }

    /**
     * Prepares the ViewHolder to be removed/recycled, and inserts it into the RecycledViewPool.
     *
     * Pass false to dispatchRecycled for views that have not been bound.
     *
     * @param holder Holder to be added to the pool.
     * @param dispatchRecycled True to dispatch View recycled callbacks.
     */
    void addViewHolderToRecycledViewPool(ViewHolder holder, boolean dispatchRecycled) {
        clearNestedRecyclerViewIfNotNested(holder);
        if (holder.hasAnyOfTheFlags(ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE)) {
            holder.setFlags(0, ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE);
            ViewCompat.setAccessibilityDelegate(holder.itemView, null);
        }
        if (dispatchRecycled) {
            dispatchViewRecycled(holder);
        }
        holder.mOwnerRecyclerView = null;
        getRecycledViewPool().putRecycledView(holder);
    }

    /**
     * Used as a fast path for unscrapping and recycling a view during a bulk operation.
     * The caller must call {@link #clearScrap()} when it's done to update the recycler's
     * internal bookkeeping.
     */
    void quickRecycleScrapView(View view) {
        final ViewHolder holder = getChildViewHolderInt(view);
        holder.mScrapContainer = null;
        holder.mInChangeScrap = false;
        holder.clearReturnedFromScrapFlag();
        recycleViewHolderInternal(holder);
    }

    /**
     * Mark an attached view as scrap.
     *
     * <p>"Scrap" views are still attached to their parent RecyclerView but are eligible
     * for rebinding and reuse. Requests for a view for a given position may return a
     * reused or rebound scrap view instance.</p>
     *
     * @param view View to scrap
     */
    void scrapView(View view) {
        final ViewHolder holder = getChildViewHolderInt(view);
        if (holder.hasAnyOfTheFlags(ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_INVALID)
                || !holder.isUpdated() || canReuseUpdatedViewHolder(holder)) {
            if (holder.isInvalid() && !holder.isRemoved() && !mAdapter.hasStableIds()) {
                throw new IllegalArgumentException("Called scrap view with an invalid view."
                        + " Invalid views cannot be reused from scrap, they should rebound from"
                        + " recycler pool." + exceptionLabel());
            }
            holder.setScrapContainer(this, false);
            mAttachedScrap.add(holder);
        } else {
            if (mChangedScrap == null) {
                mChangedScrap = new ArrayList<ViewHolder>();
            }
            holder.setScrapContainer(this, true);
            mChangedScrap.add(holder);
        }
    }

    /**
     * Remove a previously scrapped view from the pool of eligible scrap.
     *
     * <p>This view will no longer be eligible for reuse until re-scrapped or
     * until it is explicitly removed and recycled.</p>
     */
    void unscrapView(ViewHolder holder) {
        if (holder.mInChangeScrap) {
            mChangedScrap.remove(holder);
        } else {
            mAttachedScrap.remove(holder);
        }
        holder.mScrapContainer = null;
        holder.mInChangeScrap = false;
        holder.clearReturnedFromScrapFlag();
    }

    int getScrapCount() {
        return mAttachedScrap.size();
    }

    View getScrapViewAt(int index) {
        return mAttachedScrap.get(index).itemView;
    }

    void clearScrap() {
        mAttachedScrap.clear();
        if (mChangedScrap != null) {
            mChangedScrap.clear();
        }
    }

    ViewHolder getChangedScrapViewForPosition(int position) {
        // If pre-layout, check the changed scrap for an exact match.
        final int changedScrapSize;
        if (mChangedScrap == null || (changedScrapSize = mChangedScrap.size()) == 0) {
            return null;
        }
        // find by position
        for (int i = 0; i < changedScrapSize; i++) {
            final ViewHolder holder = mChangedScrap.get(i);
            if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position) {
                holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                return holder;
            }
        }
        // find by id
        if (mAdapter.hasStableIds()) {
            final int offsetPosition = mAdapterHelper.findPositionOffset(position);
            if (offsetPosition > 0 && offsetPosition < mAdapter.getItemCount()) {
                final long id = mAdapter.getItemId(offsetPosition);
                for (int i = 0; i < changedScrapSize; i++) {
                    final ViewHolder holder = mChangedScrap.get(i);
                    if (!holder.wasReturnedFromScrap() && holder.getItemId() == id) {
                        holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                        return holder;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns a view for the position either from attach scrap, hidden children, or cache.
     *
     * @param position Item position
     * @param dryRun  Does a dry run, finds the ViewHolder but does not remove
     * @return a ViewHolder that can be re-used for this position.
     */
    ViewHolder getScrapOrHiddenOrCachedHolderForPosition(int position, boolean dryRun) {
        final int scrapCount = mAttachedScrap.size();

        // Try first for an exact, non-invalid match from scrap.
        for (int i = 0; i < scrapCount; i++) {
            final ViewHolder holder = mAttachedScrap.get(i);
            if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position
                    && !holder.isInvalid() && (mState.mInPreLayout || !holder.isRemoved())) {
                holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                return holder;
            }
        }

        if (!dryRun) {
            View view = mChildHelper.findHiddenNonRemovedView(position);
            if (view != null) {
                // This View is good to be used. We just need to unhide, detach and move to the
                // scrap list.
                final ViewHolder vh = getChildViewHolderInt(view);
                mChildHelper.unhide(view);
                int layoutIndex = mChildHelper.indexOfChild(view);
                if (layoutIndex == RecyclerView.NO_POSITION) {
                    throw new IllegalStateException("layout index should not be -1 after "
                            + "unhiding a view:" + vh + exceptionLabel());
                }
                mChildHelper.detachViewFromParent(layoutIndex);
                scrapView(view);
                vh.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP
                        | ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                return vh;
            }
        }

        // Search in our first-level recycled view cache.
        final int cacheSize = mCachedViews.size();
        for (int i = 0; i < cacheSize; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            // invalid view holders may be in cache if adapter has stable ids as they can be
            // retrieved via getScrapOrCachedViewForId
            if (!holder.isInvalid() && holder.getLayoutPosition() == position) {
                if (!dryRun) {
                    mCachedViews.remove(i);
                }
                if (DEBUG) {
                    Log.d(TAG, "getScrapOrHiddenOrCachedHolderForPosition(" + position
                            + ") found match in cache: " + holder);
                }
                return holder;
            }
        }
        return null;
    }

    ViewHolder getScrapOrCachedViewForId(long id, int type, boolean dryRun) {
        // Look in our attached views first
        final int count = mAttachedScrap.size();
        for (int i = count - 1; i >= 0; i--) {
            final ViewHolder holder = mAttachedScrap.get(i);
            if (holder.getItemId() == id && !holder.wasReturnedFromScrap()) {
                if (type == holder.getItemViewType()) {
                    holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                    if (holder.isRemoved()) {
                        // this might be valid in two cases:
                        // > item is removed but we are in pre-layout pass
                        // >> do nothing. return as is. make sure we don't rebind
                        // > item is removed then added to another position and we are in
                        // post layout.
                        // >> remove removed and invalid flags, add update flag to rebind
                        // because item was invisible to us and we don't know what happened in
                        // between.
                        if (!mState.isPreLayout()) {
                            holder.setFlags(ViewHolder.FLAG_UPDATE, ViewHolder.FLAG_UPDATE
                                    | ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED);
                        }
                    }
                    return holder;
                } else if (!dryRun) {
                    // if we are running animations, it is actually better to keep it in scrap
                    // but this would force layout manager to lay it out which would be bad.
                    // Recycle this scrap. Type mismatch.
                    mAttachedScrap.remove(i);
                    removeDetachedView(holder.itemView, false);
                    quickRecycleScrapView(holder.itemView);
                }
            }
        }

        // Search the first-level cache
        final int cacheSize = mCachedViews.size();
        for (int i = cacheSize - 1; i >= 0; i--) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder.getItemId() == id) {
                if (type == holder.getItemViewType()) {
                    if (!dryRun) {
                        mCachedViews.remove(i);
                    }
                    return holder;
                } else if (!dryRun) {
                    recycleCachedViewAt(i);
                    return null;
                }
            }
        }
        return null;
    }

    void dispatchViewRecycled(ViewHolder holder) {
        if (mRecyclerListener != null) {
            mRecyclerListener.onViewRecycled(holder);
        }
        if (mAdapter != null) {
            mAdapter.onViewRecycled(holder);
        }
        if (mState != null) {
            mViewInfoStore.removeViewHolder(holder);
        }
        if (DEBUG) Log.d(TAG, "dispatchViewRecycled: " + holder);
    }

    void onAdapterChanged(Adapter oldAdapter, Adapter newAdapter,
                          boolean compatibleWithPrevious) {
        clear();
        getRecycledViewPool().onAdapterChanged(oldAdapter, newAdapter, compatibleWithPrevious);
    }

    void offsetPositionRecordsForMove(int from, int to) {
        final int start, end, inBetweenOffset;
        if (from < to) {
            start = from;
            end = to;
            inBetweenOffset = -1;
        } else {
            start = to;
            end = from;
            inBetweenOffset = 1;
        }
        final int cachedCount = mCachedViews.size();
        for (int i = 0; i < cachedCount; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder == null || holder.mPosition < start || holder.mPosition > end) {
                continue;
            }
            if (holder.mPosition == from) {
                holder.offsetPosition(to - from, false);
            } else {
                holder.offsetPosition(inBetweenOffset, false);
            }
            if (DEBUG) {
                Log.d(TAG, "offsetPositionRecordsForMove cached child " + i + " holder "
                        + holder);
            }
        }
    }

    void offsetPositionRecordsForInsert(int insertedAt, int count) {
        final int cachedCount = mCachedViews.size();
        for (int i = 0; i < cachedCount; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder != null && holder.mPosition >= insertedAt) {
                if (DEBUG) {
                    Log.d(TAG, "offsetPositionRecordsForInsert cached " + i + " holder "
                            + holder + " now at position " + (holder.mPosition + count));
                }
                holder.offsetPosition(count, true);
            }
        }
    }

    /**
     * @param removedFrom Remove start index
     * @param count Remove count
     * @param applyToPreLayout If true, changes will affect ViewHolder's pre-layout position, if
     *                         false, they'll be applied before the second layout pass
     */
    void offsetPositionRecordsForRemove(int removedFrom, int count, boolean applyToPreLayout) {
        final int removedEnd = removedFrom + count;
        final int cachedCount = mCachedViews.size();
        for (int i = cachedCount - 1; i >= 0; i--) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder != null) {
                if (holder.mPosition >= removedEnd) {
                    if (DEBUG) {
                        Log.d(TAG, "offsetPositionRecordsForRemove cached " + i
                                + " holder " + holder + " now at position "
                                + (holder.mPosition - count));
                    }
                    holder.offsetPosition(-count, applyToPreLayout);
                } else if (holder.mPosition >= removedFrom) {
                    // Item for this view was removed. Dump it from the cache.
                    holder.addFlags(ViewHolder.FLAG_REMOVED);
                    recycleCachedViewAt(i);
                }
            }
        }
    }

    void setViewCacheExtension(ViewCacheExtension extension) {
        mViewCacheExtension = extension;
    }

    void setRecycledViewPool(RecycledViewPool pool) {
        if (mRecyclerPool != null) {
            mRecyclerPool.detach();
        }
        mRecyclerPool = pool;
        if (pool != null) {
            mRecyclerPool.attach(getAdapter());
        }
    }

    RecycledViewPool getRecycledViewPool() {
        if (mRecyclerPool == null) {
            mRecyclerPool = new RecycledViewPool();
        }
        return mRecyclerPool;
    }

    void viewRangeUpdate(int positionStart, int itemCount) {
        final int positionEnd = positionStart + itemCount;
        final int cachedCount = mCachedViews.size();
        for (int i = cachedCount - 1; i >= 0; i--) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder == null) {
                continue;
            }

            final int pos = holder.mPosition;
            if (pos >= positionStart && pos < positionEnd) {
                holder.addFlags(ViewHolder.FLAG_UPDATE);
                recycleCachedViewAt(i);
                // cached views should not be flagged as changed because this will cause them
                // to animate when they are returned from cache.
            }
        }
    }

    void markKnownViewsInvalid() {
        final int cachedCount = mCachedViews.size();
        for (int i = 0; i < cachedCount; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            if (holder != null) {
                holder.addFlags(ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID);
                holder.addChangePayload(null);
            }
        }

        if (mAdapter == null || !mAdapter.hasStableIds()) {
            // we cannot re-use cached views in this case. Recycle them all
            recycleAndClearCachedViews();
        }
    }

    void clearOldPositions() {
        final int cachedCount = mCachedViews.size();
        for (int i = 0; i < cachedCount; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            holder.clearOldPosition();
        }
        final int scrapCount = mAttachedScrap.size();
        for (int i = 0; i < scrapCount; i++) {
            mAttachedScrap.get(i).clearOldPosition();
        }
        if (mChangedScrap != null) {
            final int changedScrapCount = mChangedScrap.size();
            for (int i = 0; i < changedScrapCount; i++) {
                mChangedScrap.get(i).clearOldPosition();
            }
        }
    }

    void markItemDecorInsetsDirty() {
        final int cachedCount = mCachedViews.size();
        for (int i = 0; i < cachedCount; i++) {
            final ViewHolder holder = mCachedViews.get(i);
            LayoutParams layoutParams = (LayoutParams) holder.itemView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.mInsetsDirty = true;
            }
        }
    }
}
