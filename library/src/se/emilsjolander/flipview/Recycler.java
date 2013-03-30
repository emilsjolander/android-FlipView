package se.emilsjolander.flipview;

import android.util.SparseArray;
import android.view.View;

public class Recycler {

	/** Unsorted views that can be used by the adapter as a convert view. */
	private SparseArray<View>[] scrapViews;

	private int viewTypeCount;

	private SparseArray<View> currentScrapViews;

	void setViewTypeCount(int viewTypeCount) {
		if (viewTypeCount < 1) {
			throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
		}
		// noinspection unchecked
		@SuppressWarnings("unchecked")
		SparseArray<View>[] scrapViews = new SparseArray[viewTypeCount];
		for (int i = 0; i < viewTypeCount; i++) {
			scrapViews[i] = new SparseArray<View>();
		}
		this.viewTypeCount = viewTypeCount;
		currentScrapViews = scrapViews[0];
		this.scrapViews = scrapViews;
	}

	/** @return A view from the ScrapViews collection. These are unordered. */
	View getScrapView(int position, int viewType) {
		if (viewTypeCount == 1) {
			return retrieveFromScrap(currentScrapViews, position);
		} else if (viewType >= 0 && viewType < scrapViews.length) {
			return retrieveFromScrap(scrapViews[viewType], position);
		}
		return null;
	}

	/**
	 * Put a view into the ScrapViews list. These views are unordered.
	 * 
	 * @param scrap
	 *            The view to add
	 */
	void addScrapView(View scrap, int position, int viewType) {
		if (viewTypeCount == 1) {
			currentScrapViews.put(position, scrap);
		} else {
			scrapViews[viewType].put(position, scrap);
		}

		scrap.setAccessibilityDelegate(null);
	}

	static View retrieveFromScrap(SparseArray<View> scrapViews, int position) {
		int size = scrapViews.size();
		if (size > 0) {
			// See if we still have a view for this position.
			for (int i = 0; i < size; i++) {
				View view = scrapViews.get(i);
				int fromPosition = scrapViews.keyAt(i);
				if (fromPosition == position) {
					scrapViews.remove(i);
					return view;
				}
			}
			int index = size - 1;
			View r = scrapViews.valueAt(index);
			scrapViews.removeAt(index);
			return r;
		} else {
			return null;
		}
	}

}
