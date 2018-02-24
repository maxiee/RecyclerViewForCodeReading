package com.maxiee.recyclerview;

import android.support.annotation.IntDef;
import android.util.SparseArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.maxiee.recyclerview.RecyclerViewConstants.NO_POSITION;

/**
 * <p>Contains useful information about the current RecyclerView state like target scroll
 * position or view focus. State object can also keep arbitrary data, identified by resource
 * ids.</p>
 * <p>包含关于当前 RecyclerView 的有用信息, 如目标滚动位置和视图焦点. 状态对象还可以保存由资源 id 标识的任意数据</p>
 *
 * <p>Often times, RecyclerView components will need to pass information between each other.
 * To provide a well defined data bus between components, RecyclerView passes the same State
 * object to component callbacks and these components can use it to exchange data.</p>
 * <p>通常, RecyclerView 组件之间需要相互传递信息. 为了在组件之间提供一个定义良好的数据总线, RecyclerView 将
 * 相同的状态对象传递给组件回调, 这样这些组件可以用它来传递数据</p>
 *
 * <p>If you implement custom components, you can use State's put/get/remove methods to pass
 * data between your components without needing to manage their lifecycles.</p>
 * <p>如果你实现自定义组件, 你可以使用 State 的 put/get/remove 方法来在你的组件间传递数据而无需管理他们的生命周期.</p>
 */
public class State {
    /**
     * 状态一共有 3 种:
     */
    static final int STEP_START = 1;
    static final int STEP_LAYOUT = 1 << 1;
    static final int STEP_ANIMATIONS = 1 << 2;

    /**
     * 验证当前状态是否是给定状态, 如果不是则抛出异常
     * @param accepted
     */
    void assertLayoutStep(int accepted) {
        if ((accepted & mLayoutStep) == 0) {
            throw new IllegalStateException("Layout state should be one of "
                    + Integer.toBinaryString(accepted) + " but it is "
                    + Integer.toBinaryString(mLayoutStep));
        }
    }


    /** Owned by SmoothScroller */
    private int mTargetPosition = NO_POSITION;

    private SparseArray<Object> mData;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // Fields below are carried from one layout pass to the next
    // 下面的属性被从一个布局带到下一个
    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Number of items adapter had in the previous layout.
     * 在之前布局中 adapter 项目的数量
     */
    int mPreviousLayoutItemCount = 0;

    /**
     * Number of items that were NOT laid out but has been deleted from the adapter after the
     * previous layout.
     */
    int mDeletedInvisibleItemCountSincePreviousLayout = 0;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // Fields below must be updated or cleared before they are used (generally before a pass)
    ////////////////////////////////////////////////////////////////////////////////////////////

    @IntDef(flag = true, value = {
            STEP_START, STEP_LAYOUT, STEP_ANIMATIONS
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface LayoutState {}

    @LayoutState
    int mLayoutStep = STEP_START;

    /**
     * Number of items adapter has.
     */
    int mItemCount = 0;

    boolean mStructureChanged = false;

    boolean mInPreLayout = false;

    boolean mTrackOldChangeHolders = false;

    boolean mIsMeasuring = false;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // Fields below are always reset outside of the pass (or passes) that use them
    // 下面的字段总是在 pass (or passes) $HTT$ 之外被重置
    ////////////////////////////////////////////////////////////////////////////////////////////

    boolean mRunSimpleAnimations = false;

    boolean mRunPredictiveAnimations = false;

    /**
     * This data is saved before a layout calculation happens. After the layout is finished,
     * if the previously focused view has been replaced with another view for the same item, we
     * move the focus to the new item automatically.
     */
    int mFocusedItemPosition;
    long mFocusedItemId;
    // when a sub child has focus, record its id and see if we can directly request focus on
    // that one instead
    int mFocusedSubChildId;

    int mRemainingScrollHorizontal; // 横向剩余的滚动量
    int mRemainingScrollVertical;   // 纵向剩余的滚动量

    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 状态复位
     * @return
     */
    State reset() {
        mTargetPosition = NO_POSITION;
        if (mData != null) {
            mData.clear();
        }
        mItemCount = 0;
        mStructureChanged = false;
        mIsMeasuring = false;
        return this;
    }

    /**
     * Prepare for a prefetch occurring on the RecyclerView in between traversals, potentially
     * prior to any layout passes.
     *
     * <p>Don't touch any state stored between layout passes, only reset per-layout state, so
     * that Recycler#getViewForPosition() can function safely.</p>
     */
    void prepareForNestedPrefetch(Adapter adapter) {
        mLayoutStep = STEP_START;
        mItemCount = adapter.getItemCount();
        mInPreLayout = false;
        mTrackOldChangeHolders = false;
        mIsMeasuring = false;
    }

    /**
     * Returns true if the RecyclerView is currently measuring the layout. This value is
     * {@code true} only if the LayoutManager opted into the auto measure API and RecyclerView
     * has non-exact measurement specs.
     * <p>
     * Note that if the LayoutManager supports predictive animations and it is calculating the
     * pre-layout step, this value will be {@code false} even if the RecyclerView is in
     * {@code onMeasure} call. This is because pre-layout means the previous state of the
     * RecyclerView and measurements made for that state cannot change the RecyclerView's size.
     * LayoutManager is always guaranteed to receive another call to
     * {@link LayoutManager#onLayoutChildren(Recycler, State)} when this happens.
     *
     * @return True if the RecyclerView is currently calculating its bounds, false otherwise.
     */
    public boolean isMeasuring() {
        return mIsMeasuring;
    }

    /**
     * Returns true if
     * @return
     */
    public boolean isPreLayout() {
        return mInPreLayout;
    }

    /**
     * Returns whether RecyclerView will run predictive animations in this layout pass
     * or not.
     *
     * @return true if RecyclerView is calculating predictive animations to be run at the end
     *         of the layout pass.
     */
    public boolean willRunPredictiveAnimations() {
        return mRunPredictiveAnimations;
    }

    /**
     * Returns whether RecyclerView will run simple animations in this layout pass
     * or not.
     *
     * @return true if RecyclerView is calculating simple animations to be run at the end of
     *         the layout pass.
     */
    public boolean willRunSimpleAnimations() {
        return mRunSimpleAnimations;
    }

    /**
     * Removes the mapping from the specified id, if there was any.
     * @param resourceId Id of the resource you want to remove. It is suggested to use R.id.* to
     *                   preserve cross functionality and avoid conflicts.
     */
    public void remove(int resourceId) {
        if (mData == null) {
            return;
        }
        mData.remove(resourceId);
    }

    /**
     * Gets the Object mapped from the specified id, or <code>null</code>
     * if no such data exists.
     *
     * @param resourceId Id of the resource you want to remove. It is suggested to use R.id.*
     *                   to
     *                   preserve cross functionality and avoid conflicts.
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T> T get(int resourceId) {
        if (mData == null) {
            return null;
        }
        return (T) mData.get(resourceId);
    }

    /**
     * Adds a mapping from the specified id to the specified value, replacing the previous
     * mapping from the specified key if there was one.
     *
     * @param resourceId Id of the resource you want to add. It is suggested to use R.id.* to
     *                   preserve cross functionality and avoid conflicts.
     * @param data       The data you want to associate with the resourceId.
     */
    public void put(int resourceId, Object data) {
        if (mData == null) {
            mData = new SparseArray<Object>();
        }
        mData.put(resourceId, data);
    }

    /**
     * If scroll is triggered to make a certain item visible, this value will return the
     * adapter index of that item.
     * @return Adapter index of the target item or
     * {@link NO_POSITION} if there is no target
     * position.
     */
    public int getTargetScrollPosition() {
        return mTargetPosition;
    }

    /**
     * Returns if current scroll has a target position.
     * @return true if scroll is being triggered to make a certain position visible
     * @see #getTargetScrollPosition()
     */
    public boolean hasTargetScrollPosition() {
        return mTargetPosition != NO_POSITION;
    }

    /**
     * @return true if the structure of the data set has changed since the last call to
     *         onLayoutChildren, false otherwise
     */
    public boolean didStructureChange() {
        return mStructureChanged;
    }

    /**
     * Returns the total number of items that can be laid out. Note that this number is not
     * necessarily equal to the number of items in the adapter, so you should always use this
     * number for your position calculations and never access the adapter directly.
     * <p>
     * RecyclerView listens for Adapter's notify events and calculates the effects of adapter
     * data changes on existing Views. These calculations are used to decide which animations
     * should be run.
     * <p>
     * To support predictive animations, RecyclerView may rewrite or reorder Adapter changes to
     * present the correct state to LayoutManager in pre-layout pass.
     * <p>
     * For example, a newly added item is not included in pre-layout item count because
     * pre-layout reflects the contents of the adapter before the item is added. Behind the
     * scenes, RecyclerView offsets {@link Recycler#getViewForPosition(int)} calls such that
     * LayoutManager does not know about the new item's existence in pre-layout. The item will
     * be available in second layout pass and will be included in the item count. Similar
     * adjustments are made for moved and removed items as well.
     * <p>
     * You can get the adapter's item count via {@link LayoutManager#getItemCount()} method.
     *
     * @return The number of items currently available
     * @see LayoutManager#getItemCount()
     */
    public int getItemCount() {
        return mInPreLayout
                ? (mPreviousLayoutItemCount - mDeletedInvisibleItemCountSincePreviousLayout)
                : mItemCount;
    }

    /**
     * Returns remaining horizontal scroll distance of an ongoing scroll animation(fling/
     * smoothScrollTo/SmoothScroller) in pixels. Returns zero if {@link #getScrollState()} is
     * other than {@link #SCROLL_STATE_SETTLING}.
     *
     * @return Remaining horizontal scroll distance
     */
    public int getRemainingScrollHorizontal() {
        return mRemainingScrollHorizontal;
    }

    /**
     * Returns remaining vertical scroll distance of an ongoing scroll animation(fling/
     * smoothScrollTo/SmoothScroller) in pixels. Returns zero if {@link #getScrollState()} is
     * other than {@link #SCROLL_STATE_SETTLING}.
     *
     * @return Remaining vertical scroll distance
     */
    public int getRemainingScrollVertical() {
        return mRemainingScrollVertical;
    }

    @Override
    public String toString() {
        return "State{"
                + "mTargetPosition=" + mTargetPosition
                + ", mData=" + mData
                + ", mItemCount=" + mItemCount
                + ", mPreviousLayoutItemCount=" + mPreviousLayoutItemCount
                + ", mDeletedInvisibleItemCountSincePreviousLayout="
                + mDeletedInvisibleItemCountSincePreviousLayout
                + ", mStructureChanged=" + mStructureChanged
                + ", mInPreLayout=" + mInPreLayout
                + ", mRunSimpleAnimations=" + mRunSimpleAnimations
                + ", mRunPredictiveAnimations=" + mRunPredictiveAnimations
                + '}';
    }
}