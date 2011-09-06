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

package com.morphoss.acal.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.davacal.SimpleAcalTodo;

/**
 * <p>
 * Adapter for providing views for events.
 * </p>
 * 
 * @author Morphoss Ltd
 * 
 */
public class TodoListAdapter extends BaseAdapter implements OnClickListener, ListAdapter {

	/**
	 * <p>
	 * Presently this adapter is only supported in todo view. If we decide to extend this view further we should
	 * create An interface for providing callbacks.
	 * </p>
	 */ 
	private TodoListView context;
	private boolean listCompleted;
	private boolean listFuture;
	public static final String TAG = "aCal TodoListAdapter";
	private volatile boolean clickEnabled = true;

	
	public static final int CONTEXT_EDIT = 0;
	public static final int CONTEXT_DELETE = 0x10000;
	// public static final int CONTEXT_DELETE_JUSTTHIS = 0x20000;
	// public static final int CONTEXT_DELETE_FROMNOW = 0x30000;
	public static final int CONTEXT_COPY = 0x40000;
	public static final int CONTEXT_COMPLETE = 0x80000;
	
	private SharedPreferences prefs;	

	/**
	 * <p>Create a new adaptor with the attributes provided.</p>
	 * 
	 * @param todoListView The containing view
	 * @param showAll Whether we list all Todo items or Due ones.
	 */
	public TodoListAdapter(TodoListView todoListView, boolean showCompleted, boolean showFuture ) {
		this.context = todoListView;
		this.listCompleted = showCompleted;
		this.listFuture = showFuture;

		// Get preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this.context);

		context.getTodos(listCompleted,listFuture);
		
	}

	/**
	 * <p>Returns the number of elements in this adapter.</p>
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return context.getNumberTodos(listCompleted,listFuture);
	}

	/**
	 * <p>Returns the event at specified the position in this adapter or null if position is invalid.</p> 
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public SimpleAcalTodo getItem(int position) {
		return context.getNthTodo(listCompleted,listFuture, position);
	}

	/**
	 * <p>Returns the id associated with the event at specified position. Currently not implemented (i.e. returns position)</p>
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}


	/**
	 * <p>Returns the view associated with the event at the specified position. Currently, views
	 * do not respond to any events.</p> 
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		LinearLayout rowLayout;

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		rowLayout = (LinearLayout) inflater.inflate(R.layout.todo_list_item, parent, false);

		TextView title = (TextView) rowLayout.findViewById(R.id.TodoListItemTitle);
		TextView time = (TextView) rowLayout.findViewById(R.id.TodoListItemTime);
		TextView location = (TextView) rowLayout.findViewById(R.id.TodoListItemLocation);
		
		LinearLayout sideBar = (LinearLayout) rowLayout.findViewById(R.id.TodoListItemColorBar);

		SimpleAcalTodo todo = getItem(position);
		if ( todo == null ) return rowLayout;
		
		final boolean isPending = todo.isPending;
		if (isPending) {
			sideBar.setBackgroundColor(todo.colour|0xa0000000); title.setTextColor(todo.colour|0xa0000000);
			((LinearLayout) rowLayout.findViewById(R.id.TodoListItemText)).setBackgroundColor(0x44000000);
		}
		else {
			rowLayout.findViewById(R.id.TodoListItemIcons).setBackgroundColor(todo.colour);
			sideBar.setBackgroundColor(todo.colour); 
			title.setTextColor(todo.colour);
		}

		title.setText((todo.summary == null  || todo.summary.length() <= 0 ) ? "Untitled" : todo.summary);

		time.setText(todo.getTimeText(context,
				prefs.getBoolean(context.getString(R.string.prefTwelveTwentyfour), false))
				+ (isPending ? " (saving)" : "") );
		if ( !todo.isCompleted() && todo.isOverdue() ) {
			time.setTextColor(context.getResources().getColor(R.color.OverdueTask));
		}
		else if ( !todo.isCompleted() && todo.isOverdue() ) {
			time.setTextColor(context.getResources().getColor(R.color.CompletedTask));
		}
		
		
		if ( todo.hasAlarm ) {
			ImageView alarmed = (ImageView) rowLayout.findViewById(R.id.EventListItemAlarmBell);
			alarmed.setVisibility(View.VISIBLE);
			if ( ! todo.alarmEnabled ) alarmed.setBackgroundColor(Color.WHITE);
		}
		
		if (todo.location != null && todo.location.length() > 0 )
			location.setText(todo.location);
		else
			location.setHeight(2);

		CheckBox completed = (CheckBox) rowLayout.findViewById(R.id.TodoListItemCompleted);
		completed.setChecked(todo.isCompleted());
		completed.setBackgroundColor(Color.WHITE);
		
		rowLayout.setTag(todo);
		rowLayout.setOnClickListener(this);

		//add context menu
		this.context.registerForContextMenu(rowLayout);
		rowLayout.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
		

			@Override
			public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
				menu.setHeaderTitle(context.getString(R.string.ChooseAction));
				if ( !isPending ) menu.add(0, position, 0, context.getString(R.string.Edit));

				menu.add(0, CONTEXT_COPY + position,  0, context.getString(R.string.newEventFromThis));
				menu.add(0, CONTEXT_DELETE+ position, 0, context.getString(R.string.Delete));
				menu.add(0, CONTEXT_COMPLETE+ position, 0, context.getString(R.string.SetCompleted));
			}
		});

		return rowLayout;
	}

	public void setClickEnabled(boolean enabled) {
		this.clickEnabled = enabled;
	}

	@Override
	public void onClick(View arg0) {
		if (clickEnabled) {
			Object tag = arg0.getTag();
			if (tag instanceof SimpleAcalTodo) {
				//start event activity
				Bundle bundle = new Bundle();
				bundle.putParcelable("SimpleAcalTodo", (SimpleAcalTodo)tag);
				Intent todoViewIntent = new Intent(context, TodoView.class);
				todoViewIntent.putExtras(bundle);
				context.startActivity(todoViewIntent);
			}
		} else {
			clickEnabled = true;
		}

	}
	
	public boolean contextClick(MenuItem item) {
		try {
			int id = item.getItemId();
			int action = id & 0xf0000;
			id = id & 0xffff;

			SimpleAcalTodo todo = getItem(id);
			todo.operation = SimpleAcalTodo.TODO_OPERATION_EDIT;
			switch( action ) {
				case CONTEXT_COPY:
					todo.operation = SimpleAcalTodo.TODO_OPERATION_COPY;
				case CONTEXT_EDIT:
					//start TodoEdit activity
					Bundle bundle = new Bundle();
					bundle.putParcelable("SimpleAcalTodo", todo);
					Intent todoViewIntent = new Intent(context, TodoEdit.class);
					todoViewIntent.putExtras(bundle);
					context.startActivity(todoViewIntent);
					return true;
				
				case CONTEXT_DELETE:
					this.context.deleteTodo(listCompleted,listFuture,id,SimpleAcalTodo.TODO_OPERATION_DELETE);
					return true;

				case CONTEXT_COMPLETE:
					this.context.completeTodo(listCompleted,listFuture,id,SimpleAcalTodo.TODO_OPERATION_COMPLETE);
					return true;

			}
			return false;
		}
		catch (ClassCastException e) {
			return false;
		}
		
	}

}
