package com.maxiee.recyclerview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewGroup;

/**
 * {@link android.view.ViewGroup.MarginLayoutParams LayoutParams} subclass for children of
 * {@link RecyclerView}. Custom {@link LayoutManager layout managers} are encouraged
 * to create their own subclass of this <code>LayoutParams</code> class
 * to store any additional required per-child view metadata about the layout.
 *
 * {@link android.view.ViewGroup.MarginLayoutParams LayoutParams} 的子类, 用于 RecyclerView 的子元素.
 * 建议自定义 {@link LayoutManager layout managers} 对这个 <code>LayoutParams</code> 类创建他们自己的子类,
 * 来保存附加的每个子视图都需要的关于布局的元数据.
 */
public class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
    ViewHolder mViewHolder;
    final Rect mDecorInsets = new Rect();
    boolean mInsetsDirty = true;

    // Flag is set to true if the view is bound while it is detached from RV.
    // In this case, we need to manually call invalidate after view is added to guarantee that
    // invalidation is populated through the View hierarchy
    boolean mPendingInvalidate = false;

    public LayoutParams(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    public LayoutParams(int width, int height) {
        super(width, height);
    }

    public LayoutParams(ViewGroup.MarginLayoutParams source) {
        super(source);
    }

    public LayoutParams(ViewGroup.LayoutParams source) {
        super(source);
    }

    public LayoutParams(LayoutParams source) {
        super((ViewGroup.LayoutParams) source);
    }

    /**
     * Returns true if the view this LayoutParams is attached to needs to have its content
     * updated from the corresponding adapter.
     *
     * @return true if the view should have its content updated
     */
    public boolean viewNeedsUpdate() {
        return mViewHolder.needsUpdate();
    }

    /**
     * Returns true if the view this LayoutParams is attached to is now representing
     * potentially invalid data. A LayoutManager should scrap/recycle it.
     *
     * @return true if the view is invalid
     */
    public boolean isViewInvalid() {
        return mViewHolder.isInvalid();
    }

    /**
     * Returns true if the adapter data item corresponding to the view this LayoutParams
     * is attached to has been removed from the data set. A LayoutManager may choose to
     * treat it differently in order to animate its outgoing or disappearing state.
     *
     * @return true if the item the view corresponds to was removed from the data set
     */
    public boolean isItemRemoved() {
        return mViewHolder.isRemoved();
    }

    /**
     * Returns true if the adapter data item corresponding to the view this LayoutParams
     * is attached to has been changed in the data set. A LayoutManager may choose to
     * treat it differently in order to animate its changing state.
     *
     * @return true if the item the view corresponds to was changed in the data set
     */
    public boolean isItemChanged() {
        return mViewHolder.isUpdated();
    }

    /**
     * @deprecated use {@link #getViewLayoutPosition()} or {@link #getViewAdapterPosition()}
     */
    @Deprecated
    public int getViewPosition() {
        return mViewHolder.getPosition();
    }

    /**
     * Returns the adapter position that the view this LayoutParams is attached to corresponds
     * to as of latest layout calculation.
     *
     * @return the adapter position this view as of latest layout pass
     */
    public int getViewLayoutPosition() {
        return mViewHolder.getLayoutPosition();
    }

    /**
     * Returns the up-to-date adapter position that the view this LayoutParams is attached to
     * corresponds to.
     *
     * @return the up-to-date adapter position this view. It may return
     * {@link RecyclerView#NO_POSITION} if item represented by this View has been removed or
     * its up-to-date position cannot be calculated.
     */
    public int getViewAdapterPosition() {
        return mViewHolder.getAdapterPosition();
    }

    /**
     * set mViewHolder. add By Maxiee
     * @param viewHolder
     */
    public void setViewHolder(ViewHolder viewHolder) {
        mViewHolder = viewHolder;
    }

    /**
     * get mViewHolder. add By Maxiee
     * @return
     */
    public ViewHolder getViewHolder() {
        return mViewHolder;
    }
}
