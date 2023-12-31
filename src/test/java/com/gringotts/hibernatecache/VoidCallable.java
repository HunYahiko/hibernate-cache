package com.gringotts.hibernatecache;

import java.util.concurrent.Callable;

@FunctionalInterface
public interface VoidCallable extends Callable<Void> {

	void execute() throws InterruptedException;

	default Void call() throws Exception {
		execute();
		return null;
	}
}
