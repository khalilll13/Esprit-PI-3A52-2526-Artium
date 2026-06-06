package controllers;

public class AmateursBackofficeController extends BaseUsersBackofficeController {

    @Override
    protected String managedRole() {
        return "Amateur";
    }

    @Override
    protected String managedRoleLabel() {
        return "Amateur";
    }
}

