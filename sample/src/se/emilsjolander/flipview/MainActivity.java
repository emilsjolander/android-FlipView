package se.emilsjolander.flipview;

import se.emilsjolander.flipview.FlipAdapter.Callback;
import se.emilsjolander.flipview.FlipView.OnFlipListener;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity implements Callback {
	
	private FlipView mFlipView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mFlipView = (FlipView) findViewById(R.id.flip_view);
		FlipAdapter adapter = new FlipAdapter(this);
		adapter.setCallback(this);
		mFlipView.setAdapter(adapter);
		mFlipView.setOnFlipListener(new OnFlipListener() {
			
			@Override
			public void onFlippedToPage(FlipView v, int position, long id) {
				Toast.makeText(getBaseContext(), "Page: "+position, Toast.LENGTH_SHORT).show();
			}
		});
		
		mFlipView.peakNext(false);
	}

	@Override
	public void onPageRequested(int page) {
		mFlipView.smoothFlipTo(page);
	}

}
