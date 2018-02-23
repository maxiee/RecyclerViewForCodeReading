package com.maxiee.recyclerview;

import java.util.List;

public class AdapterChangeConstants {
    /**
     * The Item represented by this ViewHolder is updated.
     * <p>
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     */
    public static final int FLAG_CHANGED = ViewHolder.FLAG_UPDATE;

    /**
     * The Item represented by this ViewHolder is removed from the adapter.
     * <p>
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     */
    public static final int FLAG_REMOVED = ViewHolder.FLAG_REMOVED;

    /**
     * Adapter {@link Adapter#notifyDataSetChanged()} has been called and the content
     * represented by this ViewHolder is invalid.
     * <p>
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     */
    public static final int FLAG_INVALIDATED = ViewHolder.FLAG_INVALID;

    /**
     * The position of the Item represented by this ViewHolder has been changed. This flag is
     * not bound to {@link Adapter#notifyItemMoved(int, int)}. It might be set in response to
     * any adapter change that may have a side effect on this item. (e.g. The item before this
     * one has been removed from the Adapter).
     * <p>
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     */
    public static final int FLAG_MOVED = ViewHolder.FLAG_MOVED;

    /**
     * This ViewHolder was not laid out but has been added to the layout in pre-layout state
     * by the {@link LayoutManager}. This means that the item was already in the Adapter but
     * invisible and it may become visible in the post layout phase. LayoutManagers may prefer
     * to add new items in pre-layout to specify their virtual location when they are invisible
     * (e.g. to specify the item should <i>animate in</i> from below the visible area).
     * <p>
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     */
    public static final int FLAG_APPEARED_IN_PRE_LAYOUT =
            ViewHolder.FLAG_APPEARED_IN_PRE_LAYOUT;
}
