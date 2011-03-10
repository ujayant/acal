/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.activity.serverconfig;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.providers.Servers;

public class AddServerListAdapter extends BaseAdapter {

	public static final String TAG = "Acal AddServerListAdapter";
	private Context context;
	private ArrayList<ContentValues> data;
	private ContentValues otherServer;
	private int preconfig=0;
	
	public AddServerListAdapter(Context c) {
		this.context = c;
		populateData();
	}
	
	private void populateData() {
		data = new ArrayList<ContentValues>();
		preconfig=0;
		//A Bit of magic to get all the right files from /raw
		ArrayList<Integer> list = new ArrayList<Integer>();
		Field[] fields = R.raw.class.getFields();
		for(Field f : fields)
		try {
				String name = f.getName();
				if (name == null || name.length() < 11) continue;
				if (name.substring(0,10).equalsIgnoreCase("serverconf")) 
					list.add(f.getInt(null));
		    } catch (IllegalArgumentException e) {
		    } catch (IllegalAccessException e) { }

		for (int i : list) {
			try {
				InputStream in = context.getResources().openRawResource(i);
				List<ServerConfigData> l = ServerConfigData.getServerConfigDataFromFile(in);
				for (ServerConfigData scd : l) {
					ContentValues cv = scd.getContentValues();
					cv.put(ServerConfiguration.MODEKEY, ServerConfiguration.MODE_IMPORT);
					data.add(cv);
					preconfig++;;
				}
			} catch (Exception e) {
				Log.e(TAG, "Error parsing file: "+e);
			}
		}
		
		
		//first find all 'acal' files in appropriate directories
		try {
			File publicDir = new File(Constants.PUBLIC_DATA_DIR);
			String[] acalFiles = publicDir.list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.substring(name.length()-5).equalsIgnoreCase(".acal");
				}
			});
		
			for (String filename : acalFiles) {
				try {
					List<ServerConfigData> l = ServerConfigData.getServerConfigDataFromFile(new File(publicDir.getAbsolutePath()+"/"+filename));
					for (ServerConfigData scd : l) {
						
						ContentValues cv = scd.getContentValues();
						cv.put(ServerConfiguration.MODEKEY, ServerConfiguration.MODE_IMPORT);
						data.add(cv);
					}
				} catch (Exception e) {
					Log.e(TAG, "Error parsing file: "+filename+" - "+e);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error reading file list: "+e);
			Log.d(TAG,Log.getStackTraceString(e));
		}
		
		

		
		// Add the 'Other Server' Option
		otherServer = new ContentValues();
		otherServer.put(ServerConfiguration.MODEKEY, ServerConfiguration.MODE_CREATE);

	}
	
	@Override
	public int getCount() {
		return this.data.size()+1; //number of servers, + 1 'Other'
	}

	@Override
	public Object getItem(int id) {
		if (id < data.size()) return data.get(id);
		return otherServer;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convert, ViewGroup parent) {
		RelativeLayout rowLayout;

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		TextView title = null, blurb = null;
		View icon = null;
		rowLayout = (RelativeLayout) inflater.inflate(R.layout.add_server_list_item, parent, false);

		title = (TextView) rowLayout.findViewById(R.id.AddServerItemTitle);
		blurb = (TextView) rowLayout.findViewById(R.id.AddServerItemBlurb);
		icon = rowLayout.findViewById(R.id.AddServerItemIcon);
		
		final ContentValues item;
		boolean preconfig=false;
		boolean other=false;
		if (position < data.size() ) item = data.get(position);
		else { item = otherServer; other = true; }
		preconfig = position<this.preconfig;
		
		//Icon
		if (other) {
			icon.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.plus_icon));
			title.setText("Create New");
			blurb.setText("Manually enter server information");
		}
		else if (!preconfig) {
			icon.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.icon));
			title.setText(item.getAsString(Servers.FRIENDLY_NAME));
			blurb.setText("A saved server configuration");
		}
		else {
			//in future we will add custom icons
			icon.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.question_icon));
			title.setText(item.getAsString(Servers.FRIENDLY_NAME));
			blurb.setText(item.getAsString("INFO"));
		}
		rowLayout.setTag(item);
		rowLayout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Create Intent to start new Activity
				Intent serverConfigIntent = new Intent();

				// Begin new activity
				serverConfigIntent.setClassName("com.morphoss.acal",
						"com.morphoss.acal.activity.serverconfig.ServerConfiguration");
				serverConfigIntent.putExtra("ServerData", item);
				context.startActivity(serverConfigIntent); 
			}
		});
		return rowLayout;
	}
}
