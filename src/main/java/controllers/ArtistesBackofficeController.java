package controllers;

public class ArtistesBackofficeController extends BaseUsersBackofficeController {

    @Override
    protected String managedRole() {
        return "Artiste";
    }

    @Override
    protected String managedRoleLabel() {
        return "Artiste";
    }
}

