package org.acme.protocol;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.websocket.DecodeException;

import org.acme.Log;
import org.acme.RoomDescription;

public class Message {

    /**
     * prefix for bookmark: customize it! Just doing something here to make
     * it less likely to collide with other rooms.
     */
    private static final String PREFIX = "room-";

    /** Incrementing message id for bookmark */
    private static AtomicLong bookmark = new AtomicLong(0);

    /**
     * The first segment in the WebSocket protocol for Game On!
     * This is used as a primitive routing filter as messages flow through
     * the system (String.startsWith... )
     */
    public enum Target {
        /** Protocol acknowledgement, sent onOpen */
        ack,
        /** Message sent to player(s) */
        player,
        /** Message sent to a specific player to trigger a location change (they are allowed to exit the room) */
        playerLocation,
        /** Message sent to the room */
        room,
        /** A player enters the room */
        roomHello,
        /** A player reconnects to the room (e.g. reconnected session) */
        roomJoin,
        /** A player's has disconnected from the room without leaving it */
        roomPart,
        /** A player leaves the room */
        roomGoodbye
    };

    /**
     * Ack message: this supports both version 1 & 2
     * {@code ack,{\"version\":[1,2]}}
     */
    public static final Message ACK_MSG = new Message(Target.ack, "", "{\"version\":[1,2]}");

    /** JSON element specifying the type of message. */
    private static final String TYPE = "type";

    /** Type of message to indicate room events */
    private static final String EVENT = "event";

    /** JSON element specifying the user id. */
    public static final String USER_ID = "userId";

    /** JSON element specifying the username. */
    public static final String USERNAME = "username";

    /** JSON element specifying the content of message. */
    public static final String CONTENT = "content";

    /** JSON element specifying the content bookmark. */
    private static final String BOOKMARK = "bookmark";

    /** Messages sent to everyone */
    private static final String ALL = "*";

    /**
     * Create an event targeted at a specific player (still use broadcast to send to
     * all connections)
     *
     * @return constructed message
     */
    public static Message createSpecificEvent(String userid, String messageForUser) {
        //  player,<userId>,{
        //      "type": "event",
        //      "content": {
        //          "<userId>": "specific to player"
        //          },
        //      "bookmark": "String representing last message seen"
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(TYPE, EVENT);

        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add(userid, messageForUser);
        payload.add(CONTENT, content.build());

        payload.add(BOOKMARK, PREFIX + bookmark.incrementAndGet());
        return new Message(Target.player, userid, payload.build().toString());
    }

    /**
     * Construct an event that broadcasts to all players. The first string will
     * be the message sent to all players. Additional messages should be specified
     * in pairs afterwards, "userId1", "player message 1", "userId2", "player message 2".
     * If the optional specified messages are uneven, only the general message will be sent.
     *
     * @return constructed message
     */
    public static Message createBroadcastEvent(String allContent, String ... pairs) {
        //  player,*,{
        //      "type": "event",
        //      "content": {
        //          "*": "general text for everyone",
        //          "<userId>": "specific to player"
        //      },
        //      "bookmark": "String representing last message seen"
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(TYPE, EVENT);

        JsonObjectBuilder content = Json.createObjectBuilder();
        if ( allContent != null ) {
            content.add(ALL, allContent);
        }
        if ( pairs != null ) {
            if ( pairs.length % 2 == 0 ) {
                for(int i = 0; i < pairs.length; i += 2) {
                    content.add(pairs[i], pairs[i+1]);
                }
            } else {
                Log.log(Level.WARNING, Message.class,
                        "Programmer error: use one element as user id, and the next as the message: {0}",
                        (Object[]) pairs);
            }
        }
        payload.add(CONTENT, content.build());

        payload.add(BOOKMARK, PREFIX + bookmark.incrementAndGet());
        return new Message(Target.player, ALL, payload.build().toString());
    }

    /**
     * Content is a simple string containing the chat message.
     * @return constructed message
     */
    public static Message createChatMessage(String username, String message) {
        //  player,*,{...}
        //  {
        //    "type": "chat",
        //    "username": "username",
        //    "content": "<message>",
        //    "bookmark": "String representing last message seen"
        //  }

        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(TYPE, "chat");
        payload.add(USERNAME, username);
        payload.add(CONTENT, message);

        payload.add(BOOKMARK, PREFIX + bookmark.incrementAndGet());
        return new Message(Target.player, ALL, payload.build().toString());
    }

    /**
     * Send information about the room to the client. This message is sent after
     * receiving a `roomHello`.
     * @param userId
     * @param roomDescription Room attributes
     * @return constructed message
     */
    public static Message createLocationMessage(String userId, RoomDescription roomDescription) {
        //  player,<userId>,{
        //      "type": "location",
        //      "name": "Room name",
        //      "fullName": "Room's descriptive full name",
        //      "description", "Lots of text about what the room looks like",
        //      "exits": {
        //          "shortDirection" : "currentDescription for Player",
        //          "N" :  "a dark entranceway"
        //      },
        //      "commands": {
        //          "/custom" : "Description of what command does"
        //      },
        //      "roomInventory": ["itemA","itemB"]
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(TYPE, "location");
        payload.add("name", roomDescription.getName());
        payload.add("fullName", roomDescription.getFullName());
        payload.add("description", roomDescription.getDescription());

        // convert map of commands into JsonObject
        JsonObject commands = roomDescription.getCommands();
        if ( !commands.isEmpty()) {
            payload.add("commands", commands);
        }

        // Convert list of items into json array
        JsonArray inventory = roomDescription.getInventory();
        if ( !inventory.isEmpty()) {
            payload.add("roomInventory", inventory);
        }

        return new Message(Target.player, userId, payload.build().toString());
    }

    /**
     * Indicates that a player can leave by the requested exit (`exitId`).
     * @param userId Targeted user
     * @param exitId Direction the user will be exiting (used as a lookup key)
     * @return constructed message
     */
    public static Message createExitMessage(String userId, String exitId) {
        return createExitMessage(userId, exitId, null);
    }

    /**
     * Indicates that a player can leave by the requested exit (`exitId`).
     * @param userId Targeted user
     * @param exitId Direction the user will be exiting (used as a lookup key)
     * @param message Message to be displayed when the player leaves the room
     * @return constructed message
     */
    public static Message createExitMessage(String userId, String exitId, String message) {
        if ( exitId == null ) {
            throw new IllegalArgumentException("exitId is required");
        }

        //  playerLocation,<userId>,{
        //      "type": "exit",
        //      "content": "You exit through door xyz... ",
        //      "exitId": "N"
        //      "exit": { ... }
        //  }
        // The exit attribute describes an exit the map service wouldn't know about..
        // This would have to be customized..

        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(TYPE, "exit");
        payload.add("exitId", exitId);
        payload.add(CONTENT, message == null ? "Fare thee well" : message);

        return new Message(Target.playerLocation, userId, payload.build().toString());
    }

    /**
     * Used for test purposes, create a message targeted for the room
     * @param roomId Id of target room
     * @param userId  Id of user that sent the message
     * @param username username for user that sent the message
     * @param content content of message
     * @return constructed message
     */
    public static Message createRoomMessage(String roomId, String userId, String username, String content) {
        //  room,<roomId>,{
        //      "username": "username",
        //      "userId": "<userId>"
        //      "content": "<message>"
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(USER_ID, userId);
        payload.add(USERNAME, username);
        payload.add(CONTENT, content);

        return new Message(Target.room, roomId, payload.build().toString());
    }

    /**
     * Used for test purposes, create a room hello message
     * @param roomId Id of target room
     * @param userId  Id of user entering the room
     * @param username username for user entering the room
     * @param version version negotiated with mediator
     * @return constructed message
     */
    public static Message createRoomHello(String roomId, String userId, String username, long version) {
        //  roomHello,<roomId>,{
        //      "username": "username",
        //      "userId": "<userId>",
        //      "version": 1|2
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(USER_ID, userId);
        payload.add(USERNAME, username);
        payload.add("version", version);

        return new Message(Target.roomHello, roomId, payload.build().toString());
    }

    /**
     * Used for test purposes, create a room hello message
     * @param roomId Id of target room
     * @param userId  Id of user entering the room
     * @param username username for user entering the room
     * @param version version negotiated with mediator
     * @return constructed message
     */
    public static Message createRoomGoodbye(String roomId, String userId, String username) {
        //  roomGoodbye,<roomId>,{
        //      "username": "username",
        //      "userId": "<userId>"
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(USER_ID, userId);
        payload.add(USERNAME, username);

        return new Message(Target.roomGoodbye, roomId, payload.build().toString());
    }

    /**
     * Used for test purposes, create a room hello message
     * @param roomId Id of target room
     * @param userId  Id of user entering the room
     * @param username username for user entering the room
     * @param version version negotiated with mediator
     * @return constructed message
     */
    public static Message createRoomJoin(String roomId, String userId, String username, long version) {
        //  roomJoin,<roomId>,{
        //      "username": "username",
        //      "userId": "<userId>",
        //      "version": 2
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(USER_ID, userId);
        payload.add(USERNAME, username);
        payload.add("version", version);

        return new Message(Target.roomJoin, roomId, payload.build().toString());
    }

    /**
     * Used for test purposes, create a room hello message
     * @param roomId Id of target room
     * @param userId  Id of user entering the room
     * @param username username for user entering the room
     * @param version version negotiated with mediator
     * @return constructed message
     */
    public static Message createRoomPart(String roomId, String userId, String username) {
        //  roomPart,<roomId>,{
        //      "username": "username",
        //      "userId": "<userId>"
        //  }
        JsonObjectBuilder payload = Json.createObjectBuilder();
        payload.add(USER_ID, userId);
        payload.add(USERNAME, username);

        return new Message(Target.roomPart, roomId, payload.build().toString());
    }

    /**
     * Target for the message
     * @see Target
     */
    private final Target target;

    /**
     * Target for the message (This room, specific player, or '*')
     */
    private final String targetId;

    /**
     * Stringified JSON payload
     */
    private final String payload;

    /**
     * Parse a string read from the WebSocket, and convert it into
     * a message
     * @param s String read from WebSocket
     * @throws DecodeException
     * @see MessageDecoder#decode(String)
     */
    public Message(String s) throws DecodeException {
        // this is getting parsed in a low-level/raw way.
        // We don't split on commas arbitrarily: there are commas in the
        // json payload, which means unnecessary splitting and joining.
        ArrayList<String> list = new ArrayList<>(3);

        int brace = s.indexOf('{'); // first brace
        int i = 0;
        int j = s.indexOf(',');
        while (j > 0 && j < brace) {
            list.add(s.substring(i, j).trim());
            i = j + 1;
            j = s.indexOf(',', i);
        }

        if ( list.isEmpty() ) {
            // UMMM. Badness. Bad message. Bad!
            throw new DecodeException(s,
                    "Badly formatted payload, unable to target and targetId: \"" + s + "\"");
        }

        // stash all of the rest in the data field.
        this.payload = s.substring(i).trim();

        // The flowTarget is always present.
        // The destination may or may not be present, but shouldn't return null.
        this.target = Target.valueOf(list.get(0));
        this.targetId = list.size() > 1 ? list.get(1) : "";
    }

    /**
     * Construct a new outbound message
     * @param target General target for the message
     * @param targetId	Specific player id, '*', or null (for
     * @param payload
     */
    private Message(Target target, String targetId, String payload) {
        this.target = target;
        this.targetId = targetId == null ? "" : targetId;
        this.payload = payload;
    }

    /**
     * @return message's target, specifically either:
     *  <ul>
     *  <li>{@link Target#room}</li>
     *  <li>{@link Target#roomHello}</li>
     *  <li>{@link Target#roomGoodbye}</li>
     *  <li>{@link Target#roomJoin}</li>
     *  <li>{@link Target#roomPart}</li>
     *  </ul>
     */
    public Target getTarget() {
        return target;
    }

    /**
     * @return message target id, should always be the room id.
     */
    public String getTargetId() {
        return targetId;
    }

    public JsonObject getParsedBody() {
        JsonReader jsonReader = Json.createReader(new StringReader(payload));
        JsonObject object = jsonReader.readObject();
        jsonReader.close();

        return object;
    }

    /**
     * Convert message to a string for use as an outbound message over the WebSocket
     * @see MessageEncoder#encode(Message)
     */
    public String encode() {
        StringBuilder result = new StringBuilder();
        result.append(target).append(',');

        if (!targetId.isEmpty()) {
            result.append(targetId).append(',');
        }

        result.append(payload);

        return result.toString();
    }

    @Override
    public String toString() {
        return encode();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + payload.hashCode();
        result = prime * result + target.hashCode();
        result = prime * result + targetId.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        // Private constructor, none of these are ever null.
        Message other = (Message) obj;

        // Private constructor, none of these are ever null.
        return payload.equals(other.payload)
                && target.equals(other.target)
                && targetId.equals(other.targetId);
    }
}
