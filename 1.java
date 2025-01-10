package app.shashi.AdminTalk.utils;

public class AppStateTracker {
    private static boolean isInChat = false;
    private static String currentChatId = null;
    private static boolean isAppInForeground = false;

    public static void setInChat(boolean inChat, String chatId) {
        isInChat = inChat;
        currentChatId = chatId;
    }

    public static void setAppInForeground(boolean inForeground) {
        isAppInForeground = inForeground;
    }

    public static boolean shouldShowNotification(String messageChatId) {
        if (!isAppInForeground) {
            return true;
        }
        
        if (!isInChat) {
            return true;
        }

        // Don't show notification if user is in the same chat where message came from
        return currentChatId == null || !currentChatId.equals(messageChatId);
    }

    public static void reset() {
        isInChat = false;
        currentChatId = null;
        isAppInForeground = false;
    }
}









package app.shashi.AdminTalk.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import app.shashi.AdminTalk.R;
import app.shashi.AdminTalk.models.Message;
import app.shashi.AdminTalk.utils.*;
import com.google.firebase.database.*;

public class MessageNotificationService extends Service {
    private DatabaseReference databaseRef;
    private ChildEventListener messagesListener;
    private String currentUserId;
    private static final int FOREGROUND_SERVICE_ID = 1001;
    private static final String ADMIN_DISPLAY_NAME = "Admin";
    private boolean isServiceInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        currentUserId = FirebaseHelper.getCurrentUserUid();
        startForeground(FOREGROUND_SERVICE_ID, createForegroundNotification());
        
        AuthHelper.isAdmin().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean isAdmin = task.getResult();
                initializeService(isAdmin);
            }
        });
    }

    private void initializeService(boolean isAdmin) {
        if (isServiceInitialized) {
            return;
        }

        isServiceInitialized = true;
        if (isAdmin) {
            databaseRef = FirebaseDatabase.getInstance().getReference(Constants.CHATS_REF);
            listenToAllChats();
        } else {
            databaseRef = FirebaseHelper.getChatReference(currentUserId)
                    .child(Constants.MESSAGES_REF);
            listenToUserChat();
        }
    }

    private void showNotification(Message message, String chatId) {
        // Only proceed if we should show the notification
        if (!AppStateTracker.shouldShowNotification(chatId)) {
            return;
        }

        AuthHelper.isAdmin().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean isUserAdmin = task.getResult();
                String title = !isUserAdmin ? ADMIN_DISPLAY_NAME : message.getSenderName();

                NotificationHelper.showMessageNotification(
                    this,
                    title,
                    message.getText(),
                    message.getSenderId(),
                    message.getSenderName()
                );
            }
        });
    }

    private void listenToUserChat() {
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null && !message.getSenderId().equals(currentUserId)) {
                    showNotification(message, currentUserId);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onChildRemoved(DataSnapshot snapshot) {}
            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onCancelled(DatabaseError error) {}
        };
        databaseRef.addChildEventListener(messagesListener);
    }

    private void listenToMessages(DatabaseReference messagesRef, String userId) {
        ChildEventListener chatMessagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null && !message.getSenderId().equals(currentUserId)) {
                    showNotification(message, userId);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onChildRemoved(DataSnapshot snapshot) {}
            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onCancelled(DatabaseError error) {}
        };
        messagesRef.addChildEventListener(chatMessagesListener);
    }

    // ... (other existing methods remain the same)

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (messagesListener != null && databaseRef != null) {
            databaseRef.removeEventListener(messagesListener);
        }
        AppStateTracker.reset();
        AuthHelper.clearCache();
    }
}








@Override
protected void onResume() {
    super.onResume();
    AppStateTracker.setInChat(true, userId); // userId is the chat partner's ID
    AppStateTracker.setAppInForeground(true);
}

@Override
protected void onPause() {
    super.onPause();
    AppStateTracker.setInChat(false, null);
}






