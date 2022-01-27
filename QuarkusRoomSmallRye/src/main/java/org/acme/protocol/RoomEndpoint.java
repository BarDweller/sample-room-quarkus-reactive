package org.acme.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.acme.Log;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;


@ServerEndpoint(value = "/room", decoders = MessageDecoder.class, encoders = MessageEncoder.class)
public class RoomEndpoint {

		@Inject @Channel("incoming-messages") Emitter<Message> emitter;		
		@Inject SessionTracker tracker;

	    @OnOpen
	    public void onOpen(Session session, EndpointConfig ec) {
	        Log.log(Level.FINE, this, "A new connection has been made to the room.");
	        tracker.addSession(session);
	        // All we have to do in onOpen is send the acknowledgement
	        sendMessageToSession(session, Message.ACK_MSG);
	    }

	    @OnClose
	    public void onClose(Session session, CloseReason r) {
	        Log.log(Level.FINE, this, "A connection to the room has been closed with reason " + r);
	        tracker.removeSession(session);
	    }

	    @OnError
	    public void onError(Session session, Throwable t) {
	        Log.log(Level.FINE, this, "A problem occurred on connection", t);

	        // TODO: Careful with what might revealed about implementation details!!
	        // We're opting for making debug easy..
	        tryToClose(session,
	                new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION,
	                        trimReason(t.getClass().getName())));	        
	        tracker.removeSession(session);
	    }


	    @OnMessage
	    public void receiveMessage(Session session, Message message) throws IOException {
	    	emitter.send(message);
	    }
	    
	    @Incoming("outgoing-messages")
	    public void sendMessage(Message message) {	
	    	tracker.visit(new Consumer<Session>(){ 
				@Override
				public void accept(Session s) {
					sendMessageToSession(s,message);
				} 
			});
	    }

	    /**
	     * Try sending the {@link Message} using
	     * {@link Session#getAsyncRemote()}, {@link Async#sendObject(Object)}.
	     *
	     * @param session Session to send the message on
	     * @param message Message to send
	     */
	    private void sendMessageToSession(Session session, Message message) {	
	        if (session.isOpen()) {
	            session.getAsyncRemote().sendObject(message);
	        }
	    }


	    /**
	     * @param message String to trim
	     * @return a string no longer than 123 characters (limit of value length for {@code CloseReason})
	     */
	    private String trimReason(String message) {
	        return message.length() > 123 ? message.substring(0, 123) : message;
	    }

	    /**
	     * Try to close the WebSocket session and give a reason for doing so.
	     *
	     * @param s  Session to close
	     * @param reason {@link CloseReason} the WebSocket is closing.
	     */
	    public void tryToClose(Session s, CloseReason reason) {
	        try {
	            s.close(reason);
	        } catch (IOException e) {
	            tryToClose(s);
	        }
	    }

	    /**
	     * Try to close a {@code Closeable} (usually once an error has already
	     * occurred).
	     *
	     * @param c Closable to close
	     */
	    public void tryToClose(Closeable c) {
	        if (c != null) {
	            try {
	                c.close();
	            } catch (IOException e1) {
	            }
	        }
	    }
}
