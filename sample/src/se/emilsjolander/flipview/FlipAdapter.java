package se.emilsjolander.flipview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

public class FlipAdapter extends BaseAdapter implements OnClickListener {
	
	public interface Callback{
		public void onPageRequested(int page);
	}
	
	private LayoutInflater inflater;
	private Callback callback;
	private int count = 10;
	
	public FlipAdapter(Context context) {
		inflater = LayoutInflater.from(context);
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	@Override
	public int getCount() {
		return count ;
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		
		if(convertView == null){
			holder = new ViewHolder();
			convertView = inflater.inflate(R.layout.page, parent, false);
			
			holder.text = (TextView) convertView.findViewById(R.id.text);
			holder.firstPage = (Button) convertView.findViewById(R.id.first_page);
			holder.lastPage = (Button) convertView.findViewById(R.id.last_page);
			
			holder.firstPage.setOnClickListener(this);
			holder.lastPage.setOnClickListener(this);
			
			convertView.setTag(holder);
		}else{
			holder = (ViewHolder) convertView.getTag();
		}
		
		holder.text.setText(""+position);
		//convertView.setBackgroundColor(getColor(position));
		
		return convertView;
	}
	
	private int getColor(int position) {
		float t = ((float)position)/(getCount()-1);
		return (int) ((1-t)*0xffff3333 + t*0xff3333ff);
	}

	static class ViewHolder{
		TextView text;
		Button firstPage;
		Button lastPage;
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.first_page:
			if(callback != null){
				callback.onPageRequested(0);
			}
			break;
		case R.id.last_page:
			if(callback != null){
				callback.onPageRequested(getCount()-1);
			}
			break;
		}
	}

	public void addItems(int amount) {
		count += amount;
		notifyDataSetChanged();
	}

}
