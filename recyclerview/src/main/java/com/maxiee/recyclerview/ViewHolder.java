package com.maxiee.recyclerview;

import android.support.annotation.VisibleForTesting;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.maxiee.recyclerview.RecyclerViewConstants.INVALID_TYPE;
import static com.maxiee.recyclerview.RecyclerViewConstants.NO_ID;
import static com.maxiee.recyclerview.RecyclerViewConstants.NO_POSITION;

public abstract class ViewHolder {
    public final View itemView;

    WeakReference<RecyclerView> mNestedRecyclerView;

    int mPosition = NO_POSITION;
    int mOldPosition = NO_POSITION;
    long mItemId = NO_ID;
    int mItemViewType = INVALID_TYPE;
    int mPreLayoutPosition = NO_POSITION;

    // The item that this holder is shadowing during an item change event/animation
    ViewHolder mShadowedHolder = null;
    // The item that is shadowing this holder during an item change event/animation
    ViewHolder mShadowingHolder = null;

    /**
     * This ViewHolder has been bound to a position; mPosition, mItemId and mItemViewType
     * are all valid.
     */
    static final int FLAG_BOUND = 1 << 0;

    /**
     * The data this ViewHolder's view reflects is stale and needs to be rebound
     * by the adapter. mPosition and mItemId are consistent.
     */
    static final int FLAG_UPDATE = 1 << 1;

    /**
     * This ViewHolder's data is invalid. The identity implied by mPosition and mItemId
     * are not to be trusted and may no longer match the item view type.
     * This ViewHolder must be fully rebound to different data.
     */
    static final int FLAG_INVALID = 1 << 2;

    /**
     * This ViewHolder points at data that represents an item previously removed from the
     * data set. Its view may still be used for things like outgoing animations.
     */
    static final int FLAG_REMOVED = 1 << 3;

    /**
     * This ViewHolder should not be recycled. This flag is set via setIsRecyclable()
     * and is intended to keep views around during animations.
     */
    static final int FLAG_NOT_RECYCLABLE = 1 << 4;

    /**
     * This ViewHolder is returned from scrap which means we are expecting an addView call
     * for this itemView. When returned from scrap, ViewHolder stays in the scrap list until
     * the end of the layout pass and then recycled by RecyclerView if it is not added back to
     * the RecyclerView.
     */
    static final int FLAG_RETURNED_FROM_SCRAP = 1 << 5;

    /**
     * This ViewHolder is fully managed by the LayoutManager. We do not scrap, recycle or remove
     * it unless LayoutManager is replaced.
     * It is still fully visible to the LayoutManager.
     */
    static final int FLAG_IGNORE = 1 << 7;

    /**
     * When the View is detached form the parent, we set this flag so that we can take correct
     * action when we need to remove it or add it back.
     */
    static final int FLAG_TMP_DETACHED = 1 << 8;

    /**
     * Set when we can no longer determine the adapter position of this ViewHolder until it is
     * rebound to a new position. It is different than FLAG_INVALID because FLAG_INVALID is
     * set even when the type does not match. Also, FLAG_ADAPTER_POSITION_UNKNOWN is set as soon
     * as adapter notification arrives vs FLAG_INVALID is set lazily before layout is
     * re-calculated.
     */
    static final int FLAG_ADAPTER_POSITION_UNKNOWN = 1 << 9;

    /**
     * Set when a addChangePayload(null) is called
     */
    static final int FLAG_ADAPTER_FULLUPDATE = 1 << 10;

    /**
     * Used by ItemAnimator when a ViewHolder's position changes
     */
    static final int FLAG_MOVED = 1 << 11;

    /**
     * Used by ItemAnimator when a ViewHolder appears in pre-layout
     */
    static final int FLAG_APPEARED_IN_PRE_LAYOUT = 1 << 12;

    static final int PENDING_ACCESSIBILITY_STATE_NOT_SET = -1;

    /**
     * Used when a ViewHolder starts the layout pass as a hidden ViewHolder but is re-used from
     * hidden list (as if it was scrap) without being recycled in between.
     *
     * When a ViewHolder is hidden, there are 2 paths it can be re-used:
     *   a) Animation ends, view is recycled and used from the recycle pool.
     *   b) LayoutManager asks for the View for that position while the ViewHolder is hidden.
     *
     * This flag is used to represent "case b" where the ViewHolder is reused without being
     * recycled (thus "bounced" from the hidden list). This state requires special handling
     * because the ViewHolder must be added to pre layout maps for animations as if it was
     * already there.
     */
    static final int FLAG_BOUNCED_FROM_HIDDEN_LIST = 1 << 13;

    /**
     * Flags that RecyclerView assigned {@link RecyclerViewAccessibilityDelegate
     * #getItemDelegate()} in onBindView when app does not provide a delegate.
     */
    static final int FLAG_SET_A11Y_ITEM_DELEGATE = 1 << 14;

    private int mFlags;

    private static final List<Object> FULLUPDATE_PAYLOADS = Collections.EMPTY_LIST;

    List<Object> mPayloads = null;
    List<Object> mUnmodifiedPayloads = null;

    private int mIsRecyclableCount = 0;

    // If non-null, view is currently considered scrap and may be reused for other data by the
    // scrap container.
    private Recycler mScrapContainer = null;
    // Keeps whether this ViewHolder lives in Change scrap or Attached scrap
    private boolean mInChangeScrap = false;

    // Saves isImportantForAccessibility value for the view item while it's in hidden state and
    // marked as unimportant for accessibility.
    private int mWasImportantForAccessibilityBeforeHidden =
            ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    // set if we defer the accessibility state change of the view holder
    @VisibleForTesting
    int mPendingAccessibilityState = PENDING_ACCESSIBILITY_STATE_NOT_SET;

    /**
     * Is set when VH is bound from the adapter and cleaned right before it is sent to
     * {@link RecycledViewPool}.
     */
    RecyclerView mOwnerRecyclerView;

    public ViewHolder(View itemView) {
        if (itemView == null) {
            throw new IllegalArgumentException("itemView may not be null");
        }
        this.itemView = itemView;
    }

    void flagRemovedAndOffsetPosition(int mNewPosition, int offset, boolean applyToPreLayout) {
        addFlags(ViewHolder.FLAG_REMOVED);
        offsetPosition(offset, applyToPreLayout);
        mPosition = mNewPosition;
    }

    void offsetPosition(int offset, boolean applyToPreLayout) {
        if (mOldPosition == NO_POSITION) {
            mOldPosition = mPosition;
        }
        if (mPreLayoutPosition == NO_POSITION) {
            mPreLayoutPosition = mPosition;
        }
        if (applyToPreLayout) {
            mPreLayoutPosition += offset;
        }
        mPosition += offset;
        if (itemView.getLayoutParams() != null) {
            ((LayoutParams) itemView.getLayoutParams()).mInsetsDirty = true;
        }
    }

    void clearOldPosition() {
        mOldPosition = NO_POSITION;
        mPreLayoutPosition = NO_POSITION;
    }

    void saveOldPosition() {
        if (mOldPosition == NO_POSITION) {
            mOldPosition = mPosition;
        }
    }

    boolean shouldIgnore() {
        return (mFlags & FLAG_IGNORE) != 0;
    }

    /**
     * @deprecated This method is deprecated because its meaning is ambiguous due to the async
     * handling of adapter updates. Please use {@link #getLayoutPosition()} or
     * {@link #getAdapterPosition()} depending on your use case.
     *
     * @see #getLayoutPosition()
     * @see #getAdapterPosition()
     */
    @Deprecated
    public final int getPosition() {
        return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
    }

    /**
     * Returns the position of the ViewHolder in terms of the latest layout pass.
     * <p>
     * This position is mostly used by RecyclerView components to be consistent while
     * RecyclerView lazily processes adapter updates.
     * <p>
     * For performance and animation reasons, RecyclerView batches all adapter updates until the
     * next layout pass. This may cause mismatches between the Adapter position of the item and
     * the position it had in the latest layout calculations.
     * <p>
     * LayoutManagers should always call this method while doing calculations based on item
     * positions. All methods in {@link RecyclerView.LayoutManager}, {@link RecyclerView.State},
     * {@link RecyclerView.Recycler} that receive a position expect it to be the layout position
     * of the item.
     * <p>
     * If LayoutManager needs to call an external method that requires the adapter position of
     * the item, it can use {@link #getAdapterPosition()} or
     * {@link RecyclerView.Recycler#convertPreLayoutPositionToPostLayout(int)}.
     *
     * @return Returns the adapter position of the ViewHolder in the latest layout pass.
     * @see #getAdapterPosition()
     */
    public final int getLayoutPosition() {
        return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
    }

    /**
     * Returns the Adapter position of the item represented by this ViewHolder.
     * <p>
     * Note that this might be different than the {@link #getLayoutPosition()} if there are
     * pending adapter updates but a new layout pass has not happened yet.
     * <p>
     * RecyclerView does not handle any adapter updates until the next layout traversal. This
     * may create temporary inconsistencies between what user sees on the screen and what
     * adapter contents have. This inconsistency is not important since it will be less than
     * 16ms but it might be a problem if you want to use ViewHolder position to access the
     * adapter. Sometimes, you may need to get the exact adapter position to do
     * some actions in response to user events. In that case, you should use this method which
     * will calculate the Adapter position of the ViewHolder.
     * <p>
     * Note that if you've called {@link RecyclerView.Adapter#notifyDataSetChanged()}, until the
     * next layout pass, the return value of this method will be {@link #NO_POSITION}.
     *
     * @return The adapter position of the item if it still exists in the adapter.
     * {@link RecyclerView#NO_POSITION} if item has been removed from the adapter,
     * {@link RecyclerView.Adapter#notifyDataSetChanged()} has been called after the last
     * layout pass or the ViewHolder has already been recycled.
     */
    public final int getAdapterPosition() {
        if (mOwnerRecyclerView == null) {
            return NO_POSITION;
        }
        return mOwnerRecyclerView.getAdapterPositionFor(this);
    }

    /**
     * When LayoutManager supports animations, RecyclerView tracks 3 positions for ViewHolders
     * to perform animations.
     * <p>
     * If a ViewHolder was laid out in the previous onLayout call, old position will keep its
     * adapter index in the previous layout.
     *
     * @return The previous adapter index of the Item represented by this ViewHolder or
     * {@link #NO_POSITION} if old position does not exists or cleared (pre-layout is
     * complete).
     */
    public final int getOldPosition() {
        return mOldPosition;
    }

    /**
     * Returns The itemId represented by this ViewHolder.
     *
     * @return The item's id if adapter has stable ids, {@link RecyclerView#NO_ID}
     * otherwise
     */
    public final long getItemId() {
        return mItemId;
    }

    /**
     * @return The view type of this ViewHolder.
     */
    public final int getItemViewType() {
        return mItemViewType;
    }

    boolean isScrap() {
        return mScrapContainer != null;
    }

    void unScrap() {
        mScrapContainer.unscrapView(this);
    }

    boolean wasReturnedFromScrap() {
        return (mFlags & FLAG_RETURNED_FROM_SCRAP) != 0;
    }

    void clearReturnedFromScrapFlag() {
        mFlags = mFlags & ~FLAG_RETURNED_FROM_SCRAP;
    }

    void clearTmpDetachFlag() {
        mFlags = mFlags & ~FLAG_TMP_DETACHED;
    }

    void stopIgnoring() {
        mFlags = mFlags & ~FLAG_IGNORE;
    }

    void setScrapContainer(Recycler recycler, boolean isChangeScrap) {
        mScrapContainer = recycler;
        mInChangeScrap = isChangeScrap;
    }

    boolean isInvalid() {
        return (mFlags & FLAG_INVALID) != 0;
    }

    boolean needsUpdate() {
        return (mFlags & FLAG_UPDATE) != 0;
    }

    boolean isBound() {
        return (mFlags & FLAG_BOUND) != 0;
    }

    boolean isRemoved() {
        return (mFlags & FLAG_REMOVED) != 0;
    }

    boolean hasAnyOfTheFlags(int flags) {
        return (mFlags & flags) != 0;
    }

    boolean isTmpDetached() {
        return (mFlags & FLAG_TMP_DETACHED) != 0;
    }

    boolean isAdapterPositionUnknown() {
        return (mFlags & FLAG_ADAPTER_POSITION_UNKNOWN) != 0 || isInvalid();
    }

    void setFlags(int flags, int mask) {
        mFlags = (mFlags & ~mask) | (flags & mask);
    }

    void addFlags(int flags) {
        mFlags |= flags;
    }

    void addChangePayload(Object payload) {
        if (payload == null) {
            addFlags(FLAG_ADAPTER_FULLUPDATE);
        } else if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
            createPayloadsIfNeeded();
            mPayloads.add(payload);
        }
    }

    private void createPayloadsIfNeeded() {
        if (mPayloads == null) {
            mPayloads = new ArrayList<Object>();
            mUnmodifiedPayloads = Collections.unmodifiableList(mPayloads);
        }
    }

    void clearPayload() {
        if (mPayloads != null) {
            mPayloads.clear();
        }
        mFlags = mFlags & ~FLAG_ADAPTER_FULLUPDATE;
    }

    List<Object> getUnmodifiedPayloads() {
        if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
            if (mPayloads == null || mPayloads.size() == 0) {
                // Initial state,  no update being called.
                return FULLUPDATE_PAYLOADS;
            }
            // there are none-null payloads
            return mUnmodifiedPayloads;
        } else {
            // a full update has been called.
            return FULLUPDATE_PAYLOADS;
        }
    }

    void resetInternal() {
        mFlags = 0;
        mPosition = NO_POSITION;
        mOldPosition = NO_POSITION;
        mItemId = NO_ID;
        mPreLayoutPosition = NO_POSITION;
        mIsRecyclableCount = 0;
        mShadowedHolder = null;
        mShadowingHolder = null;
        clearPayload();
        mWasImportantForAccessibilityBeforeHidden = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        mPendingAccessibilityState = PENDING_ACCESSIBILITY_STATE_NOT_SET;
        clearNestedRecyclerViewIfNotNested(this);
    }

    /**
     * Called when the child view enters the hidden state
     */
    private void onEnteredHiddenState(RecyclerView parent) {
        // While the view item is in hidden state, make it invisible for the accessibility.
        mWasImportantForAccessibilityBeforeHidden =
                ViewCompat.getImportantForAccessibility(itemView);
        parent.setChildImportantForAccessibilityInternal(this,
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    /**
     * Called when the child view leaves the hidden state
     */
    private void onLeftHiddenState(RecyclerView parent) {
        parent.setChildImportantForAccessibilityInternal(this,
                mWasImportantForAccessibilityBeforeHidden);
        mWasImportantForAccessibilityBeforeHidden = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ViewHolder{"
                + Integer.toHexString(hashCode()) + " position=" + mPosition + " id=" + mItemId
                + ", oldPos=" + mOldPosition + ", pLpos:" + mPreLayoutPosition);
        if (isScrap()) {
            sb.append(" scrap ")
                    .append(mInChangeScrap ? "[changeScrap]" : "[attachedScrap]");
        }
        if (isInvalid()) sb.append(" invalid");
        if (!isBound()) sb.append(" unbound");
        if (needsUpdate()) sb.append(" update");
        if (isRemoved()) sb.append(" removed");
        if (shouldIgnore()) sb.append(" ignored");
        if (isTmpDetached()) sb.append(" tmpDetached");
        if (!isRecyclable()) sb.append(" not recyclable(" + mIsRecyclableCount + ")");
        if (isAdapterPositionUnknown()) sb.append(" undefined adapter position");

        if (itemView.getParent() == null) sb.append(" no parent");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Informs the recycler whether this item can be recycled. Views which are not
     * recyclable will not be reused for other items until setIsRecyclable() is
     * later set to true. Calls to setIsRecyclable() should always be paired (one
     * call to setIsRecyclabe(false) should always be matched with a later call to
     * setIsRecyclable(true)). Pairs of calls may be nested, as the state is internally
     * reference-counted.
     *
     * @param recyclable Whether this item is available to be recycled. Default value
     * is true.
     *
     * @see #isRecyclable()
     */
    public final void setIsRecyclable(boolean recyclable) {
        mIsRecyclableCount = recyclable ? mIsRecyclableCount - 1 : mIsRecyclableCount + 1;
        if (mIsRecyclableCount < 0) {
            mIsRecyclableCount = 0;
            if (DEBUG) {
                throw new RuntimeException("isRecyclable decremented below 0: "
                        + "unmatched pair of setIsRecyable() calls for " + this);
            }
            Log.e(VIEW_LOG_TAG, "isRecyclable decremented below 0: "
                    + "unmatched pair of setIsRecyable() calls for " + this);
        } else if (!recyclable && mIsRecyclableCount == 1) {
            mFlags |= FLAG_NOT_RECYCLABLE;
        } else if (recyclable && mIsRecyclableCount == 0) {
            mFlags &= ~FLAG_NOT_RECYCLABLE;
        }
        if (DEBUG) {
            Log.d(TAG, "setIsRecyclable val:" + recyclable + ":" + this);
        }
    }

    /**
     * @return true if this item is available to be recycled, false otherwise.
     *
     * @see #setIsRecyclable(boolean)
     */
    public final boolean isRecyclable() {
        return (mFlags & FLAG_NOT_RECYCLABLE) == 0
                && !ViewCompat.hasTransientState(itemView);
    }

    /**
     * Returns whether we have animations referring to this view holder or not.
     * This is similar to isRecyclable flag but does not check transient state.
     */
    private boolean shouldBeKeptAsChild() {
        return (mFlags & FLAG_NOT_RECYCLABLE) != 0;
    }

    /**
     * @return True if ViewHolder is not referenced by RecyclerView animations but has
     * transient state which will prevent it from being recycled.
     */
    private boolean doesTransientStatePreventRecycling() {
        return (mFlags & FLAG_NOT_RECYCLABLE) == 0 && ViewCompat.hasTransientState(itemView);
    }

    boolean isUpdated() {
        return (mFlags & FLAG_UPDATE) != 0;
    }
}
