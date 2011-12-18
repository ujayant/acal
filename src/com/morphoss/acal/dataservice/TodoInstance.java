package com.morphoss.acal.dataservice;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.VTodo;

public class TodoInstance extends CalendarInstance {

	private final AcalDateTime completed;
	private int	percentComplete;
	
	public TodoInstance(VTodo vTodo, AcalDateTime dtstart, AcalDuration duration) {
		super(vTodo, dtstart, duration);
		completed = vTodo.getCompleted();
		percentComplete = vTodo.getPercentComplete();
	}

	public AcalDateTime getCompleted() {
		return completed;
	}

	public int getPercentComplete() {
		return percentComplete;
	}
}
