package ltdjms.discord.aichat.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;

class ReasoningMessageTrackerTest {

  @Test
  void deleteAll_shouldDeleteMessagesAddedAfterDeletionRequest() {
    AIChatMentionListener.ReasoningMessageTracker tracker =
        new AIChatMentionListener.ReasoningMessageTracker();

    Message initialMessage = mockMessage();
    Message existingReasoningMessage = mockMessage();
    Message lateReasoningMessage = mockMessage();

    tracker.setInitialMessage(initialMessage);
    tracker.addReasoningMessage(existingReasoningMessage);

    tracker.deleteAll(() -> {});

    tracker.addReasoningMessage(lateReasoningMessage);

    verify(initialMessage).delete();
    verify(existingReasoningMessage).delete();
    verify(lateReasoningMessage).delete();
  }

  private Message mockMessage() {
    Message message = mock(Message.class);
    AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
    when(message.delete()).thenReturn(deleteAction);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Void> success = invocation.getArgument(0);
              if (success != null) {
                success.accept(null);
              }
              return null;
            })
        .when(deleteAction)
        .queue(any(), any());
    return message;
  }
}
