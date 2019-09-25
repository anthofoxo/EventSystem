package cc.antho.eventsystem;

public enum EventPriority {

	HIGHEST(false), HIGH(false), NORMAL(false), LOW(false), LOWEST(false), MONITOR(true);

	boolean ignoreCanceled;

	private EventPriority(boolean ignoreCanceled) {

		this.ignoreCanceled = ignoreCanceled;

	}

}