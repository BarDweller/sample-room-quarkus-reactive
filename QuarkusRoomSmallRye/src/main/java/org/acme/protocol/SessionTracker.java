package org.acme.protocol;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.Session;

@ApplicationScoped
public class SessionTracker {
	private CopyOnWriteArrayList<Session> sessions = new CopyOnWriteArrayList<>();
	public void addSession(Session s) {
		sessions.addIfAbsent(s);
	}
	public void removeSession(Session s) {
		sessions.remove(s);
	}
	public void visit(Consumer<Session> f) {
		for(Session session: sessions) {
			f.accept(session);
		}
	}
}
