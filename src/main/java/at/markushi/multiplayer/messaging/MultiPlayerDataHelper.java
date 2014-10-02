package at.markushi.multiplayer.messaging;

import android.util.SparseArray;

import com.google.android.gms.games.multiplayer.Participant;

public class MultiPlayerDataHelper {

	private static final String TAG = "MultiPlayerDataHandler";
	private static final int MESSAGE_TYPE_HEADER_SIZE = 1;

	private final SparseArray<MultiPlayerEvent> mapping;

	public MultiPlayerDataHelper() {
		mapping = new SparseArray<MultiPlayerEvent>();
	}

	public void register(MultiPlayerEvent message) {
		mapping.put(message.getMessageType(), message);
	}

	public MultiPlayerEvent handle(Participant participant, byte[] data) {
		if (data == null || data.length < MESSAGE_TYPE_HEADER_SIZE) {
			return null;
		}
		final MultiPlayerEvent message = mapping.get(data[0]);
		if (message == null) {
			return null;
		}
		message.participant = participant;
		message.fromByte(data, MESSAGE_TYPE_HEADER_SIZE);
		return message;
	}
}
