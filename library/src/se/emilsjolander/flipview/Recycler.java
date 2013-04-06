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
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	void addScrapView(View scrap, int position, int viewType) {
		if (viewTypeCount == 1) {
			currentScrapViews.put(position, scrap);
		} else {
			scrapViews[viewType].put(position, scrap);
		}
		if (Build.VERSION.SDK_INT >= 14) {
			scrap.setAccessibilityDelegate(null);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static View retrieveFromScrap(SparseArray<View> scrapViews, int position) {
		int size = scrapViews.size();
		if (size > 0) {
			// See if we still have a view for this position.
			View result = scrapViews.get(position, null);
			if (result != null) {
				scrapViews.remove(position);
				return result;
			}
			int index = size - 1;
			result = scrapViews.valueAt(index);
			if (Build.VERSION.SDK_INT >= 11) {
				scrapViews.removeAt(index);
			} else {
				scrapViews.remove(scrapViews.keyAt(index));
			}
			return result;
		}
		return null;
	}

}
