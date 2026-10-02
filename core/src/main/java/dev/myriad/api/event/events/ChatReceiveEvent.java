package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.text.Text;

/**
 * A line is about to be added to the chat: from the server, another player, or the client itself. Cancel to hide
 * it, or {@link #setMessage} to change it. Posted on the render thread.
 */
public final class ChatReceiveEvent extends Cancellable {
	private Text message;

	public ChatReceiveEvent(Text message) {
		this.message = message;
	}

	public Text message() {
		return message;
	}

	public void setMessage(Text message) {
		this.message = message;
	}
}
