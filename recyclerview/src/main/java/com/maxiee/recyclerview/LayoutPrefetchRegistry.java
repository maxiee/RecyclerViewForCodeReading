package com.maxiee.recyclerview;

/**
 * Interface for LayoutManagers to request items to be prefetched, based on position, with
 * specified distance from viewport, which indicates priority.
 *
 * @see LayoutManager#collectAdjacentPrefetchPositions(int, int, State, LayoutPrefetchRegistry)
 * @see LayoutManager#collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)
 */
public interface LayoutPrefetchRegistry {
    /**
     * Requests an an item to be prefetched, based on position, with a specified distance,
     * indicating priority.
     *
     * @param layoutPosition Position of the item to prefetch.
     * @param pixelDistance Distance from the current viewport to the bounds of the item,
     *                      must be non-negative.
     */
    void addPosition(int layoutPosition, int pixelDistance);
}