package cc.antho.eventsystem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventLayer {

	private boolean useLater;

	public EventLayer() {

		this(false);

	}

	public EventLayer(boolean useLater) {

		this.useLater = useLater;

	}

	private List<Event> queue = Collections.synchronizedList(new ArrayList<>());

	// Stores methods to the event that they are listening for, this is grouped by
	// priority then by listener
	private Map<EventListener, Map<EventPriority, Map<Class<? extends Event>, List<Method>>>> listeners = new HashMap<>();

	private static Class<? extends Event> validate(Method method) {

		// Require the method to be annotated with the event hander annotation
		// We also require the method have one parameter
		if (!method.isAnnotationPresent(EventHandler.class)) return null;
		if (method.getParameterCount() != 1) return null;

		// Check that the one parameter is a subclass of event
		// If not then we cannot dispatch events to it
		Class<?> param = method.getParameters()[0].getType();
		if (!Event.class.isAssignableFrom(param)) return null;

		// We found a valid event handler, set accessible to true to allow us to access
		// it via reflection, even if it's private
		method.setAccessible(true);

		// Make sure we are able to reference this as an event
		return param.asSubclass(Event.class);

	}

	public void registerEventListener(EventListener listener) {

		// Don't bother registering this listener if it already is registered
		if (listeners.containsKey(listener)) return;

		// Create a value to put into the listener map
		Map<EventPriority, Map<Class<? extends Event>, List<Method>>> methods = new HashMap<>();

		// Loop though each method of the event listener class
		for (Method method : listener.getClass().getDeclaredMethods()) {

			// Validate the method
			Class<? extends Event> param = validate(method);
			if (param == null) continue;

			// Get the priority of the event hander
			EventPriority priority = method.getAnnotation(EventHandler.class).priority();

			// If we don't have a group for this priority, create one and store it
			if (!methods.containsKey(priority)) methods.put(priority, new HashMap<>());

			// Get all the methods for this class type
			List<Method> methodsCallable = methods.get(priority).get(param);

			// If no list existed then create a new one and insert it into the map
			if (methodsCallable == null) {

				methodsCallable = new ArrayList<>();
				methods.get(priority).put(param, methodsCallable);

			}

			// Add this method into the list of methods
			methodsCallable.add(method);

		}

		// Add this class to the listener list
		listeners.put(listener, methods);

	}

	public void deregisterEventListener(EventListener listener) {

		listeners.remove(listener);

	}

	public void complete() {

		for (int i = queue.size() - 1; i >= 0; i--)
			dispatchImmediate(queue.remove(i));

	}

	public void dispatch(Event event) {

		if (useLater) dispatchLater(event);
		else dispatchImmediate(event);

	}

	public void dispatchLater(Event event) {

		queue.add(event);

	}

	public void dispatchImmediate(Event event) {

		dispatch(EventPriority.HIGHEST, event);
		dispatch(EventPriority.HIGH, event);
		dispatch(EventPriority.NORMAL, event);
		dispatch(EventPriority.LOW, event);
		dispatch(EventPriority.LOWEST, event);
		dispatch(EventPriority.MONITOR, event);

	}

	private void dispatch(EventPriority priority, Event event) {

		if (event.canceled && !priority.ignoreCanceled) return;

		// Loop though all event listeners
		for (EventListener listener : listeners.keySet()) {

			// Pull the values from the event listener
			Map<EventPriority, Map<Class<? extends Event>, List<Method>>> priorityMap = listeners.get(listener);

			// Only run this if any of the given priority is there
			if (!priorityMap.containsKey(priority)) continue;

			// Run for this event type only
			Class<?> eventType = event.getClass();
			for (Class<? extends Event> targetType : priorityMap.get(priority).keySet()) {

				// If the target type is a subclass of the event passed in, we can run it
				// without harm
				if (targetType.isAssignableFrom(eventType))

					for (Method method : priorityMap.get(priority).get(targetType)) {

						try {

							method.invoke(listener, event);

						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {

							e.printStackTrace();

						}

					}

			}

		}

	}

}