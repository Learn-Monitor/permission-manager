package de.igslandstuhl.database.permissions.restrictions;

import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.permissions.PermissionManager;
import de.igslandstuhl.database.permissions.generics.SchoolClassGeneric;
import de.igslandstuhl.database.permissions.generics.SubjectGeneric;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;

public class RestrictionTypeHelper {
    private RestrictionTypeHelper() {}
    public static void registerDefaults() {
        RestrictionType.register("student", (args, fieldMissingBehavior) -> (request) -> {
            if (args.length != 2) {
                PermissionManager.getInstance().getLogger().warn("Wrong config: student post restriction must have arg length 2");
                return true;
            }

            APIPostRequest apiPostRequest;
            if (request instanceof APIPostRequest r) {
                apiPostRequest = r;
            } else {
                apiPostRequest = APIPostRequest.fromPostRequest(request);
            }

            Student student = apiPostRequest.getCurrentStudent();

            if (student == null) return "grant".equals(fieldMissingBehavior);

            String restriction = args[0];
            
            if (restriction == "part_of") {
                SchoolClass schoolClass = SchoolClassGeneric.getInstance().getSchoolClass(args[1]);
                if (schoolClass == null) {
                    PermissionManager.getInstance().getLogger().warn("SchoolClass not found: {}", args[1]);
                    return true;
                }
                return schoolClass.getStudents().contains(student);
            } else {
                PermissionManager.getInstance().getLogger().warn("Wrong config: unknown student restriction type: {}", restriction);
                return true;
            }
        });
        RestrictionType.register("subject", (args, fieldMissingBehavior) -> (request) -> {
            if (args.length != 2) {
                PermissionManager.getInstance().getLogger().warn("Wrong config: subject post restriction must have arg length 2");
                return true;
            }

            APIPostRequest apiPostRequest;
            if (request instanceof APIPostRequest r) {
                apiPostRequest = r;
            } else {
                apiPostRequest = APIPostRequest.fromPostRequest(request);
            }

            Subject subject = apiPostRequest.getSubject();

            if (subject == null) return "grant".equals(fieldMissingBehavior);

            String restriction = args[0];
            
            if (restriction == "is") {
                Subject restrictionSubject = SubjectGeneric.getInstance().getSubject(args[1]);
                if (restrictionSubject == null) {
                    PermissionManager.getInstance().getLogger().warn("Subject not found: {}", args[1]);
                    return true;
                }
                return restrictionSubject.equals(subject);
            } else {
                PermissionManager.getInstance().getLogger().warn("Wrong config: unknown subject restriction type: {}", restriction);
                return true;
            }
        });
        RestrictionType.register("class", (args, fieldMissingBehavior) -> (request) -> {
            if (args.length != 2) {
                PermissionManager.getInstance().getLogger().warn("Wrong config: subject post restriction must have arg length 2");
                return true;
            }

            APIPostRequest apiPostRequest;
            if (request instanceof APIPostRequest r) {
                apiPostRequest = r;
            } else {
                apiPostRequest = APIPostRequest.fromPostRequest(request);
            }

            SchoolClass schoolClass = apiPostRequest.getSchoolClass();

            if (schoolClass == null) return "grant".equals(fieldMissingBehavior);

            String restriction = args[0];
            
            if (restriction == "is") {
                SchoolClass restrictionClass = SchoolClassGeneric.getInstance().getSchoolClass(args[1]);
                if (restrictionClass == null) {
                    PermissionManager.getInstance().getLogger().warn("School class not found: {}", args[1]);
                    return true;
                }
                return restrictionClass.equals(schoolClass);
            } else {
                PermissionManager.getInstance().getLogger().warn("Wrong config: unknown class restriction type: {}", restriction);
                return true;
            }
        });
    }
}
