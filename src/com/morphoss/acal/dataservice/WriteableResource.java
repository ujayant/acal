package com.morphoss.acal.dataservice;

public interface WriteableResource extends Resource {

	int ACTION_DELETE_SINGLE = 0;
	int ACTION_DELETE_ALL_FUTURE = 1;
	int ACTION_MODIFY_SINGLE = 2;
	int ACTION_MODIFY_ALL_FUTURE = 3;
	int ACTION_MODIFY_ALL = 4;
	int ACTION_CREATE = 5;
	int ACTION_DELETE_ALL = 6;
	
}
