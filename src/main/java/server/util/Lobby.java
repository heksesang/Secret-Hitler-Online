package server.util;

import game.SecretHitlerGame;
import io.javalin.websocket.WsContext;
import org.json.JSONObject;

import java.util.*;

/**
 * A Lobby holds a collection of websocket connections, each representing a player.
 * It maintains the game that the connections are associated with.
 */
public class Lobby {

    private SecretHitlerGame game;
    private Map<WsContext, String> userToUsername;
    private Set<String> activeUsernames;
    private Set<String> usersInGame;

    /**
     * Constructs a new Lobby.
     */
    public Lobby() {
        userToUsername = new HashMap<>();
        activeUsernames = new LinkedHashSet<>();
        usersInGame = new LinkedHashSet<>();
    }

    /////// User Management
    //<editor-fold desc="User Management">

    /**
     * Returns whether the given user (websocket connection) is in this lobby
     * @param context the Websocket context of a user.
     * @return true iff the {@code context} is in this lobby.
     */
    public boolean hasUser(WsContext context) {
        return userToUsername.containsKey(context);
    }

    /**
     * Returns true if the lobby has a user with a given username.
     * @param name the username to check the Lobby for.
     * @return true iff the username {@code name} is in this lobby.
     */
    public boolean hasUsername(String name) {
        return activeUsernames.contains(name);
    }

    /**
     * Checks if a user can be added back to the lobby while a game is running.
     * @param name the name of the user to add.
     * @return true if the user can be added. A user can only be added back if they were in the current game but were then
     *         removed from the lobby.
     *
     */
    public boolean canAddUserDuringGame(String name) {
        return (usersInGame.contains(name) && activeUsernames.contains(name)); // the user was in the game but was disconnected.
    }

    /**
     * Checks whether the lobby is full.
     * @return Returns true if the number of players in the lobby is {@literal >= } {@code SecretHitlerGame.MAX_PLAYERS}.
     */
    public boolean isFull() {
        return activeUsernames.size() >= SecretHitlerGame.MAX_PLAYERS;
    }

    /**
     * Adds a user (websocket connection) to the lobby.
     * @param context the websocket connection context.
     * @param name the name of the player to be added.
     * @throws IllegalArgumentException if a duplicate websocket is added, if there is already a websocket with the
     *         given name in the game, if the lobby is full, if the player has a duplicate name,
     *         or if a new player is added during a game.
     * @modifies this
     * @effects adds the given user to the lobby.
     *          If the game has already started, the player can only join if a player with the name {@name} was
     *          previously in the same game but was removed.
     */
    public void addUser(WsContext context, String name) {
        if(userToUsername.containsKey(context)) {
            throw new IllegalArgumentException("Duplicate websockets cannot be added to a lobby.");
        } else {
            if (isInGame()) {
                if(canAddUserDuringGame(name)) { // This username is in the game but is not currently connected.
                    // allow the user to be connected.
                    userToUsername.put(context, name);
                } else {
                    throw new IllegalArgumentException("Cannot add a new player to a lobby currently in a game.");
                }
            } else {
                if (isFull()) {
                    if (!hasUsername(name)) { // This is a new user with a new name, so we add them to the Lobby.
                        userToUsername.put(context, name);
                        activeUsernames.add(name);
                    } else {
                        throw new IllegalArgumentException("Cannot add duplicate names.");
                    }
                } else {
                    throw new IllegalArgumentException("Cannot add the player because the lobby is full.");
                }
            }
        }
    }

    /**
     * Removes a user from the Lobby.
     * @param context the websocket connection context of the player to remove.
     * @throws IllegalArgumentException if {@code context} is not a user in the Lobby.
     * @modifies this
     * @effects removes the user context (websocket connection) of the player from the lobby.
     */
    public void removeUser(WsContext context) {
        if (!hasUser(context)) {
            throw new IllegalArgumentException("Cannot remove a websocket that is not in the Lobby.");
        } else {
            activeUsernames.remove(userToUsername.get(context));
            userToUsername.remove(context);
        }
    }

    /**
     * Returns the number of active users connected to the Lobby.
     * @return the number of active websocket connections currently in the lobby.
     */
    public int getActiveUserCount() {
        return userToUsername.size();
    }

    /**
     * Sends a message to every connected user with the current game state.
     * @effects a message containing a JSONObject representing the state of the SecretHitlerGame is sent
     *          to each connected WsContext. ({@code GameToJSONConverter.convert()})
     */
    public void updateAllUsers() {
        for (WsContext ws : userToUsername.keySet()) {
            updateUser(ws);
        }
    }

    /**
     * Sends a message to the specified user with the current game state.
     * @param ctx the WsContext websocket context.
     * @effects a message containing a JSONObject representing the state of the SecretHitlerGame is sent
     *          to the specified WsContext. ({@code GameToJSONConverter.convert()})
     */
    public void updateUser(WsContext ctx) {
        JSONObject message;

        if (isInGame()) {
            message = GameToJSONConverter.convert(game); // sends the game state
            message.put("in-game", true);
        } else {
            message = new JSONObject();
            message.put("in-game", false);
            message.put("user-count", getActiveUserCount());
            message.put("usernames", activeUsernames);
        }
        System.out.println("Updating user with message: " + message.toString());
        ctx.send(message.toString());
    }

    //</editor-fold>

    ////// Game Management
    //<editor-fold desc="Game Management">

    /**
     * Returns whether the Lobby is currently in a game.
     * @return true iff the Lobby has a currently active game.
     */
    public boolean isInGame() {
        return game != null;
    }

    /**
     * Starts a new SecretHitlerGame with the connected users as players.
     * @throws RuntimeException if there are an insufficient number of players to start a game, if there are too
     *         many in the lobby, or if the lobby is in a game ({@code isInGame() == true}).
     * @modifies this
     * @effects creates and stores a new SecretHitlerGame.
     *          The usernames of all active users are added to the game in a randomized order.
     */
    public void startNewGame() {
        if (userToUsername.size() < SecretHitlerGame.MIN_PLAYERS) {
            throw new RuntimeException("Too many users to start a game.");
        } else if (userToUsername.size() > SecretHitlerGame.MAX_PLAYERS) {
            throw new RuntimeException("Too many users to start a game.");
        } else if (isInGame()) {
            throw new RuntimeException("Cannot start a new game while a game is in progress.");
        }
        usersInGame.clear();
        usersInGame.addAll(userToUsername.values());
        List<String> playerNames = new ArrayList<>(userToUsername.values());
        Collections.shuffle(playerNames);
        game = new SecretHitlerGame(playerNames);
    }

    /**
     * Returns the current game.
     * @throws RuntimeException if called when there is no active game ({@code !this.isInGame()}).
     * @return the SecretHitlerGame for this lobby.
     */
    public SecretHitlerGame game() {
        if (game == null) {
            throw new RuntimeException();
        } else {
            return game;
        }
    }

    //</editor-fold>

}
