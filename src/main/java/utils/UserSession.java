package utils;

import controllers.MainFX;
import entities.User;

public final class UserSession {

	private UserSession() {
		// Utility class.
	}

	public static User getCurrentUser() {
		User fromMainFx = MainFX.getAuthenticatedUser();
		if (fromMainFx != null) {
			return fromMainFx;
		}
		return SessionManager.getCurrentUser();
	}

	public static Integer getCurrentUserId() {
		User current = getCurrentUser();
		return current == null ? null : current.getId();
	}
}

