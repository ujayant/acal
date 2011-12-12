package com.morphoss.acal.database.resourcesmanager;


public abstract class ResourceRequestWithResponse<E> implements ResourceRequest {

	//The CallBack
	private ResourceResponseListener<E> callBack = null;
	
	/**
	 * Mandatory constructor - stores the callBack to notify when posting response. CallBack can be null if requester doesn't care about
	 * response;
	 * @param callBack
	 */
	protected ResourceRequestWithResponse(ResourceResponseListener<E> callBack ){
		this.callBack = callBack;
	}
	
	/**
	 * Called by child classes to send response to the callback. Sends response on its own Thread so will usually return immediately.
	 * Beware of Race conditions when sending multiple requests - callbacks may come back in an arbitrary order.
	 * @param response
	 */
	protected void postResponse(final ResourceResponse<E> response) {
		if (callBack == null) return;
		new Thread(new Runnable() {

			@Override
			public void run() {
				callBack.resourceResponse(response);
			}
		}).start();
	}
	
	

}
