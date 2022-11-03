package io.github.arlol.testing;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DestroyListener implements LifecycleListener {

	private static final Logger LOG = LoggerFactory
			.getLogger(DestroyListener.class);

	private boolean destroyed = false;
	private boolean stopped = false;

	public boolean isStopped() {
		return stopped;
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	@Override
	public void lifecycleEvent(LifecycleEvent event) {
		LOG.info(event.getType());
		if (Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
			destroyed = true;
		} else if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
			stopped = true;
		}
	}

}
