package com.maxiee.recyclerview;

import android.view.View;

/**
 * ViewCacheExtension is a helper class to provide an additional layer of view caching that can
 * be controlled by the developer.
 * ViewCacheExtension 是一个帮助类, 提供了一个额外的可以被开发者控制的视图缓存层.
 *
 * <p>
 * When {@link Recycler#getViewForPosition(int)} is called, Recycler checks attached scrap and
 * first level cache to find a matching View. If it cannot find a suitable View, Recycler will
 * call the {@link #getViewForPositionAndType(Recycler, int, int)} before checking
 * {@link RecycledViewPool}.
 * <p>
 * Note that, Recycler never sends Views to this method to be cached. It is developers
 * responsibility to decide whether they want to keep their Views in this custom cache or let
 * the default recycling policy handle it.
 */
public abstract class ViewCacheExtension {

    /**
     * Returns a View that can be binded to the given Adapter position.
     * <p>
     * This method should <b>not</b> create a new View. Instead, it is expected to return
     * an already created View that can be re-used for the given type and position.
     * If the View is marked as ignored, it should first call
     * {@link LayoutManager#stopIgnoringView(View)} before returning the View.
     * <p>
     * RecyclerView will re-bind the returned View to the position if necessary.
     *
     * @param recycler The Recycler that can be used to bind the View
     * @param position The adapter position
     * @param type     The type of the View, defined by adapter
     * @return A View that is bound to the given position or NULL if there is no View to re-use
     * @see LayoutManager#ignoreView(View)
     */
    public abstract View getViewForPositionAndType(Recycler recycler, int position, int type);
}
