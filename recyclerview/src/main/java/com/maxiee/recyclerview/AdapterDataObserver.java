package com.maxiee.recyclerview;

/**
 * Observer base class for watching changes to an {@link Adapter}.
 * See {@link Adapter#registerAdapterDataObserver(AdapterDataObserver)}.
 */
public abstract class AdapterDataObserver {
    public void onChanged() {
        // Do nothing
    }

    public void onItemRangeChanged(int positionStart, int itemCount) {
        // do nothing
    }

    public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
        // fallback to onItemRangeChanged(positionStart, itemCount) if app
        // does not override this method.
        onItemRangeChanged(positionStart, itemCount);
    }

    public void onItemRangeInserted(int positionStart, int itemCount) {
        // do nothing
    }

    public void onItemRangeRemoved(int positionStart, int itemCount) {
        // do nothing
    }

    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        // do nothing
    }
}
