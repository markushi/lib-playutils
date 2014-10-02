package at.markushi.multiplayer;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.google.example.games.basegameutils.GameHelper;

public class MultiPlayerHelper implements RealTimeMessageReceivedListener, RoomStatusUpdateListener, RoomUpdateListener, OnInvitationReceivedListener,
		GameHelper.GameHelperListener {

	public interface MultiPlayerUi {

		void showScreen(int screen);

		void onUpdateReceived(Participant participant, byte[] data);

		void onParticipantsChanged(List<Participant> participants);

		void onSignInStatusChanged(boolean isSignedIn);
	}

	public static final int SCREEN_MAIN = 0;
	public static final int SCREEN_LOADING = 2;
	public static final int SCREEN_GAME = 4;

	private final static boolean ENABLE_DEBUG = true;
	private final static String TAG = "MultiPlayerHelper";
	private final static int RC_SELECT_PLAYERS = 10000;
	private final static int RC_INVITATION_INBOX = 10001;
	private final static int RC_WAITING_ROOM = 10002;

	private GameHelper gameHelper;
	private String roomId = null;
	private String userId = null;
	private String incomingInvitationId = null;
	private Context context;
	private Activity activity;
	private MultiPlayerUi multiPlayerUi;
	private boolean setupDone;
	private boolean invitationRegistered;
	private final ArrayList<Participant> mParticipants = new ArrayList<Participant>(10);

	public MultiPlayerHelper(Activity activity) {
		this.context = activity.getApplicationContext();
		this.activity = activity;
		this.gameHelper = new GameHelper(activity, GameHelper.CLIENT_GAMES);
		this.gameHelper.enableDebugLog(ENABLE_DEBUG);
	}

	public void onCreate() {
		gameHelper.setup(this);
		gameHelper.onStart(activity);
		setupDone = true;
	}

	public void setActivity(Activity activity) {
		Log.d(TAG, "setActivity, isNull:" + (activity == null));
		this.activity = activity;
		gameHelper.setActivity(activity);
	}

	private void registerInvitationListener() {
		if (activity != null && !invitationRegistered) {
			invitationRegistered = true;
			Games.Invitations.registerInvitationListener(gameHelper.getApiClient(), this);
		}
	}

	private void unregisterInvitationListener() {
		if (setupDone && invitationRegistered && gameHelper.getApiClient().isConnected()) {
			Games.Invitations.unregisterInvitationListener(gameHelper.getApiClient());
		}
	}

	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		gameHelper.onStop();
		leaveRoom();
		unregisterInvitationListener();
		stopKeepingScreenOn();
		multiPlayerUi = null;
		setActivity(null);
	}

	public void onActivityResult(int requestCode, int responseCode, Intent intent) {
		gameHelper.onActivityResult(requestCode, responseCode, intent);

		switch (requestCode) {
		case RC_SELECT_PLAYERS:
			handleSelectPlayersResult(responseCode, intent);
			break;
		case RC_INVITATION_INBOX:
			handleInvitationInboxResult(responseCode, intent);
			break;
		case RC_WAITING_ROOM:
			if (responseCode == Activity.RESULT_OK) {
				Log.d(TAG, "Starting game (waiting room returned OK).");
				startGame();
			} else if (responseCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
				leaveRoom();
			} else if (responseCode == Activity.RESULT_CANCELED) {
				leaveRoom();
			}
			break;
		}
	}

	@Override
	public void onSignInFailed() {
		Log.d(TAG, "Sign-in failed.");
		if (multiPlayerUi != null) {
			multiPlayerUi.onSignInStatusChanged(false);
		}
	}

	@Override
	public void onSignInSucceeded() {
		Log.d(TAG, "Sign-in succeeded.");
		registerInvitationListener();

		final String invitationId = gameHelper.getInvitationId();
		if (invitationId != null) {
			acceptInviteToRoom(invitationId);
			return;
		}
		if (multiPlayerUi != null) {
			multiPlayerUi.onSignInStatusChanged(true);
		}
	}

	@Override
	public void onInvitationReceived(Invitation invitation) {
		Log.d(TAG, "onInvitationReceived: " + invitation.getInviter().getDisplayName());
		incomingInvitationId = invitation.getInvitationId();
		acceptInviteToRoom(invitation.getInvitationId());
	}

	@Override
	public void onInvitationRemoved(String invitationId) {
		if (incomingInvitationId.equals(invitationId)) {
			incomingInvitationId = null;
		}
	}

	@Override
	public void onConnectedToRoom(Room room) {
		Log.d(TAG, "onConnectedToRoom.");
		roomId = room.getRoomId();
		mParticipants.clear();
		mParticipants.addAll(room.getParticipants());
		userId = room.getParticipantId(Games.Players.getCurrentPlayerId(gameHelper.getApiClient()));
	}

	@Override
	public void onLeftRoom(int statusCode, String roomId) {
		Log.d(TAG, "onLeftRoom, code " + statusCode);
		showScreen(SCREEN_MAIN);
	}

	@Override
	public void onDisconnectedFromRoom(Room room) {
		roomId = null;
		showGameError();
	}

	@Override
	public void onRoomCreated(int statusCode, Room room) {
		Log.d(TAG, "onRoomCreated(" + statusCode + ", " + room + ")");
		if (statusCode != GamesStatusCodes.STATUS_OK) {
			Log.e(TAG, "Error: onRoomCreated, status " + statusCode);
			showGameError();
			return;
		}
		showWaitingRoom(room);
	}

	@Override
	public void onRoomConnected(int statusCode, Room room) {
		Log.d(TAG, "onRoomConnected(" + statusCode + ", " + room + ")");
		if (statusCode != GamesStatusCodes.STATUS_OK) {
			Log.e(TAG, "Error: onRoomConnected, status " + statusCode);
			showGameError();
			return;
		}
		updateRoom(room);
	}

	@Override
	public void onJoinedRoom(int statusCode, Room room) {
		Log.d(TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");
		if (statusCode != GamesStatusCodes.STATUS_OK) {
			Log.e(TAG, "Error: onRoomConnected, status " + statusCode);
			showGameError();
			return;
		}
		showWaitingRoom(room);
	}

	@Override
	public void onPeerDeclined(Room room, List<String> arg1) {
		updateRoom(room);
	}

	@Override
	public void onPeerInvitedToRoom(Room room, List<String> arg1) {
		updateRoom(room);
	}

	@Override
	public void onP2PDisconnected(String participant) {
	}

	@Override
	public void onP2PConnected(String participant) {
	}

	@Override
	public void onPeerJoined(Room room, List<String> arg1) {
		updateRoom(room);
	}

	@Override
	public void onPeerLeft(Room room, List<String> peersWhoLeft) {
		updateRoom(room);
	}

	@Override
	public void onRoomAutoMatching(Room room) {
		updateRoom(room);
	}

	@Override
	public void onRoomConnecting(Room room) {
		updateRoom(room);
	}

	@Override
	public void onPeersConnected(Room room, List<String> peers) {
		updateRoom(room);
	}

	@Override
	public void onPeersDisconnected(Room room, List<String> peers) {
		updateRoom(room);
	}

	@Override
	public void onRealTimeMessageReceived(RealTimeMessage rtm) {
		final byte[] buf = rtm.getMessageData();
		final String sender = rtm.getSenderParticipantId();
		Log.d(TAG, "Message received from: " + sender);

		Participant participant = null;
		for (Participant p : mParticipants) {
			if (p.getParticipantId().equals(sender)) {
				participant = p;
				break;
			}
		}

		if (participant == null) {
			Log.w(TAG, "Received message from unknown participant -> discarding");
		}
		if (multiPlayerUi != null && participant != null) {
			multiPlayerUi.onUpdateReceived(participant, buf);
		}
	}

	public void setMultiPlayerUi(MultiPlayerUi multiPlayerUi) {
		this.multiPlayerUi = multiPlayerUi;
	}

	public void startQuickGame(int minOpponents, int maxOpponents) {
		final Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minOpponents, maxOpponents, 0);
		final RoomConfig.Builder rtmConfigBuilder = RoomConfig.builder(this);
		rtmConfigBuilder.setMessageReceivedListener(this);
		rtmConfigBuilder.setRoomStatusUpdateListener(this);
		rtmConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);

		showScreen(SCREEN_LOADING);
		keepScreenOn();
		Games.RealTimeMultiplayer.create(gameHelper.getApiClient(), rtmConfigBuilder.build());
	}

	public void startGameWithFriends(int minOpponents, int maxOpponents) {
		final Intent intent = Games.RealTimeMultiplayer.getSelectOpponentsIntent(gameHelper.getApiClient(), minOpponents, maxOpponents);
		showScreen(SCREEN_LOADING);
		keepScreenOn();
		if (activity != null) {
			activity.startActivityForResult(intent, RC_SELECT_PLAYERS);
		} else {
			Log.w(TAG, "activity not set");
		}
	}

	public void publishUpdate(byte[] data, boolean reliable) {
		for (Participant p : mParticipants) {
			if (p.getParticipantId().equals(userId)) {
				continue;
			}
			if (p.getStatus() != Participant.STATUS_JOINED) {
				continue;
			}
			if (reliable) {
				Games.RealTimeMultiplayer.sendReliableMessage(gameHelper.getApiClient(), null, data, roomId, p.getParticipantId());
			} else {
				Games.RealTimeMultiplayer.sendUnreliableMessage(gameHelper.getApiClient(), data, roomId, p.getParticipantId());
			}
		}
	}

	public void signIn() {
		gameHelper.beginUserInitiatedSignIn();
	}

	public void signOut() {
		gameHelper.signOut();
	}

	public boolean isSignedIn() {
		return gameHelper.isSignedIn();
	}

	private void handleSelectPlayersResult(int response, Intent data) {
		if (response != Activity.RESULT_OK) {
			Log.d(TAG, "Select players UI succeeded.");
		} else {
			Log.w(TAG, "select players UI cancelled, " + response);
			showScreen(SCREEN_MAIN);
			return;
		}

		final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
		Log.d(TAG, "Invitee count: " + invitees.size());

		Bundle autoMatchCriteria = null;
		int minAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
		int maxAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);
		if (minAutoMatchPlayers > 0 || maxAutoMatchPlayers > 0) {
			autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPlayers, maxAutoMatchPlayers, 0);
			Log.d(TAG, "Automatch criteria: " + autoMatchCriteria);
		}

		Log.d(TAG, "Creating room...");
		final RoomConfig.Builder rtmConfigBuilder = RoomConfig.builder(this);
		rtmConfigBuilder.addPlayersToInvite(invitees);
		rtmConfigBuilder.setMessageReceivedListener(this);
		rtmConfigBuilder.setRoomStatusUpdateListener(this);
		if (autoMatchCriteria != null) {
			rtmConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);
		}
		showScreen(SCREEN_LOADING);
		keepScreenOn();
		Games.RealTimeMultiplayer.create(gameHelper.getApiClient(), rtmConfigBuilder.build());
		Log.d(TAG, "Room created, waiting for it to be ready...");
	}

	private void handleInvitationInboxResult(int response, Intent data) {
		if (response != Activity.RESULT_OK) {
			Log.w(TAG, "invitation inbox UI cancelled, " + response);
			showScreen(SCREEN_MAIN);
			return;
		}

		Log.d(TAG, "Invitation inbox UI succeeded.");
		final Invitation inv = data.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);

		// accept invitation
		acceptInviteToRoom(inv.getInvitationId());
	}

	private void acceptInviteToRoom(String invId) {
		// accept the invitation
		Log.d(TAG, "Accepting invitation: " + invId);
		final RoomConfig.Builder roomConfigBuilder = RoomConfig.builder(this);
		roomConfigBuilder.setInvitationIdToAccept(invId).setMessageReceivedListener(this).setRoomStatusUpdateListener(this);
		showScreen(SCREEN_LOADING);
		keepScreenOn();
		Games.RealTimeMultiplayer.join(gameHelper.getApiClient(), roomConfigBuilder.build());
	}

	private void leaveRoom() {
		Log.d(TAG, "Leaving room.");
		stopKeepingScreenOn();
		if (roomId != null) {
			final GoogleApiClient apiClient = gameHelper.getApiClient();
			if (apiClient.isConnected()) {
				Games.RealTimeMultiplayer.leave(apiClient, this, roomId);
			}
			roomId = null;
			showScreen(SCREEN_LOADING);
		} else {
			showScreen(SCREEN_MAIN);
		}
	}

	private void showWaitingRoom(Room room) {
		if (activity == null) {
			Log.w(TAG, "Activity not set");
		} else {
			final int MIN_PLAYERS = Integer.MAX_VALUE;
			final Intent i = Games.RealTimeMultiplayer.getWaitingRoomIntent(gameHelper.getApiClient(), room, MIN_PLAYERS);
			activity.startActivityForResult(i, RC_WAITING_ROOM);
		}
	}

	private void showGameError() {
		Log.d(TAG, "showGameError");
		showScreen(SCREEN_MAIN);
	}

	private void updateRoom(Room room) {
		if (room == null) {
			return;
		}

		mParticipants.clear();
		mParticipants.addAll(room.getParticipants());
		if (multiPlayerUi != null) {
			multiPlayerUi.onParticipantsChanged(mParticipants);
		}
	}

	public List<Participant> getParticipants() {
		return mParticipants;
	}

	public Participant getMe() {
		for (Participant p : mParticipants) {
			if (p.getParticipantId().equals(userId)) {
				return p;
			}

		}
		return null;
	}

	private void startGame() {
		if (multiPlayerUi != null) {
			multiPlayerUi.onParticipantsChanged(mParticipants);
		}
		showScreen(SCREEN_GAME);
	}

	private void showScreen(int screen) {
		Log.d(TAG, "showScreen: " + screen);
		if (multiPlayerUi == null) {
			return;
		}
		multiPlayerUi.showScreen(screen);
	}

	private void keepScreenOn() {
		if (activity == null) {
			return;
		}
		activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void stopKeepingScreenOn() {
		if (activity == null) {
			return;
		}
		activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
}