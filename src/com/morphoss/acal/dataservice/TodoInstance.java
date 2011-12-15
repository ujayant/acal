package com.morphoss.acal.dataservice;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.VTodo;

public class TodoInstance extends CalendarInstance {

	public TodoInstance(VTodo vTodo, AcalDateTime dtstart, AcalDuration duration) {
		super(vTodo.getCollectionId(), vTodo.getResourceId(), vTodo.getStart(), vTodo.getEnd(),
				vTodo.getAlarms(), vTodo.getRRule(), dtstart.toPropertyString(PropertyName.RECURRENCE_ID),
				vTodo.getSummary(), vTodo.getLocation(), vTodo.getDescription(), vTodo.getResource().getEtag());

	}
	
	@Override
	public AcalDateTime getEnd() {
		// TODO Auto-generated method stub
		return null;
	}

}
