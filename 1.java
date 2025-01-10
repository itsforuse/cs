package app.shashi.AdminTalk.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import app.shashi.AdminTalk.R;
import app.shashi.AdminTalk.models.Message;
import app.shashi.AdminTalk.utils.Constants;
import app.shashi.AdminTalk.utils.FirebaseHelper;
import app.shashi.AdminTalk.utils.NotificationHelper;
import app.shashi.AdminTalk.utils.AuthHelper;
import com.google.firebase.database.*;

public class MessageNotificationService extends Service {
    private DatabaseReference databaseRef;
    private ChildEventListener messagesListener;
    private String currentUserId;
    private static final int FOREGROUND_SERVICE_ID = 1001;
    private static final String ADMIN_DISPLAY_NAME = "Admin"; // Fixed admin name
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

    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Chat Service")
                .setContentText("Listening for new messages")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setNotificationSilent()
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void listenToAllChats() {
        databaseRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot chatSnapshot, String previousChildName) {
                String userId = chatSnapshot.getKey();
                if (userId != null && !userId.equals(currentUserId)) {
                    DatabaseReference messagesRef = chatSnapshot.getRef()
                            .child(Constants.MESSAGES_REF);
                    listenToMessages(messagesRef, userId);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                if (snapshot.getKey() != null) {
                    DatabaseReference messagesRef = snapshot.getRef()
                            .child(Constants.MESSAGES_REF);
                    messagesRef.removeEventListener(messagesListener);
                }
            }
            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void listenToUserChat() {
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null && !message.getSenderId().equals(currentUserId)) {
                    showNotification(message);
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
                    showNotification(message);
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

    private void showNotification(Message message) {
        AuthHelper.isAdmin().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean isUserAdmin = task.getResult();
                String title;
                if (!isUserAdmin) {
                    // If the current user is not an admin, always show fixed admin name
                    title = ADMIN_DISPLAY_NAME;
                } else {
                    // If current user is admin, show the actual sender name
                    title = message.getSenderName();
                }

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (messagesListener != null && databaseRef != null) {
            databaseRef.removeEventListener(messagesListener);
        }
        AuthHelper.clearCache();
    }
}
