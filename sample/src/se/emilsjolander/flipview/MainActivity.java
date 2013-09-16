package se.emilsjolander.flipview;

import se.emilsjolander.flipview.FlipAdapter.Callback;
import se.emilsjolander.flipview.FlipView.OnFlipListener;
import se.emilsjolander.flipview.FlipView.OnOverFlipListener;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity implements Callback, OnFlipListener, OnOverFlipListener {
	
	private FlipView mFlipView;
	private FlipAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mFlipView = (FlipView) findViewById(R.id.flip_view);
		mAdapter = new FlipAdapter(this);
		mAdapter.setCallback(this);
		mFlipView.setAdapter(mAdapter);
		mFlipView.setOnFlipListener(this);
		mFlipView.peakNext(false);
		mFlipView.setOverFlipMode(OverFlipMode.RUBBER_BAND);
		mFlipView.setEmptyView(findViewById(R.id.empty_view));
		mFlipView.setOnOverFlipListener(this);
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.prepend:
			mAdapter.addItemsBefore(5);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPageRequested(int page) {
		mFlipView.smoothFlipTo(page);
	}

	@Override
	public void onFlippedToPage(FlipView v, int position, long id) {
		Log.i("pageflip", "Page: "+position);
		if(position > mFlipView.getPageCount()-3 && mFlipView.getPageCount()<30){
			mAdapter.addItems(5);
		}
	}

	@Override
	public void onOverFlip(FlipView v, OverFlipMode mode,
			boolean overFlippingPrevious, float overFlipDistance,
			float flipDistancePerPage) {
		Log.i("overflip", "overFlipDistance = "+overFlipDistance);
	}

}
