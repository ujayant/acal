package com.morphoss.acal.dataservice;

import java.util.ArrayList;
import java.util.List;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.SimpleAcalTodo;

/**
 * This class is responsible for the actual fetching of VTODO data from the database,
 * potentially caching it if that becomes necessary at some point.
 * 
 * @author Andrew McMillan <andrew@morphoss.com>
 *
 */
public class TodoList {

	private static final String TAG = "Acal TodoList"; 

	private static List<SimpleAcalTodo> allTasks = new ArrayList<SimpleAcalTodo>();
	private static List<SimpleAcalTodo> dueTasks = new ArrayList<SimpleAcalTodo>();
	private static List<SimpleAcalTodo> incompleteTasks = new ArrayList<SimpleAcalTodo>();

	public TodoList() {
		reset();
	}

	public void reset() {
		allTasks = new ArrayList<SimpleAcalTodo>();
		dueTasks = new ArrayList<SimpleAcalTodo>();
		incompleteTasks = new ArrayList<SimpleAcalTodo>();
	}

	public void add(SimpleAcalTodo task) {
		allTasks.add(task);
		if ( ! task.isCompleted() ) {
			incompleteTasks.add(task);
		}
		else if ( task.due != null && (task.due < ((System.currentTimeMillis() / 1000L) + (AcalDateTime.SECONDS_IN_DAY * 7))) ) {
			dueTasks.add(task);
		}
	}

	public List<SimpleAcalTodo> getList(boolean listCompleted, boolean listFuture) {
		return ( !listFuture ? dueTasks : (!listCompleted ? incompleteTasks : allTasks) );
	}

	public int count(boolean listCompleted, boolean listFuture) {
		return ( !listFuture ? dueTasks : (!listCompleted ? incompleteTasks : allTasks) ).size();
	}

	public SimpleAcalTodo getNth(boolean listCompleted, boolean listFuture, int n) {
		return ( !listFuture ? dueTasks : (!listCompleted ? incompleteTasks : allTasks) ).get(n);
	}

}
