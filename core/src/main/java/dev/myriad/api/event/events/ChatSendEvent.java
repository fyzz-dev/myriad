package dev.myriad.api.event.events;

import org.jetbrains.annotations.ApiStatus;
import dev.myriad.api.event.Cancellable;

/** The player is about to send a chat message. Cancel to swallow it; {@link #setMessage} to rewrite it. */
public final class ChatSendEvent extends Cancellable {
	private String message;

	@ApiStatus.Internal
	public ChatSendEvent(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
