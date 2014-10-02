package at.markushi.multiplayer.messaging;

import com.google.android.gms.games.multiplayer.Participant;

public abstract class MultiPlayerEvent {

	public Participant participant;

	public boolean isImportant() {
		return false;
	}

	public abstract byte getMessageType();
	public abstract byte[] toByte();
	public abstract void fromByte(byte[] data, int offset);
}
