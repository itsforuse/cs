private void showNotification(Message message) {
    // Check if sender is admin
    AuthHelper.isAdmin(message.getSenderId()).addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
            boolean isAdmin = task.getResult();
            String title = isAdmin ? "Admin" : message.getSenderName(); // Use fixed "Admin" title for admin messages
            
            NotificationHelper.showMessageNotification(
                this,
                title,
                message.getText(),
                message.getSenderId(),
                title  // Pass the same title as sender name to maintain consistency
            );
        }
    });
}





public static Task<Boolean> isAdmin(String userId) {
    if (userId == null) {
        TaskCompletionSource<Boolean> taskCompletionSource = new TaskCompletionSource<>();
        taskCompletionSource.setResult(false);
        return taskCompletionSource.getTask();
    }

    // Return cached result for current user
    if (FirebaseAuth.getInstance().getCurrentUser() != null && 
        userId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid()) && 
        isAdminCached != null) {
        TaskCompletionSource<Boolean> taskCompletionSource = new TaskCompletionSource<>();
        taskCompletionSource.setResult(isAdminCached);
        return taskCompletionSource.getTask();
    }

    // Get admin claim for specified user
    return FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("isAdmin")
            .get()
            .continueWith(task -> {
                if (!task.isSuccessful()) {
                    return false;
                }
                return task.getResult().getValue(Boolean.class) != null && 
                       task.getResult().getValue(Boolean.class);
            });
}
