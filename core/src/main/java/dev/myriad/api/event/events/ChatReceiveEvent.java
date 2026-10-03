package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.network.chat.Component;

/**
 * A line is about to be added to the chat: from the server, another player, or the client itself. Cancel to hide
 * it, or {@link #setMessage} to change it. Posted on the render thread.
 */
public final class ChatReceiveEvent extends Cancellable {
	private Component message;

	public ChatReceiveEvent(Component message) {
		this.message = message;
	}

	public Component message() {
		return message;
	}

	public void setMessage(Component message) {
		this.message = message;
	}
}
