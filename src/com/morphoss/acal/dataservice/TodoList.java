package com.morphoss.acal.dataservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.davacal.SimpleAcalTodo;

/**
 * This class is responsible for the actual fetching of VTODO data from the database,
 * potentially caching it if that becomes necessary at some point.
 * 
 * @author Andrew McMillan <andrew@morphoss.com>
 *
 */
public class TodoList {

	final private static String TAG = "aCal TodoList";
	
	private static List<SimpleAcalTodo> taskList = new ArrayList<SimpleAcalTodo>();

	private boolean includeCompleted = false;
	private boolean includeFuture = false;
	
	public TodoList() {
		reset(includeCompleted, includeFuture);
	}
	public TodoList(boolean listCompleted, boolean listFuture) {
		reset(listCompleted, listFuture);
	}

	public void reset(boolean listCompleted, boolean listFuture) {
		taskList = new ArrayList<SimpleAcalTodo>();
		includeCompleted = listCompleted;
		includeFuture = listFuture;
	}

	public void sort() {
		Collections.sort(taskList);
	}

	public void add(SimpleAcalTodo task) {
		boolean includeIfCompleted = includeCompleted || !task.isCompleted();
		boolean includeIfFuture = includeFuture || !task.isFuture();

		if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG, 
				"Task: "+task.summary+", complete="+task.isCompleted()+", future="+task.isFuture()+", iC="+includeIfCompleted+", iF="+includeIfFuture);
		if ( includeIfFuture && includeIfCompleted ) taskList.add(task);
	}

	public List<SimpleAcalTodo> getList() {
		return taskList;
	}

	public int count() {
		return taskList.size();
	}

	public SimpleAcalTodo getNth(int n) {
		return taskList.get(n);
	}

}
